/*
 *     Copyright (C) 2026 The Gramophone authors
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic.queue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavioural contract shared by the in-memory and SQLite repository implementations. Assertions
 * describe queue state, never SQL or storage layout.
 */
abstract class QueueRepositoryContractTest {
    private lateinit var repository: QueueRepository

    protected abstract fun createRepository(): QueueRepository

    protected open fun closeRepository(repository: QueueRepository) = Unit

    @Before
    fun setUp() {
        repository = createRepository()
    }

    @After
    fun tearDown() {
        closeRepository(repository)
    }

    @Test
    fun createFromSourceStoresTheCompleteQueue() = runBlocking {
        val source = QueueSource(QueueSourceKind.ALBUM, "album-1", "Abbey Road")

        val queue = repository.create(source, listOf("row-a", "row-b", "row-c"), now = 100L)
        val stored = repository.queue(queue.id).first()

        assertEquals(queue, stored)
        assertEquals("Abbey Road", stored?.title)
        assertEquals(source, stored?.source)
        assertEquals(listOf("row-a", "row-b", "row-c"), stored?.rows)
        assertEquals(QueueBookmark(), stored?.bookmark)
        assertEquals(QueueRepeatMode.OFF, stored?.repeatMode)
        assertEquals(100L, stored?.createdAt)
        assertEquals(100L, stored?.updatedAt)
        assertNull(stored?.lastPlayedAt)
        assertNull(stored?.exportFileName)
    }

    @Test
    fun replayingASourceAlwaysCreatesANewCompleteQueue() = runBlocking {
        val source = QueueSource(QueueSourceKind.ALBUM, "album-1", "Abbey Road")
        val first = repository.create(source, listOf("a", "b", "c"), now = 100L)
        repository.mutate(first.id, now = 150L) { it.copy(repeatMode = QueueRepeatMode.ALL) }

        val second = repository.create(source, listOf("a", "b", "c"), now = 200L)

        assertNotEquals(first.id, second.id)
        assertEquals("Abbey Road", first.title)
        assertEquals("Abbey Road 2", second.title)
        assertEquals(listOf("a", "b", "c"), second.rows)
        val storedFirst = repository.queue(first.id).first()
        assertEquals(listOf("a", "b", "c"), storedFirst?.rows)
        assertEquals(QueueRepeatMode.ALL, storedFirst?.repeatMode)
    }

    @Test
    fun collidingSourceTitlesUseTheSmallestFreeNumberAndRenameFreesTheBaseName() = runBlocking {
        val source = QueueSource(QueueSourceKind.GENRE, "genre-1", "Jazz")

        val first = repository.create(source, now = 1L)
        val second = repository.create(source, now = 2L)
        val third = repository.create(source, now = 3L)
        assertEquals(listOf("Jazz", "Jazz 2", "Jazz 3"), listOf(first, second, third).map { it.title })

        repository.rename(second.id, "My Jazz", now = 4L)
        val next = repository.create(source, now = 5L)

        assertEquals("Jazz 2", next.title)
        assertEquals("My Jazz", repository.queue(second.id).first()?.title)
    }

    @Test
    fun numberedQueuesUseAMonotonicCounterWithGaps() = runBlocking {
        val first = repository.create(source = null, now = 1L)
        val second = repository.create(
            QueueSource(QueueSourceKind.SEARCH_RESULTS),
            now = 2L,
        )
        repository.delete(first.id)

        val third = repository.create(source = null, now = 3L)

        assertEquals("new queue #1", first.title)
        assertEquals("new queue #2", second.title)
        assertEquals("new queue #3", third.title)
        assertNull(repository.queue(first.id).first())
    }

    @Test
    fun createWithTitleUsesTitleAndAdvancesNumberedCounter() = runBlocking {
        val custom = repository.create(source = null, title = "  My Party Queue  ", now = 1L)
        val next = repository.create(source = null, now = 2L)

        assertEquals("My Party Queue", custom.title)
        assertFalse(custom.isModified)
        assertEquals("new queue #2", next.title)
    }

