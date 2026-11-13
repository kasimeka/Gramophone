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

object QueueDedupe {
    /**
     * Positions of rows whose [keys] value already appeared earlier. The first row for each key is
     * kept; rows with a null key cannot be matched and are kept.
     */
    fun duplicatePositions(keys: List<String?>): List<Int> {
        val seen = HashSet<String>()
        val duplicates = mutableListOf<Int>()
        keys.forEachIndexed { index, key ->
            if (key != null && !seen.add(key)) duplicates.add(index)
        }
        return duplicates
    }
}
