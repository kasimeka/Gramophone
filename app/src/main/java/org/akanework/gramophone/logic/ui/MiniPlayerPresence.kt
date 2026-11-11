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
 * Whether the current screen allows the mini player. The default is [ALLOWED]; only deliberate
 * exclusions such as playlist editing and the detail dialog use [DENIED].
 */
enum class MiniPlayerScreenPolicy {
    ALLOWED,
    DENIED,
}

/**
 * Pure mini player presence rule. The mini player is shown only while a non-empty active queue
 * exists and the current screen allows it.
 */
object MiniPlayerPresence {
    fun shouldShow(hasActiveQueue: Boolean, screenPolicy: MiniPlayerScreenPolicy): Boolean {
        return hasActiveQueue && screenPolicy == MiniPlayerScreenPolicy.ALLOWED
    }
}
