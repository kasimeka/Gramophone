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

/**
 * Pure layout rules for the bottom section tab bar. The tab bar sits directly under the mini
 * player, and the full player covers it when the sheet is expanded or half-expanded.
 */
object BottomTabBarLayout {
    fun isVisible(available: Boolean, coveredBySheet: Boolean): Boolean {
        return available && !coveredBySheet
    }

    /** Collapsed peek height: the mini player rests above the tab bar. */
    fun peekHeight(miniPlayerHeight: Int, tabBarHeight: Int): Int {
        return miniPlayerHeight + tabBarHeight
    }

    /** Content bottom padding: the mini player only counts while the sheet is on screen. */
    fun contentBottomPadding(sheetVisible: Boolean, miniPlayerHeight: Int, tabBarHeight: Int): Int {
        return (if (sheetVisible) miniPlayerHeight else 0) + tabBarHeight
    }

    /** Slide offset at which the tab bar has finished fading out as the sheet opens. */
    private const val FADE_OUT_END = 0.2f

    /** Tab bar alpha for a sheet slide offset: fully shown when collapsed, gone before the sheet opens. */
    fun alphaForSlide(slideOffset: Float): Float {
        return (1f - slideOffset / FADE_OUT_END).coerceIn(0f, 1f)
    }
}
