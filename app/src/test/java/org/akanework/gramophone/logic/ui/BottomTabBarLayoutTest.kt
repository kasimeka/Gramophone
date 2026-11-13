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

class BottomTabBarLayoutTest {
    @Test
    fun showsWhenAvailableAndSheetCollapsed() {
        assertTrue(BottomTabBarLayout.isVisible(available = true, sheetSlide = 0f))
    }

    @Test
    fun hidesWhenFullPlayerCoversIt() {
        assertFalse(BottomTabBarLayout.isVisible(available = true, sheetSlide = 1f))
    }

    @Test
    fun hidesWhenNotAvailable() {
        assertFalse(BottomTabBarLayout.isVisible(available = false, sheetSlide = 0f))
    }

    @Test
    fun peekHeightStacksMiniPlayerAboveTabBar() {
        assertEquals(180, BottomTabBarLayout.peekHeight(miniPlayerHeight = 120, tabBarHeight = 60))
    }

    @Test
    fun hiddenTabBarAddsNoPeekHeight() {
        assertEquals(120, BottomTabBarLayout.peekHeight(miniPlayerHeight = 120, tabBarHeight = 0))
    }

    @Test
    fun paddingIncludesMiniPlayerAndTabBarWhenSheetVisible() {
        assertEquals(
            180,
            BottomTabBarLayout.contentBottomPadding(
                sheetVisible = true, miniPlayerHeight = 120, tabBarHeight = 60
            )
        )
    }

    @Test
    fun paddingIsTabBarOnlyWhenSheetHidden() {
        assertEquals(
            60,
            BottomTabBarLayout.contentBottomPadding(
                sheetVisible = false, miniPlayerHeight = 120, tabBarHeight = 60
            )
        )
    }

    @Test
    fun paddingIsZeroWithNeitherBar() {
        assertEquals(
            0,
            BottomTabBarLayout.contentBottomPadding(
                sheetVisible = false, miniPlayerHeight = 120, tabBarHeight = 0
            )
        )
    }

    @Test
    fun tabBarFadesInStepWithTheMiniPlayerAsTheSheetOpens() {
        assertEquals(1f, BottomTabBarLayout.alphaForSlide(0f), 0.001f)
        assertEquals(0.9f, BottomTabBarLayout.alphaForSlide(0.1f), 0.001f)
        assertEquals(0.5f, BottomTabBarLayout.alphaForSlide(0.5f), 0.001f)
        assertEquals(0f, BottomTabBarLayout.alphaForSlide(1f), 0.001f)
    }
}