    @Test
    fun createWithLocalizedSourceTitlePreservesUnmodifiedAndResolvesUniqueTitles() = runBlocking {
        val favSource = QueueSource(QueueSourceKind.FAVOURITES, "favourites", "Favourites")
        val firstFav = repository.create(source = favSource, title = "Favorites", now = 1L)
        val secondFav = repository.create(source = favSource, title = "Favorites", now = 2L)

        val allSongsSource = QueueSource(QueueSourceKind.ALL_SONGS, "all_songs", "Songs")
        val songsQueue = repository.create(source = allSongsSource, title = "Songs", now = 3L)

        val recentSource = QueueSource(QueueSourceKind.RECENTLY_ADDED, "recently_added", "Recently added")
        val recentQueue = repository.create(source = recentSource, title = "Recently Added", now = 4L)

        assertEquals("Favorites", firstFav.title)
        assertFalse(firstFav.isModified)
        assertEquals("Favorites 2", secondFav.title)
        assertFalse(secondFav.isModified)
        assertEquals("Songs", songsQueue.title)
        assertFalse(songsQueue.isModified)
        assertEquals("Recently Added", recentQueue.title)
        assertFalse(recentQueue.isModified)
    }

    @Test
    fun anEmptyQueueIsANormalPersistedQueue() = runBlocking {
        val queue = repository.create(source = null, rows = emptyList(), now = 10L)

        val stored = repository.queue(queue.id).first()

        assertEquals(queue, stored)
        assertTrue(stored?.rows?.isEmpty() == true)
        assertEquals(QueueBookmark(), stored?.bookmark)
    }

    @Test
    fun renameCollisionsTakeTheSmallestFreeSuffixAndBlankIsIgnored() = runBlocking {
        val first = repository.create(source = null, rows = listOf("a"), now = 1L)
        repository.rename(first.id, "Roadtrip", now = 2L)

        val second = repository.create(source = null, rows = listOf("b"), now = 3L)
        val renamed = repository.rename(second.id, "  Roadtrip  ", now = 4L)

        assertEquals("Roadtrip 2", renamed?.title)
        assertEquals("Roadtrip 2", repository.queue(second.id).first()?.title)
        assertEquals("Roadtrip", repository.queue(first.id).first()?.title)

        val ignored = repository.rename(second.id, "   ", now = 5L)
        assertEquals("Roadtrip 2", ignored?.title)
        assertEquals(4L, repository.queue(second.id).first()?.updatedAt)
    }

    @Test
    fun renamingToTheSameTitleIsANoOpWithoutBumpingTheEditTimestamp() = runBlocking {
        val queue = repository.create(source = null, now = 1L)

        val unchanged = repository.rename(queue.id, queue.title, now = 9L)

        assertEquals(queue, unchanged)
        assertEquals(1L, repository.queue(queue.id).first()?.updatedAt)
        assertNull(repository.rename(java.util.UUID.randomUUID(), "Ghost"))
    }

    @Test
    fun renameKeepsIdentityRowsBookmarkRepeatAndTimestamps() = runBlocking {
        val queue = repository.create(
            source = QueueSource(QueueSourceKind.FOLDER, "/music", "Music"),
            rows = listOf("a", "b", "c"),
            now = 10L,
        )
        repository.mutate(queue.id, now = 20L) {
            it.copy(
                bookmark = QueueBookmark(row = 1, positionMs = 5_000L, ended = false),
                repeatMode = QueueRepeatMode.ONE,
                lastPlayedAt = 15L,
            )
        }

        val renamed = repository.rename(queue.id, "Roadtrip", now = 30L)
        val stored = repository.queue(queue.id).first()

        assertEquals(queue.id, renamed?.id)
        assertEquals("Roadtrip", stored?.title)
        assertEquals(queue.source, stored?.source)
        assertEquals(listOf("a", "b", "c"), stored?.rows)
        assertEquals(QueueBookmark(1, 5_000L), stored?.bookmark)
        assertEquals(QueueRepeatMode.ONE, stored?.repeatMode)
        assertEquals(10L, stored?.createdAt)
        assertEquals(15L, stored?.lastPlayedAt)
        assertEquals(30L, stored?.updatedAt)
        assertTrue(stored?.isModified == true)
    }

    @Test
    fun mutationsKeepOneOrderAndMoveTheBookmarkWithItsRow() = runBlocking {
        val queue = repository.create(source = null, rows = listOf("a", "b", "c", "d"), now = 1L)
        repository.mutate(queue.id, now = 2L) { it.copy(bookmark = QueueBookmark(row = 2)) }

        repository.mutate(queue.id, now = 3L) { it.reordered(listOf(2, 0, 3, 1), now = 3L) }
        val stored = repository.queue(queue.id).first()

        assertEquals(listOf("c", "a", "d", "b"), stored?.rows)
        assertEquals(QueueBookmark(row = 0), stored?.bookmark)
    }

