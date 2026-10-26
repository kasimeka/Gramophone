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

package org.akanework.gramophone.logic.utils.exoplayer

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import org.akanework.gramophone.logic.queue.QueueOrderTarget

/**
 * Applies queue-order edits to the active queue. The media item order is the only order: it is
 * displayed, played, edited, exported, and persisted.
 */
class PlayerQueueOrderTarget(
    private val player: EndedWorkaroundPlayer,
) : QueueOrderTarget {
    private val exoPlayer
        get() = player.exoPlayer

    override val size: Int
        get() = exoPlayer.mediaItemCount

    override fun displayedOrder(): List<Int> = (0 until size).toList()

    override fun currentPosition(): Int? {
        val current = exoPlayer.currentMediaItemIndex
        return current.takeIf { it != C.INDEX_UNSET }
    }

    override fun applyDisplayedOrder(order: List<Int>) {
        require(order.size == size) {
            "displayed order size ${order.size} does not match media item count $size"
        }
        require(order.toSet().size == order.size) { "displayed order contains duplicate rows" }

        // Move media items until their order matches the edited displayed order. The current
        // media item keeps playing; only its index changes when it moves.
        val work = (0 until size).toMutableList()
        for (targetPosition in order.indices) {
            val from = work.indexOf(order[targetPosition])
            check(from != -1) { "displayed order references unknown row ${order[targetPosition]}" }
            if (from != targetPosition) {
                exoPlayer.moveMediaItem(from, targetPosition)
                work.add(targetPosition, work.removeAt(from))
            }
        }
    }

    override fun removeDisplayedRow(position: Int) {
        require(position in 0..<size) { "position $position is outside displayed order" }
        exoPlayer.removeMediaItem(position)
    }

    /**
     * Restore the order captured before an edit. [order] holds the media-item order before the
     * edit and [capturedItems] the media items at that time; items may have shifted since then, so
     * rows are matched back by identity. Returns false, changing nothing, when the captured edit
     * no longer matches the queue.
     */
    fun restoreDisplayedOrder(order: List<Int>, capturedItems: List<MediaItem>): Boolean {
        if (order.size != size || capturedItems.size != size) return false
        if (order.toSet().size != order.size) return false
        if (order.any { it !in 0..<size }) return false

        val currentItems = (0 until size).map { exoPlayer.getMediaItemAt(it) }
        val remaining = currentItems.indices.toMutableList()
        val targetOrder = capturedItems.map { captured ->
            val position = remaining.indexOfFirst { currentItems[it] == captured }
            if (position == -1) return false
            remaining.removeAt(position)
        }
        applyDisplayedOrder(targetOrder)
        return true
    }
}
