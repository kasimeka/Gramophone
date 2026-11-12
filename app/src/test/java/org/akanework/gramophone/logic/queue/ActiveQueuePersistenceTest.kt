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

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.akanework.gramophone.logic.utils.MediaRowCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActiveQueuePersistenceTest {
    private class FakePlayer(
        var items: List<MediaItem>,
        var index: Int = 0,
        var position: Long = 0L,
        var state: Int = Player.STATE_READY,
        var repeat: Int = Player.REPEAT_MODE_OFF,
    ) : ActiveQueuePlayer {
        override fun mediaItemCount(): Int = items.size

        override fun getMediaItemAt(index: Int): MediaItem = items[index]

        override fun currentMediaItemIndex(): Int = index

        override fun currentPosition(): Long = position

        override fun playbackState(): Int = state

        override fun repeatMode(): Int = repeat
    }

    private fun item(id: String) = MediaItem.Builder()
        .setMediaId(id)
        .setUri("content://media/external/audio/media/$id")
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Song $id").build())
        .build()

    private fun rows(vararg ids: String): List<String> =
        ids.map { MediaRowCodec.encode(item(it)) }

    @Test
    fun saveNowAwaitMirrorsRowsBookmarkAndRepeatIntoTheActiveQueue() = runBlocking {
        val repository = QueueRepository.inMemory()
        val created = repository.create(source = null, rows = rows("a"))
        repository.setActiveQueue(created.id)
        val player = FakePlayer(
            items = listOf(item("a"), item("b")),
            index = 1,
            position = 42_000L,
            repeat = Player.REPEAT_MODE_ALL,
        )
        val persistence = ActiveQueuePersistence(repository, player, scope = this)
        persistence.adopt(created)

        val saved = persistence.saveNowAwait()!!

        assertEquals(rows("a", "b"), saved.rows)
        assertEquals(QueueBookmark(row = 1, positionMs = 42_000L), saved.bookmark)
        assertEquals(QueueRepeatMode.ALL, saved.repeatMode)
        assertEquals(created.id, repository.lastActiveQueue.value?.id)
    }

    @Test
    fun anEndedPlayerIsBookmarkedOnTheFinalRow() = runBlocking {
        val repository = QueueRepository.inMemory()
        val created = repository.create(source = null, rows = rows("a", "b"))
        repository.setActiveQueue(created.id)
        val player = FakePlayer(
            items = listOf(item("a"), item("b")),
            index = 1,
            position = 9_000L,
            state = Player.STATE_ENDED,
        )
        val persistence = ActiveQueuePersistence(repository, player, scope = this)
        persistence.adopt(created)

        val saved = persistence.saveNowAwait()!!

        assertEquals(QueueBookmark(row = 1, positionMs = 9_000L, ended = true), saved.bookmark)
    }

    @Test
    fun saveClearedNowPersistsAnActiveQueueWhoseLastRowWasRemoved() = runBlocking {
        val repository = QueueRepository.inMemory()
        val created = repository.create(source = null, rows = rows("a"))
        repository.setActiveQueue(created.id)
        val persistence = ActiveQueuePersistence(repository, FakePlayer(emptyList()), scope = this)
        persistence.adopt(created)

        val saved = persistence.saveClearedNow(created.id)!!

        assertEquals(emptyList<String>(), saved.rows)
        assertEquals(QueueBookmark(), saved.bookmark)
        assertEquals(saved, repository.queue(created.id).first())
    }

    @Test
    fun saveClearedNowUsesTheCapturedQueueIdentityAfterDetach() = runBlocking {
        val repository = QueueRepository.inMemory()
        val created = repository.create(source = null, rows = rows("a"))
        repository.setActiveQueue(created.id)
        val persistence = ActiveQueuePersistence(repository, FakePlayer(emptyList()), scope = this)
        persistence.adopt(created)

        // The timeline listener detaches the marker as soon as the player becomes empty.
        persistence.detach()
        val saved = persistence.saveClearedNow(created.id)!!

        assertEquals(emptyList<String>(), saved.rows)
        assertEquals(saved, repository.queue(created.id).first())
    }

    @Test
    fun readRestoredRebuildsTheEndedQueueWithoutAdoptingIt() = runBlocking {
        val repository = QueueRepository.inMemory()
        val created = repository.create(source = null, rows = rows("a", "b", "c"))
        val ended = repository.mutate(created.id) {
            it.copy(bookmark = QueueBookmark(row = 0, positionMs = 9_000L, ended = true))
        }!!
        repository.setActiveQueue(ended.id)
        val persistence = ActiveQueuePersistence(repository, FakePlayer(emptyList()), scope = this)

        val restored = persistence.readRestored()!!

        assertEquals(ended.id, restored.queue.id)
        assertEquals(2, restored.startRow)
        assertEquals(9_000L, restored.positionMs)
        assertTrue(restored.ended)
        assertNull(persistence.activeQueueId())
    }

    @Test
    fun readRestoredReturnsNullWithoutAPlayableActiveQueue() = runBlocking {
        val repository = QueueRepository.inMemory()
        val created = repository.create(source = null, rows = emptyList())
        repository.setActiveQueue(created.id)
        val persistence = ActiveQueuePersistence(repository, FakePlayer(emptyList()), scope = this)

        assertNull(persistence.readRestored())
    }

    @Test
    fun progressSavingPersistsTheCurrentPositionPeriodically() = runBlocking {
        val repository = QueueRepository.inMemory()
        val created = repository.create(source = null, rows = rows("a"))
        repository.setActiveQueue(created.id)
        val player = FakePlayer(items = listOf(item("a")), position = 7_000L)
        val persistence = ActiveQueuePersistence(
            repository,
            player,
            scope = this,
            progressIntervalMs = 10L,
        )
        persistence.adopt(created)

        persistence.startProgressSaving()
        val saved = withTimeout(5_000L) {
            repository.lastActiveQueue.first { it?.bookmark?.positionMs == 7_000L }
        }
        persistence.stopProgressSaving(save = false)

        assertEquals(7_000L, saved!!.bookmark.positionMs)
        assertFalse(saved.bookmark.ended)
    }

    @Test
    fun progressFromTheOutgoingPlayerCannotOverwriteTheIncomingQueue() = runBlocking {
        val repository = QueueRepository.inMemory()
        val outgoing = repository.create(source = null, rows = rows("a", "b"))
        val incoming = repository.create(source = null, rows = rows("c", "d"))
        val player = FakePlayer(
            items = listOf(item("a"), item("b")),
            index = 1,
            position = 12_345L,
            repeat = Player.REPEAT_MODE_ALL,
        )
        val persistence = ActiveQueuePersistence(repository, player, scope = this)
        persistence.adopt(outgoing)
        persistence.activate(incoming)

        // The player still holds the outgoing rows until the incoming queue is loaded.
        val saved = persistence.saveProgressNowAwait()

        assertNull(saved)
        val stored = repository.queue(incoming.id).first()!!
        assertEquals(incoming.bookmark, stored.bookmark)
        assertEquals(incoming.repeatMode, stored.repeatMode)
        assertEquals(outgoing.bookmark, repository.queue(outgoing.id).first()!!.bookmark)
    }
}
