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
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRestoreRulesTest {
    private fun queue(
        rows: List<String>,
        bookmark: QueueBookmark = QueueBookmark(),
    ) = Queue.create(title = "Roadtrip", source = null, rows = rows).copy(bookmark = bookmark)

    @Test
    fun partialQueueRestoresAtItsSavedRowAndPosition() {
        val point = QueueRestoreRules.restorePoint(
            queue(
                listOf("a", "b", "c"),
                QueueBookmark(row = 1, positionMs = 42_000L),
            )
        )

        assertEquals(QueueRestorePoint(1, 42_000L, ended = false), point)
    }

    @Test
    fun endedQueueRestoresOnItsFinalRow() {
        val point = QueueRestoreRules.restorePoint(
            queue(
                listOf("a", "b", "c"),
                QueueBookmark(row = 0, positionMs = 9_000L, ended = true),
            )
        )

        assertEquals(QueueRestorePoint(2, 9_000L, ended = true), point)
    }

    @Test
    fun emptyQueueHasNoRestorePoint() {
        assertNull(QueueRestoreRules.restorePoint(queue(emptyList())))
    }

    @Test
    fun playerBookmarkIsClampedAndAnEndedQueueIsStoredOnTheFinalRow() {
        assertEquals(
            QueueBookmark(row = 2, positionMs = 5_000L),
            QueueRestoreRules.bookmarkFromPlayer(99, 5_000L, ended = false, rowCount = 3),
        )
        assertEquals(
            QueueBookmark(row = 2, positionMs = 5_000L, ended = true),
            QueueRestoreRules.bookmarkFromPlayer(0, 5_000L, ended = true, rowCount = 3),
        )
        assertEquals(
            QueueBookmark(),
            QueueRestoreRules.bookmarkFromPlayer(2, 10L, ended = true, rowCount = 0),
        )
    }

    @Test
    fun rowsOnlyChangeWhenThePlayerOrderActuallyDiffers() {
        assertFalse(QueueRestoreRules.rowsChanged(listOf("a", "b"), listOf("a", "b")))
        assertTrue(QueueRestoreRules.rowsChanged(listOf("a", "b"), listOf("b", "a")))
        assertTrue(QueueRestoreRules.rowsChanged(listOf("a", "b"), listOf("a", "b", "c")))
    }
}
