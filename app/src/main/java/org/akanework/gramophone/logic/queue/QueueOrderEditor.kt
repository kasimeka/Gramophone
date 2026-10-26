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
 * Operations the queue row menu can apply to the active queue.
 */
enum class QueueEditOperation {
    PLAY_NEXT,
    REMOVE,
}

/**
 * Result of a queue order edit. [order] is the complete displayed order after the edit.
 */
sealed interface QueueEditResult {
    data class Applied(val order: List<Int>) : QueueEditResult

    data object NoOp : QueueEditResult
}

/**
 * Pure queue order rules. Row identities are positions in the underlying media list; duplicate
 * songs therefore stay distinct because they have different identities.
 */
object QueueOrderEditor {
    /**
     * Play next is only meaningful when the selected row is neither the current row nor the row
     * that already follows it.
     */
    fun canPlayNext(size: Int, currentPosition: Int?, selectedPosition: Int): Boolean {
        if (currentPosition == null) return false
        if (currentPosition !in 0..<size) return false
        if (selectedPosition !in 0..<size) return false
        return selectedPosition != currentPosition && selectedPosition != currentPosition + 1
    }

    /**
     * Move [selectedPosition] to immediately after [currentPosition] in the displayed order.
     * The selected row keeps its identity, so nothing is duplicated.
     */
    fun playNext(
        order: List<Int>,
        currentPosition: Int?,
        selectedPosition: Int,
    ): QueueEditResult {
        if (!canPlayNext(order.size, currentPosition, selectedPosition)) return QueueEditResult.NoOp

        val newOrder = order.toMutableList()
        val row = newOrder.removeAt(selectedPosition)
        val currentAfterRemoval =
            if (selectedPosition < currentPosition!!) currentPosition - 1 else currentPosition
        val targetPosition = currentAfterRemoval + 1
        newOrder.add(targetPosition, row)
        return QueueEditResult.Applied(newOrder)
    }

    /**
     * Shuffle everything is only meaningful when there is an anchor row and at least one other row
     * to reorder after it.
     */
    fun canShuffleEverything(size: Int, anchorPosition: Int?): Boolean {
        if (anchorPosition == null) return false
        if (size <= 1) return false
        return anchorPosition in 0..<size
    }

    /**
     * Move the anchor row to the front, then randomly reorder every other row after it. Every row
     * keeps its identity, so the result is a permutation of [order] with no duplicates or omissions.
     */
    fun shuffleEverything(
        order: List<Int>,
        anchorPosition: Int?,
        random: Random = Random.Default,
    ): QueueEditResult {
        if (!canShuffleEverything(order.size, anchorPosition)) return QueueEditResult.NoOp
        val permutation = QueueShuffle.shuffledOrder(order.size, anchorPosition!!, random)
        return QueueEditResult.Applied(permutation.map { order[it] })
    }

    /**
     * Remove the row at [position]. Playback itself is left to the player; the caller decides
     * which row is current afterwards.
     */
    fun remove(
        order: List<Int>,
        position: Int,
    ): QueueEditResult {
        if (position !in order.indices) return QueueEditResult.NoOp

        val newOrder = order.toMutableList()
        newOrder.removeAt(position)
        return QueueEditResult.Applied(newOrder)
    }
}

/**
 * A queue's displayed order and the operations needed to change it. Player-backed and in-memory
 * implementations both use this seam, so tests can check that display order and playback order
 * agree without touching ExoPlayer.
 */
interface QueueOrderTarget {
    val size: Int

    /** Row identities in the order the user sees, and the order playback follows. */
    fun displayedOrder(): List<Int>

    /** Current row's position in [displayedOrder], or null when nothing is current. */
    fun currentPosition(): Int?

    /** Reorder rows to [order]. This must not change what is currently playing. */
    fun applyDisplayedOrder(order: List<Int>)

    /** Remove the displayed row at [position]. */
    fun removeDisplayedRow(position: Int)
}
