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

object SheetScrollRules {
    /**
     * Determines whether the list is scrolled away from the top and can scroll backward.
     */
    fun canScrollBackward(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int): Boolean {
        return !(firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0)
    }

    /**
     * Calculates the Y delta to consume in onPreScroll.
     * When scrolling downward (availableY > 0) while the list can scroll backward,
     * consume the delta beforehand so parent BottomSheetBehavior does not intercept or drag.
     */
    fun calculatePreScrollConsumedY(availableY: Float, canScrollBackward: Boolean): Float {
        return if (availableY > 0f && canScrollBackward) {
            availableY
        } else {
            0f
        }
    }

    /**
     * Calculates the Y velocity to consume in onPreFling.
     * When flinging downward (velocityY > 0) while the list can scroll backward,
     * consume the velocity beforehand so parent BottomSheetBehavior does not dismiss the sheet.
     */
    fun calculatePreFlingConsumedY(velocityY: Float, canScrollBackward: Boolean): Float {
        return if (velocityY > 0f && canScrollBackward) {
            velocityY
        } else {
            0f
        }
    }
}