    @Test
    fun appendingSongsToQueuesNeverChangesTheActiveQueueOrBookmark() = runBlocking {
        val active = repository.create(source = null, rows = listOf("a"), now = 1L)
        repository.setActiveQueue(active.id)
        val target = repository.create(source = null, rows = listOf("x"), now = 2L)

        val appended = repository.mutate(target.id, now = 3L) { it.withAppendedRows(listOf("y")) }
        val createdWithSong = repository.create(source = null, rows = listOf("z"), now = 4L)

        assertEquals(active.id, repository.lastActiveQueue.value?.id)
        assertEquals(listOf("x", "y"), appended?.rows)
        assertEquals(QueueBookmark(), appended?.bookmark)
        assertTrue(appended?.isModified == true)
        assertEquals(listOf("z"), createdWithSong.rows)
        assertFalse(createdWithSong.id == active.id)
    }

    @Test
    fun bookmarkAndRepeatAreIsolatedPerQueue() = runBlocking {
        val first = repository.create(source = null, rows = listOf("a", "b"), now = 1L)
        val second = repository.create(source = null, rows = listOf("x", "y"), now = 2L)

        repository.mutate(first.id, now = 3L) {
            it.copy(
                bookmark = QueueBookmark(row = 1, positionMs = 8_000L, ended = true),
                repeatMode = QueueRepeatMode.ALL,
            )
        }

        val untouched = repository.queue(second.id).first()
        assertEquals(QueueBookmark(), untouched?.bookmark)
        assertEquals(QueueRepeatMode.OFF, untouched?.repeatMode)
        assertEquals(
            QueueBookmark(1, 8_000L, ended = true),
            repository.queue(first.id).first()?.bookmark,
        )
    }

    @Test
    fun mutateNormalizesAnOutOfRangeBookmarkAndBumpsTheTimestamp() = runBlocking {
        val queue = repository.create(source = null, rows = listOf("a", "b"), now = 1L)

        val changed = repository.mutate(queue.id, now = 9L) {
            it.copy(bookmark = QueueBookmark(row = 99, positionMs = -5L))
        }

        assertEquals(QueueBookmark(row = 1, positionMs = 0L), changed?.bookmark)
        assertEquals(9L, changed?.updatedAt)
    }

    @Test
    fun mutatingWithoutAChangeDoesNotBumpTheTimestamp() = runBlocking {
        val queue = repository.create(source = null, rows = listOf("a", "b"), now = 1L)

        val unchanged = repository.mutate(queue.id, now = 9L) { it }

        assertEquals(1L, unchanged?.updatedAt)
        assertEquals(1L, repository.queue(queue.id).first()?.updatedAt)
    }

    @Test
    fun savingProgressUpdatesBookmarkAndRepeatWithoutTouchingTheEditTimestamp() = runBlocking {
        val queue = repository.create(source = null, rows = listOf("a", "b", "c"), now = 10L)

        val changed = repository.saveProgress(
            queue.id,
            QueueBookmark(row = 2, positionMs = 7_000L, ended = true),
            QueueRepeatMode.ALL,
        )
        val stored = repository.queue(queue.id).first()

        assertEquals(QueueBookmark(2, 7_000L, ended = true), changed?.bookmark)
        assertEquals(QueueRepeatMode.ALL, changed?.repeatMode)
        assertEquals(10L, stored?.updatedAt)
        assertEquals(changed, stored)
    }

    @Test
    fun savingProgressClampsAnOutOfRangeRow() = runBlocking {
        val queue = repository.create(source = null, rows = listOf("a", "b"), now = 1L)

        val changed = repository.saveProgress(queue.id, QueueBookmark(row = 99))

        assertEquals(QueueBookmark(row = 1), changed?.bookmark)
        assertNull(repository.saveProgress(java.util.UUID.randomUUID(), QueueBookmark()))
    }

    @Test
    fun lastActiveQueueIsRememberedAndClearedWhenDeleted() = runBlocking {
        val first = repository.create(source = null, now = 1L)
        val second = repository.create(source = null, now = 2L)

        repository.setActiveQueue(first.id)
        assertEquals(first.id, repository.lastActiveQueue.first()?.id)

        repository.setActiveQueue(second.id)
        assertEquals(second.id, repository.lastActiveQueue.first()?.id)

        repository.delete(second.id)
        assertNull(repository.lastActiveQueue.first())
        assertEquals(first.id, repository.queue(first.id).first()?.id)
    }

