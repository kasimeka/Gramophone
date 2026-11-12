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

/**
 * Pure state and math for drag-and-drop queue reordering (ADR 0003).
 *
 * Invariants:
 * 1. Incremental swaps: Crossing +/- half row height triggers immediate in-drag swap.
 * 2. Finger tracking: Drag offset is decremented/incremented by row height on each swap so the
 *    item stays pinned under the pointer with no drop snap.
 * 3. Scroll compensation: Content scroll delta offsets the visual translation to avoid finger drift.
 * 4. Bounds handling: Bounds clamp downward/upward transitions appropriately without pinning index 0.
 */
object QueueDragReorder {

    data class State<T>(
        val items: List<T>,
        val currentIndex: Int,
        val dragOffset: Float,
    )

    sealed interface StepResult<T> {
        val state: State<T>
        val swapped: Boolean
        val requiresScrollAnchorReset: Boolean

        data class Unchanged<T>(
            override val state: State<T>,
        ) : StepResult<T> {
            override val swapped: Boolean get() = false
            override val requiresScrollAnchorReset: Boolean get() = false
        }

        data class Swapped<T>(
            override val state: State<T>,
            override val requiresScrollAnchorReset: Boolean,
        ) : StepResult<T> {
            override val swapped: Boolean get() = true
        }
    }

    /**
     * Step the drag state by a delta in pixels.
     *
     * @param state Current state of the dragged item and list
     * @param deltaY Movement delta along the vertical drag axis
     * @param rowHeightPx Height of each list item in pixels
     * @param isScrollCompensation Whether this delta comes from edge auto-scroll compensating the list position
     * @param firstVisibleItemIndex Current first visible item index from list state
     * @param firstVisibleItemScrollOffset Current scroll offset of the first visible item
     */
    fun <T> onDragDelta(
        state: State<T>,
        deltaY: Float,
        rowHeightPx: Float,
        isScrollCompensation: Boolean = false,
        firstVisibleItemIndex: Int = 0,
        firstVisibleItemScrollOffset: Int = 0,
    ): StepResult<T> {
        if (deltaY == 0f && !isScrollCompensation) {
            return StepResult.Unchanged(state)
        }

        var currentItems = state.items
        var currentIndex = state.currentIndex
        if (currentIndex !in currentItems.indices) {
            return StepResult.Unchanged(state)
        }

        var offset = state.dragOffset + deltaY
        var didSwap = false
        var anchorResetNeeded = false

        val halfRow = rowHeightPx / 2f

        // Downward swaps: item moves down towards end of list
        while (offset > halfRow && currentIndex < currentItems.lastIndex) {
            val to = currentIndex + 1
            currentItems = currentItems.toMutableList().also { list ->
                list.add(to, list.removeAt(currentIndex))
            }
            currentIndex = to
            offset -= rowHeightPx
            didSwap = true
            if (firstVisibleItemIndex == to - 1 && firstVisibleItemScrollOffset == 0) {
                anchorResetNeeded = true
            }
        }

        // Upward swaps: item moves up towards start of list
        while (offset < -halfRow && currentIndex > 0) {
            val to = currentIndex - 1
            currentItems = currentItems.toMutableList().also { list ->
                list.add(to, list.removeAt(currentIndex))
            }
            currentIndex = to
            offset += rowHeightPx
            didSwap = true
        }

        val nextState = State(
            items = currentItems,
            currentIndex = currentIndex,
            dragOffset = offset,
        )

        return if (didSwap) {
            StepResult.Swapped(nextState, requiresScrollAnchorReset = anchorResetNeeded)
        } else {
            StepResult.Unchanged(nextState)
        }
    }
}
