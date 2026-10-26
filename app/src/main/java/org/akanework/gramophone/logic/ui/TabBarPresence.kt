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

/** Preference key for the tab bar position. Absence means [TABS_AT_BOTTOM_DEFAULT]. */
const val TABS_AT_BOTTOM_KEY = "tabs_at_bottom"
const val TABS_AT_BOTTOM_DEFAULT = true

enum class TabBarPosition {
    TOP,
    BOTTOM,
}

/**
 * Pure tab bar presence rule for the activity-owned bar. The bar only exists in bottom mode,
 * needs at least two enabled sections to be useful, and obeys the same screen policy as the
 * mini player.
 */
object TabBarPresence {
    fun shouldShow(
        position: TabBarPosition,
        hasEnoughSections: Boolean,
        screenPolicy: MiniPlayerScreenPolicy,
    ): Boolean {
        return position == TabBarPosition.BOTTOM &&
                hasEnoughSections &&
                screenPolicy == MiniPlayerScreenPolicy.ALLOWED
    }
}