    @Test
    fun markingAQueuePlayedRecordsActivityWithoutBumpingTheEditTimestamp() = runBlocking {
        val queue = repository.create(source = null, rows = listOf("a"), now = 10L)

        val played = repository.markPlayed(queue.id, now = 25L)
        val stored = repository.queue(queue.id).first()

        assertEquals(25L, played?.lastPlayedAt)
        assertEquals(10L, stored?.updatedAt)
        assertEquals(25L, stored?.lastPlayedAt)
        assertNull(repository.markPlayed(java.util.UUID.randomUUID(), now = 30L))
    }

    @Test
    fun settingAnUnknownActiveQueueClearsTheMarker() = runBlocking {
        val queue = repository.create(source = null, now = 1L)
        repository.setActiveQueue(queue.id)

        repository.setActiveQueue(java.util.UUID.randomUUID())

        assertNull(repository.lastActiveQueue.first())
    }

    @Test
    fun deletingAnInactiveQueueLeavesTheActiveQueueAlone() = runBlocking {
        val active = repository.create(source = null, now = 1L)
        val inactive = repository.create(source = null, now = 2L)
        repository.setActiveQueue(active.id)

        assertTrue(repository.delete(inactive.id))
        assertFalse(repository.delete(inactive.id))

        assertEquals(active.id, repository.lastActiveQueue.first()?.id)
        assertNull(repository.queue(inactive.id).first())
    }

    @Test
    fun queueListKeepsStableManualOrderAcrossPlaybackActivity() = runBlocking {
        val first = repository.create(source = null, now = 100L)
        val second = repository.create(source = null, now = 200L)
        val third = repository.create(source = null, now = 300L)

        assertEquals(listOf(first.id, second.id, third.id), repository.queues.first().map { it.id })

        repository.markPlayed(second.id, now = 400L)
        repository.setActiveQueue(first.id)
        repository.mutate(second.id, now = 500L) { it.copy(lastPlayedAt = 600L) }

        assertEquals(
            listOf(first.id, second.id, third.id),
            repository.queues.first().map { it.id },
        )
    }

    @Test
    fun moveQueuePersistsManualOrder() = runBlocking {
        val first = repository.create(source = null, now = 100L)
        val second = repository.create(source = null, now = 200L)
        val third = repository.create(source = null, now = 300L)

        assertTrue(repository.moveQueue(third.id, 0))
        assertEquals(listOf(third.id, first.id, second.id), repository.queues.first().map { it.id })

        assertTrue(repository.moveQueue(first.id, 2))
        assertEquals(listOf(third.id, second.id, first.id), repository.queues.first().map { it.id })
        assertFalse(repository.moveQueue(java.util.UUID.randomUUID(), 0))
    }

    @Test
    fun singleQueueFlowReflectsMutationsAndDeletion() = runBlocking {
        val queue = repository.create(source = null, rows = listOf("a"), now = 1L)

        repository.mutate(queue.id, now = 2L) { it.copy(repeatMode = QueueRepeatMode.ALL) }
        assertEquals(QueueRepeatMode.ALL, repository.queue(queue.id).first()?.repeatMode)

        repository.delete(queue.id)
        assertNull(repository.queue(queue.id).first())
    }

    @Test
    fun exportFileNameRoundTripsThroughTheStore() = runBlocking {
        val queue = repository.create(source = null, rows = listOf("a"), now = 1L)

        repository.mutate(queue.id, now = 2L) { it.copy(exportFileName = "queue_${it.id}.m3u8") }

        assertEquals(
            "queue_${queue.id}.m3u8",
            repository.queue(queue.id).first()?.exportFileName,
        )
    }

    @Test
    fun recordingTheMirrorNameDoesNotBumpTheEditTimestamp() = runBlocking {
        val queue = repository.create(source = null, rows = listOf("a"), now = 10L)

        repository.setExportFileName(queue.id, "queue_${queue.id}.m3u8")

        val stored = repository.queue(queue.id).first()
        assertEquals("queue_${queue.id}.m3u8", stored?.exportFileName)
        assertEquals(10L, stored?.updatedAt)
    }

    @Test
    fun concurrentMutationsAreSerializedWithoutLostUpdates() = runBlocking {
        val queue = repository.create(source = null, now = 1L)

        (1..40).map { index ->
            async(Dispatchers.Default) {
                repository.mutate(queue.id, now = index.toLong()) {
                    it.copy(rows = it.rows + "row-$index")
                }
            }
        }.forEach { it.await() }

        val stored = repository.queue(queue.id).first()
        assertEquals(40, stored?.rows?.size)
        assertEquals(40, stored?.rows?.distinct()?.size)
    }
}
