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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class QueueDeduplicationRulesTest {

    private fun createQueue(
        id: UUID = UUID.randomUUID(),
        source: QueueSource?,
        isModified: Boolean = false,
        createdAt: Long = 0L,
        updatedAt: Long = createdAt,
        lastPlayedAt: Long? = null,
    ): Queue = Queue(
        id = id,
        title = source?.displayName ?: "Queue",
        source = source,
        rows = emptyList(),
        isModified = isModified,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastPlayedAt = lastPlayedAt,
    )

    @Test
    fun exactSourceMatchFindsUnmodifiedReplica() {
        val albumSource = QueueSource(QueueSourceKind.ALBUM, identifier = "album-1", displayName = "Abbey Road")
        val queue = createQueue(source = albumSource, isModified = false)

        val found = QueueDeduplicationRules.findUnmodifiedReplica(listOf(queue), albumSource)
        assertEquals(queue, found)
    }

    @Test
    fun modifiedQueueIsIgnored() {
        val albumSource = QueueSource(QueueSourceKind.ALBUM, identifier = "album-1", displayName = "Abbey Road")
        val queue = createQueue(source = albumSource, isModified = true)

        val found = QueueDeduplicationRules.findUnmodifiedReplica(listOf(queue), albumSource)
        assertNull(found)
    }

    @Test
    fun searchQueryMatchesExactQuery() {
        val search1 = QueueSource(QueueSourceKind.SEARCH_RESULTS, identifier = "Radiohead", displayName = "Radiohead")
        val search2 = QueueSource(QueueSourceKind.SEARCH_RESULTS, identifier = "Coldplay", displayName = "Coldplay")
        val queue1 = createQueue(source = search1, isModified = false)

        val foundMatch = QueueDeduplicationRules.findUnmodifiedReplica(listOf(queue1), search1)
        assertEquals(queue1, foundMatch)

        val foundMismatch = QueueDeduplicationRules.findUnmodifiedReplica(listOf(queue1), search2)
        assertNull(foundMismatch)
    }

    @Test
    fun returnsMostRecentlyActiveOrCreatedReplicaWhenMultipleExist() {
        val artistSource = QueueSource(QueueSourceKind.ARTIST, identifier = "artist-1", displayName = "Radiohead")
        val older = createQueue(source = artistSource, isModified = false, createdAt = 100L)
        val newer = createQueue(source = artistSource, isModified = false, createdAt = 200L)

        val found = QueueDeduplicationRules.findUnmodifiedReplica(listOf(older, newer), artistSource)
        assertEquals(newer, found)
    }

    @Test
    fun returnsNullIfSourceNullOrDifferentKind() {
        val albumSource = QueueSource(QueueSourceKind.ALBUM, identifier = "1", displayName = "Album")
        val artistSource = QueueSource(QueueSourceKind.ARTIST, identifier = "1", displayName = "Artist")
        val queue = createQueue(source = albumSource, isModified = false)

        assertNull(QueueDeduplicationRules.findUnmodifiedReplica(listOf(queue), artistSource))
    }

    @Test
    fun deduplicatesAcrossAlbumArtistAndSearchSources() {
        val albumSource = QueueSource(QueueSourceKind.ALBUM, identifier = "album-42", displayName = "OK Computer")
        val artistSource = QueueSource(QueueSourceKind.ARTIST, identifier = "artist-7", displayName = "Radiohead")
        val searchSource = QueueSource(QueueSourceKind.SEARCH_RESULTS, identifier = "Paranoid", displayName = "Search: Paranoid")

        val albumQueue = createQueue(source = albumSource, isModified = false, createdAt = 100L)
        val artistQueue = createQueue(source = artistSource, isModified = false, createdAt = 150L)
        val searchQueue = createQueue(source = searchSource, isModified = false, createdAt = 200L)

        val queues = listOf(albumQueue, artistQueue, searchQueue)

        assertEquals(albumQueue, QueueDeduplicationRules.findUnmodifiedReplica(queues, albumSource))
        assertEquals(artistQueue, QueueDeduplicationRules.findUnmodifiedReplica(queues, artistSource))
        assertEquals(searchQueue, QueueDeduplicationRules.findUnmodifiedReplica(queues, searchSource))
    }

    @Test
    fun deduplicatesFavouritesRecentlyAddedAndSeparatesAllSongsFromDates() {
        val favSource = QueueSource(QueueSourceKind.FAVOURITES, identifier = "favourites", displayName = "Favourites")
        val recentSource = QueueSource(QueueSourceKind.RECENTLY_ADDED, identifier = "recently_added", displayName = "Recently added")
        val allSongsSource = QueueSource(QueueSourceKind.ALL_SONGS, identifier = "all_songs", displayName = "Songs")
        val dateSource = QueueSource(QueueSourceKind.ALL_SONGS, identifier = "date:2024", displayName = "2024")

        val favQueue = createQueue(source = favSource, isModified = false, createdAt = 100L)
        val recentQueue = createQueue(source = recentSource, isModified = false, createdAt = 200L)
        val allSongsQueue = createQueue(source = allSongsSource, isModified = false, createdAt = 300L)
        val dateQueue = createQueue(source = dateSource, isModified = false, createdAt = 400L)

        val queues = listOf(favQueue, recentQueue, allSongsQueue, dateQueue)

        assertEquals(favQueue, QueueDeduplicationRules.findUnmodifiedReplica(queues, favSource))
        assertEquals(recentQueue, QueueDeduplicationRules.findUnmodifiedReplica(queues, recentSource))
        assertEquals(allSongsQueue, QueueDeduplicationRules.findUnmodifiedReplica(queues, allSongsSource))
        assertEquals(dateQueue, QueueDeduplicationRules.findUnmodifiedReplica(queues, dateSource))
        assertNull(
            QueueDeduplicationRules.findUnmodifiedReplica(
                listOf(allSongsQueue),
                QueueSource(QueueSourceKind.ALL_SONGS, identifier = "date:2024", displayName = "2024"),
            )
        )
    }

    @Test
    fun repeatedPlayAndShufflePlayReuseSameUnmodifiedReplica() {
        val allSongsSource = QueueSource(QueueSourceKind.ALL_SONGS, identifier = "all_songs", displayName = "Songs")
        val original = createQueue(source = allSongsSource, isModified = false, createdAt = 100L, lastPlayedAt = 100L)
        val reshuffledUnmodified = original.copy(rows = listOf("r2", "r1"), lastPlayedAt = 200L, isModified = false)

        assertEquals(
            reshuffledUnmodified,
            QueueDeduplicationRules.findUnmodifiedReplica(listOf(reshuffledUnmodified), allSongsSource),
        )
    }
}
