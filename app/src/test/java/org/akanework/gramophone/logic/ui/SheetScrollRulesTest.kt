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

package org.akanework.gramophone.logic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetScrollRulesTest {

    @Test
    fun atTopOfListCanScrollBackwardIsFalse() {
        assertFalse(SheetScrollRules.canScrollBackward(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0))
    }

    @Test
    fun scrolledIntoListCanScrollBackwardIsTrue() {
        assertTrue(SheetScrollRules.canScrollBackward(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 10))
        assertTrue(SheetScrollRules.canScrollBackward(firstVisibleItemIndex = 3, firstVisibleItemScrollOffset = 0))
        assertTrue(SheetScrollRules.canScrollBackward(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 50))
    }

    @Test
    fun preScrollConsumesDownwardDeltaOnlyWhenListCanScrollBackward() {
        // available.y > 0 means downward gesture (pulling down to scroll list backward toward top)
        // When list can scroll backward, the sheet connection must consume the downward delta beforehand
        // so BottomSheetBehavior does NOT see or consume it.
        val availableDown = 50f
        val consumedWhenCanScrollBackward = SheetScrollRules.calculatePreScrollConsumedY(
            availableY = availableDown,
            canScrollBackward = true,
        )
        assertEquals(50f, consumedWhenCanScrollBackward, 0.001f)

        // When at top (cannot scroll backward), pre-scroll must NOT consume, allowing BottomSheetBehavior to drag/dismiss.
        val consumedAtTop = SheetScrollRules.calculatePreScrollConsumedY(
            availableY = availableDown,
            canScrollBackward = false,
        )
        assertEquals(0f, consumedAtTop, 0.001f)
    }

    @Test
    fun preScrollDoesNotConsumeUpwardDelta() {
        // available.y < 0 means upward gesture (scrolling forward through list)
        // Must never be consumed by preScroll, allowing list to scroll freely.
        val availableUp = -50f
        val consumedWhenCanScrollBackward = SheetScrollRules.calculatePreScrollConsumedY(
            availableY = availableUp,
            canScrollBackward = true,
        )
        assertEquals(0f, consumedWhenCanScrollBackward, 0.001f)

        val consumedAtTop = SheetScrollRules.calculatePreScrollConsumedY(
            availableY = availableUp,
            canScrollBackward = false,
        )
        assertEquals(0f, consumedAtTop, 0.001f)
    }

    @Test
    fun preFlingConsumesDownwardFlingOnlyWhenListCanScrollBackward() {
        // available.y > 0 means downward fling (flinging downward)
        // When list can scroll backward, consume the downward fling so BottomSheetBehavior does not dismiss.
        val flingVelocityDown = 1200f
        val consumedWhenCanScrollBackward = SheetScrollRules.calculatePreFlingConsumedY(
            velocityY = flingVelocityDown,
            canScrollBackward = true,
        )
        assertEquals(1200f, consumedWhenCanScrollBackward, 0.001f)

        // When at top, let downward fling pass through so sheet can dismiss.
        val consumedAtTop = SheetScrollRules.calculatePreFlingConsumedY(
            velocityY = flingVelocityDown,
            canScrollBackward = false,
        )
        assertEquals(0f, consumedAtTop, 0.001f)
    }

    @Test
    fun preFlingDoesNotConsumeUpwardFling() {
        // available.y < 0 means upward fling
        val flingVelocityUp = -1200f
        val consumed = SheetScrollRules.calculatePreFlingConsumedY(
            velocityY = flingVelocityUp,
            canScrollBackward = true,
        )
        assertEquals(0f, consumed, 0.001f)
    }
}
