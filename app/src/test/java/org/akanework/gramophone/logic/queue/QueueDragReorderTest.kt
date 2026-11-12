/*
 *     Copyright (C) 2026 Akane Foundation
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

/**
 * Pins ADR 0003 invariants:
 * - Incremental in-drag reorder (no drop snap)
 * - Offset normalization across swaps keeping dragged item directly under pointer
 * - Scroll compensation preserving pointer alignment during auto-scroll
 * - Bound limits without sticking at boundaries
 */
class QueueDragReorderTest {

    private val rowHeight = 100f
    private val initialItems = listOf("A", "B", "C", "D")

    @Test
    fun smallDeltaBelowThresholdDoesNotSwap() {
        val initial = QueueDragReorder.State(
            items = initialItems,
            currentIndex = 0,
            dragOffset = 0f,
        )

        val result = QueueDragReorder.onDragDelta(
            state = initial,
            deltaY = 40f,
            rowHeightPx = rowHeight,
        )

        assertFalse(result.swapped)
        assertEquals(listOf("A", "B", "C", "D"), result.state.items)
        assertEquals(0, result.state.currentIndex)
        assertEquals(40f, result.state.dragOffset)
    }

    @Test
    fun exceedingHalfRowDownwardSwapsIncrementallyAndNormalizesOffset() {
        val initial = QueueDragReorder.State(
            items = initialItems,
            currentIndex = 0,
            dragOffset = 0f,
        )

        // Delta of 60f exceeds 50f (rowHeight / 2) -> swap with next item (index 1)
        val result = QueueDragReorder.onDragDelta(
            state = initial,
            deltaY = 60f,
            rowHeightPx = rowHeight,
        )

        assertTrue(result.swapped)
        assertEquals(listOf("B", "A", "C", "D"), result.state.items)
        assertEquals(1, result.state.currentIndex)
        // Offset decremented by rowHeight: 60 - 100 = -40f
        assertEquals(-40f, result.state.dragOffset)
    }

    @Test
    fun fastDragDownwardSwapsMultipleSlotsConsecutively() {
        val initial = QueueDragReorder.State(
            items = initialItems,
            currentIndex = 0,
            dragOffset = 0f,
        )

        // Delta of 220f across 100f row height -> swaps 0->1, then 1->2
        val result = QueueDragReorder.onDragDelta(
            state = initial,
            deltaY = 220f,
            rowHeightPx = rowHeight,
        )

        assertTrue(result.swapped)
        assertEquals(listOf("B", "C", "A", "D"), result.state.items)
        assertEquals(2, result.state.currentIndex)
        // 220 - 100 - 100 = 20f
        assertEquals(20f, result.state.dragOffset)
    }

    @Test
    fun upwardDragSwapsIncrementallyAndNormalizesOffset() {
        val initial = QueueDragReorder.State(
            items = listOf("A", "B", "C", "D"),
            currentIndex = 2,
            dragOffset = 0f,
        )

        // Delta of -60f exceeds -50f -> swap with previous item (index 1)
        val result = QueueDragReorder.onDragDelta(
            state = initial,
            deltaY = -60f,
            rowHeightPx = rowHeight,
        )

        assertTrue(result.swapped)
        assertEquals(listOf("A", "C", "B", "D"), result.state.items)
        assertEquals(1, result.state.currentIndex)
        // -60 + 100 = 40f
        assertEquals(40f, result.state.dragOffset)
    }

    @Test
    fun scrollCompensationAdjustsOffsetWithoutPrematureSwappingIfWithinThreshold() {
        val initial = QueueDragReorder.State(
            items = initialItems,
            currentIndex = 1,
            dragOffset = 0f,
        )

        // List auto-scrolled by 15px: visual translation compensates so item stays pinned to finger
        val result = QueueDragReorder.onDragDelta(
            state = initial,
            deltaY = 15f,
            rowHeightPx = rowHeight,
            isScrollCompensation = true,
        )

        assertFalse(result.swapped)
        assertEquals(1, result.state.currentIndex)
        assertEquals(15f, result.state.dragOffset)
    }

    @Test
    fun bottomBoundaryClampsSwapsAtLastIndex() {
        val initial = QueueDragReorder.State(
            items = initialItems,
            currentIndex = 3, // Already at bottom
            dragOffset = 0f,
        )

        // Dragging further down cannot swap beyond list end
        val result = QueueDragReorder.onDragDelta(
            state = initial,
            deltaY = 150f,
            rowHeightPx = rowHeight,
        )

        assertFalse(result.swapped)
        assertEquals(3, result.state.currentIndex)
        assertEquals(listOf("A", "B", "C", "D"), result.state.items)
        assertEquals(150f, result.state.dragOffset)
    }

    @Test
    fun scrollAnchorResetDetectedWhenSwappingAcrossFirstVisibleItem() {
        val initial = QueueDragReorder.State(
            items = initialItems,
            currentIndex = 0,
            dragOffset = 0f,
        )

        val result = QueueDragReorder.onDragDelta(
            state = initial,
            deltaY = 60f,
            rowHeightPx = rowHeight,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )

        assertTrue(result.swapped)
        assertTrue(result.requiresScrollAnchorReset)
    }
}
