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
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueOrderEditorTest {
    @Test
    fun playNextIsDisabledWithoutCurrentRow() {
        assertFalse(QueueOrderEditor.canPlayNext(4, null, 0))
        assertEquals(
            QueueEditResult.NoOp,
            QueueOrderEditor.playNext(listOf(0, 1, 2, 3), null, 0)
        )
    }

    @Test
    fun playNextIsDisabledOnCurrentRow() {
        assertFalse(QueueOrderEditor.canPlayNext(4, 2, 2))
        assertEquals(
            QueueEditResult.NoOp,
            QueueOrderEditor.playNext(listOf(0, 1, 2, 3), 2, 2)
        )
    }

    @Test
    fun playNextIsDisabledWhenRowIsAlreadyNext() {
        assertFalse(QueueOrderEditor.canPlayNext(4, 1, 2))
        assertEquals(
            QueueEditResult.NoOp,
            QueueOrderEditor.playNext(listOf(0, 1, 2, 3), 1, 2)
        )
    }

    @Test
    fun playNextIsDisabledForInvalidPositions() {
        assertFalse(QueueOrderEditor.canPlayNext(4, -1, 0))
        assertFalse(QueueOrderEditor.canPlayNext(4, 4, 0))
        assertFalse(QueueOrderEditor.canPlayNext(4, 0, -1))
        assertFalse(QueueOrderEditor.canPlayNext(4, 0, 4))
    }

    @Test
    fun playNextMovesRowImmediatelyAfterCurrent() {
        assertEquals(
            QueueEditResult.Applied(listOf(0, 4, 1, 2, 3)),
            QueueOrderEditor.playNext(listOf(0, 1, 2, 3, 4), 0, 4)
        )
    }

    @Test
    fun playNextFromBeforeCurrentKeepsCurrentIdentity() {
        // Removing row 1 shifts the current row 3 down, then row 1 goes after it.
        assertEquals(
            QueueEditResult.Applied(listOf(0, 2, 3, 1, 4)),
            QueueOrderEditor.playNext(listOf(0, 1, 2, 3, 4), 3, 1)
        )
    }

    @Test
    fun playNextWhenCurrentIsLastMakesSelectedRowLast() {
        assertEquals(
            QueueEditResult.Applied(listOf(0, 2, 3, 1)),
            QueueOrderEditor.playNext(listOf(0, 1, 2, 3), 3, 1)
        )
    }

    @Test
    fun playNextNeverDuplicatesARow() {
        val order = listOf(0, 1, 2, 3)
        val result = QueueOrderEditor.playNext(order, 0, 3)
        assertTrue(result is QueueEditResult.Applied)
        val newOrder = (result as QueueEditResult.Applied).order
        assertEquals(order.size, newOrder.size)
        assertEquals(order.toSet(), newOrder.toSet())
    }

    @Test
    fun playNextOnlyMovesTheSelectedDuplicate() {
        // Row identities stay distinct even when the same song appears more than once.
        val result = QueueOrderEditor.playNext(listOf(0, 1, 2, 3), 1, 3)
        assertEquals(listOf(0, 1, 3, 2), (result as QueueEditResult.Applied).order)
    }

    @Test
    fun removeKeepsRemainingOrder() {
        assertEquals(
            QueueEditResult.Applied(listOf(0, 1, 3, 4)),
            QueueOrderEditor.remove(listOf(0, 1, 2, 3, 4), 2)
        )
    }

    @Test
    fun removeWithInvalidPositionIsNoOp() {
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.remove(listOf(0, 1), -1))
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.remove(listOf(0, 1), 2))
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.remove(emptyList(), 0))
    }
}
