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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueTitlesTest {
    @Test
    fun albumArtistFolderAndGenreUseTheirDisplayName() {
        val cases = mapOf(
            QueueSourceKind.ALBUM to "Abbey Road",
            QueueSourceKind.ARTIST to "The Beatles",
            QueueSourceKind.FOLDER to "Music",
            QueueSourceKind.GENRE to "Rock",
        )

        cases.forEach { (kind, displayName) ->
            val source = QueueSource(kind, identifier = "id", displayName = displayName)
            assertEquals(displayName, QueueTitles.sourceTitle(source, emptyList()))
        }
    }

    @Test
    fun favouritesAndRecentlyAddedUseTheirFixedNames() {
        assertEquals(
            "Favourites",
            QueueTitles.sourceTitle(QueueSource(QueueSourceKind.FAVOURITES), emptyList())
        )
        assertEquals(
            "Recently added",
            QueueTitles.sourceTitle(QueueSource(QueueSourceKind.RECENTLY_ADDED), emptyList())
        )
    }

    @Test
    fun allSongsAndSearchResultsHaveNoSourceDerivedTitle() {
        assertNull(QueueSource(QueueSourceKind.ALL_SONGS).baseTitle)
        assertNull(QueueSource(QueueSourceKind.SEARCH_RESULTS).baseTitle)
        assertThrows(IllegalArgumentException::class.java) {
            QueueTitles.sourceTitle(QueueSource(QueueSourceKind.SEARCH_RESULTS), emptyList())
        }
    }

    @Test
    fun allSongsAndSearchResultsAreNumberedSources() {
        assertTrue(QueueSource(QueueSourceKind.ALL_SONGS).kind.isNumbered)
        assertTrue(QueueSource(QueueSourceKind.SEARCH_RESULTS).kind.isNumbered)
        assertEquals(
            "new queue #7",
            QueueTitles.nextNumberedTitle(7).title
        )
    }

    @Test
    fun collisionUsesTheSmallestFreeInteger() {
        val source = QueueSource(QueueSourceKind.ALBUM, displayName = "Abbey Road")

        assertEquals(
            "Abbey Road 3",
            QueueTitles.sourceTitle(source, listOf("Abbey Road", "Abbey Road 2"))
        )
        assertEquals(
            "Abbey Road 2",
            QueueTitles.sourceTitle(source, listOf("Abbey Road", "Abbey Road 3"))
        )
    }

    @Test
    fun renamingFreesTheOldBaseTitle() {
        val source = QueueSource(QueueSourceKind.ALBUM, displayName = "Abbey Road")
        val renamed = Queue.create(
            title = "Abbey Road",
            source = source,
            now = 1L,
        ).renamed("My Road", now = 2L)
        val otherQueues = listOf(renamed.title, "Abbey Road 2")

        assertEquals("Abbey Road", QueueTitles.sourceTitle(source, otherQueues))
        assertEquals(
            "Abbey Road 3",
            QueueTitles.sourceTitle(source, otherQueues + "Abbey Road")
        )
    }

    @Test
    fun numberedQueuesTakeThePersistedCounterAndAdvanceIt() {
        val first = QueueTitles.nextNumberedTitle(QueueTitles.FIRST_NUMBERED_COUNTER)

        assertEquals("new queue #1", first.title)
        assertEquals(2L, first.nextCounter)
    }

    @Test
    fun aDeletedNumberIsNotReused() {
        val first = QueueTitles.nextNumberedTitle(QueueTitles.FIRST_NUMBERED_COUNTER)
        val second = QueueTitles.nextNumberedTitle(first.nextCounter)
        // Queue #2 is deleted, but the stored counter has already moved past it.
        val afterDeletion = QueueTitles.nextNumberedTitle(second.nextCounter)

        assertEquals("new queue #1", first.title)
        assertEquals("new queue #2", second.title)
        assertEquals("new queue #3", afterDeletion.title)
    }

    @Test
    fun numberedCounterMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            QueueTitles.nextNumberedTitle(0)
        }
    }

    @Test
    fun renameTitlesAreTrimmedAndRejectBlankNames() {
        assertEquals("Roadtrip", QueueTitles.renameTitle("  Roadtrip  ", emptyList()))
        assertNull(QueueTitles.renameTitle("", emptyList()))
        assertNull(QueueTitles.renameTitle("   ", listOf("Roadtrip")))
    }

    @Test
    fun renameCollisionsUseTheSameSmallestFreeSuffixAsGeneratedTitles() {
        assertEquals(
            "Jazz 3",
            QueueTitles.renameTitle("Jazz", listOf("Jazz", "Jazz 2")),
        )
        assertEquals(
            "Jazz 2",
            QueueTitles.renameTitle("Jazz", listOf("Jazz", "Jazz 3")),
        )
        // The queue's own title is excluded by the caller, so keeping it is a no-op, not a suffix.
        assertEquals("Roadtrip", QueueTitles.renameTitle("Roadtrip", emptyList()))
    }
}
