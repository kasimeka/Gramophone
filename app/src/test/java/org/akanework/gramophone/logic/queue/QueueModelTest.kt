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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueModelTest {
    @Test
    fun createGivesARandomV4IdentityAndTimestamps() {
        val queue = Queue.create(
            title = "Abbey Road",
            source = QueueSource(QueueSourceKind.ALBUM, identifier = "album-1", displayName = "Abbey Road"),
            rows = listOf("row-a"),
            now = 1_000L,
        )

        assertEquals(4, queue.id.version())
        assertEquals(1_000L, queue.createdAt)
        assertEquals(1_000L, queue.updatedAt)
        assertNull(queue.lastPlayedAt)
        assertFalse(queue.hasBeenPlayed)
        assertEquals(QueueBookmark(), queue.bookmark)
        assertEquals(QueueRepeatMode.OFF, queue.repeatMode)
    }

    @Test
    fun createGeneratesDistinctIdentities() {
        val first = Queue.create("One", null, now = 1L)
        val second = Queue.create("One", null, now = 1L)

        assertTrue(first.id != second.id)
    }

    @Test
    fun renamingKeepsIdentityAndEverythingButTheTitle() {
        val original = Queue.create(
            title = "Abbey Road",
            source = QueueSource(QueueSourceKind.ALBUM, displayName = "Abbey Road"),
            rows = listOf("row-a", "row-b"),
            now = 1_000L,
        ).copy(
            bookmark = QueueBookmark(row = 1, positionMs = 5_000L),
            repeatMode = QueueRepeatMode.ALL,
            lastPlayedAt = 1_500L,
        )

        val renamed = original.renamed("My Road", now = 2_000L)

        assertEquals(original.id, renamed.id)
        assertEquals("My Road", renamed.title)
        assertEquals(original.source, renamed.source)
        assertEquals(original.rows, renamed.rows)
        assertEquals(original.bookmark, renamed.bookmark)
        assertEquals(original.repeatMode, renamed.repeatMode)
        assertEquals(1_000L, renamed.createdAt)
        assertEquals(2_000L, renamed.updatedAt)
        assertEquals(1_500L, renamed.lastPlayedAt)
    }

    @Test
    fun reorderingKeepsTheBookmarkOnTheSameSerializedRow() {
        val queue = Queue.create(
            title = "Queue",
            source = null,
            rows = listOf("a", "b", "c", "d"),
            now = 1L,
        ).copy(bookmark = QueueBookmark(row = 2, positionMs = 5_000L))

        val reordered = queue.reordered(listOf(2, 0, 3, 1), now = 2L)

        assertEquals(listOf("c", "a", "d", "b"), reordered.rows)
        assertEquals(QueueBookmark(row = 0, positionMs = 5_000L), reordered.bookmark)
        assertEquals(2L, reordered.updatedAt)
    }

    @Test
    fun reorderingRejectsAnOrderThatWouldLoseOrDuplicateRows() {
        val queue = Queue.create("Queue", null, rows = listOf("a", "b", "c"), now = 1L)

        assertThrows(IllegalArgumentException::class.java) { queue.reordered(listOf(0, 1)) }
        assertThrows(IllegalArgumentException::class.java) {
            queue.reordered(listOf(0, 1, 1))
        }
    }

    @Test
    fun appendingARowKeepsIdentityOrderBookmarkAndRepeat() {
        val queue = Queue.create(
            title = "Queue",
            source = null,
            rows = listOf("a", "b"),
            now = 1L,
        ).copy(
            bookmark = QueueBookmark(row = 1, positionMs = 5_000L),
            repeatMode = QueueRepeatMode.ALL,
        )

        val appended = queue.withAppendedRows(listOf("c"))

        assertEquals(queue.id, appended.id)
        assertEquals(listOf("a", "b", "c"), appended.rows)
        assertEquals(queue.bookmark, appended.bookmark)
        assertEquals(queue.repeatMode, appended.repeatMode)
        assertEquals(queue.createdAt, appended.createdAt)
    }

    @Test
    fun removingARowClampsTheBookmarkToASurvivingRow() {
        val queue = Queue.create(
            title = "Queue",
            source = null,
            rows = listOf("a", "b", "c"),
            now = 1L,
        ).copy(bookmark = QueueBookmark(row = 2, positionMs = 5_000L))

        assertEquals(listOf("a", "b"), queue.withoutRow(position = 2).rows)
        assertEquals(
            QueueBookmark(row = 1, positionMs = 5_000L),
            queue.withoutRow(position = 2).bookmark
        )
        assertEquals(queue, queue.withoutRow(position = 9))
    }

    @Test
    fun savedBookmarkIsNormalizedAgainstTheCurrentRows() {
        val queue = Queue.create("Queue", null, rows = listOf("a", "b", "c"), now = 1L)
            .copy(bookmark = QueueBookmark(row = 99, positionMs = 5_000L))

        assertEquals(QueueBookmark(row = 2, positionMs = 5_000L), queue.normalizedBookmark())
    }

    @Test
    fun repeatModesKeepTheExistingPlayerValues() {
        assertEquals(0, QueueRepeatMode.OFF.value)
        assertEquals(1, QueueRepeatMode.ONE.value)
        assertEquals(2, QueueRepeatMode.ALL.value)
        assertEquals(QueueRepeatMode.ONE, QueueRepeatMode.fromValue(1))
        assertEquals(QueueRepeatMode.ALL, QueueRepeatMode.fromValue(2))
    }

    @Test
    fun unknownRepeatValueFallsBackToOff() {
        assertEquals(QueueRepeatMode.OFF, QueueRepeatMode.fromValue(-1))
        assertEquals(QueueRepeatMode.OFF, QueueRepeatMode.fromValue(99))
    }
}
