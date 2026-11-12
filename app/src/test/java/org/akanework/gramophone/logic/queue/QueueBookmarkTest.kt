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
import org.junit.Test
import kotlin.random.Random

class QueueBookmarkTest {
    @Test
    fun outOfRangeRowClampsToTheQueue() {
        assertEquals(
            QueueBookmark(row = 2, positionMs = 10L),
            QueueBookmark(row = 99, positionMs = 10L).normalized(3)
        )
        assertEquals(
            QueueBookmark(row = 0, positionMs = 10L),
            QueueBookmark(row = -5, positionMs = 10L).normalized(3)
        )
    }

    @Test
    fun emptyQueueHasAnEmptyBookmark() {
        assertEquals(
            QueueBookmark(row = 0, positionMs = 0L, ended = false),
            QueueBookmark(row = 4, positionMs = 100L, ended = true).normalized(0)
        )
    }

    @Test
    fun negativePositionIsNormalized() {
        assertEquals(
            QueueBookmark(row = 1, positionMs = 0L),
            QueueBookmark(row = 1, positionMs = -1L).normalized(3)
        )
    }

    @Test
    fun bookmarkFollowsItsRowThroughAReorder() {
        val oldOrder = listOf(10, 20, 30, 40)
        val newOrder = listOf(30, 10, 40, 20)

        assertEquals(
            QueueBookmark(row = 0, positionMs = 5L),
            QueueBookmark(row = 2, positionMs = 5L).afterReorder(oldOrder, newOrder)
        )
    }

    @Test
    fun bookmarkFollowsTheAnchorThroughQueueShuffle() {
        val oldOrder = listOf(0, 1, 2, 3)
        val shuffled = QueueOrderEditor.shuffleEverything(oldOrder, 2, Random(1))
        val newOrder = when (shuffled) {
            is QueueEditResult.Applied -> shuffled.order
            QueueEditResult.NoOp -> error("shuffle should have been applied")
        }

        assertEquals(
            0,
            QueueBookmark(row = 2).afterReorder(oldOrder, newOrder).row
        )
    }

    @Test
    fun removingARowBeforeTheBookmarkShiftsItBack() {
        assertEquals(
            QueueBookmark(row = 1, positionMs = 5L),
            QueueBookmark(row = 2, positionMs = 5L).afterRemoval(removedRow = 0, rowCountAfter = 3)
        )
    }

    @Test
    fun removingTheBookmarkedRowClampsToANearestSurvivingRow() {
        // The next row takes the same position when there is one.
        assertEquals(
            QueueBookmark(row = 1, positionMs = 5L),
            QueueBookmark(row = 1, positionMs = 5L).afterRemoval(removedRow = 1, rowCountAfter = 3)
        )
        // Removing the last row clamps back to the new last row.
        assertEquals(
            QueueBookmark(row = 2, positionMs = 5L),
            QueueBookmark(row = 3, positionMs = 5L).afterRemoval(removedRow = 3, rowCountAfter = 3)
        )
    }

    @Test
    fun removingARowAfterTheBookmarkLeavesItAlone() {
        assertEquals(
            QueueBookmark(row = 1, positionMs = 5L),
            QueueBookmark(row = 1, positionMs = 5L).afterRemoval(removedRow = 3, rowCountAfter = 3)
        )
    }

    @Test
    fun removingTheOnlyRowLeavesAnEmptyBookmark() {
        assertEquals(
            QueueBookmark(row = 0, positionMs = 0L, ended = false),
            QueueBookmark(row = 0, positionMs = 5L, ended = true)
                .afterRemoval(removedRow = 0, rowCountAfter = 0)
        )
    }
}
