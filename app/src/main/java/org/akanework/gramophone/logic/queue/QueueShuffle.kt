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

import kotlin.random.Random

/**
 * Plain permutation used by the shuffle-all action. It keeps the historical shuffle distribution:
 * a full Fisher-Yates permutation, then the list is rotated so [firstIndex] is first.
 */
object QueueShuffle {
    fun shuffledOrder(
        size: Int,
        firstIndex: Int,
        random: Random = Random.Default,
    ): List<Int> {
        require(size > 0) { "size must be positive" }
        require(firstIndex in 0..<size) { "firstIndex $firstIndex is outside 0..<$size" }

        val shuffled = IntArray(size)
        for (i in shuffled.indices) {
            val swapIndex = random.nextInt(i + 1)
            shuffled[i] = shuffled[swapIndex]
            shuffled[swapIndex] = i
        }

        val firstShuffledIndex = shuffled.indexOf(firstIndex)
        val before = shuffled.copyOfRange(0, firstShuffledIndex)
        val fromFirst = shuffled.copyOfRange(firstShuffledIndex, shuffled.size)
        return (fromFirst + before).toList()
    }
}
