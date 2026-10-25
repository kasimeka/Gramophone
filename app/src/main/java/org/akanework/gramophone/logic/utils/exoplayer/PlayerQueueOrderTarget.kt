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
import org.akanework.gramophone.logic.utils.CircularShuffleOrder
import kotlin.random.Random

/**
 * Applies displayed-order edits to the active queue. With shuffle on the shuffle order is the
 * displayed order. With shuffle off the media item order is the displayed order.
 */
class PlayerQueueOrderTarget(
    private val player: EndedWorkaroundPlayer,
) : QueueOrderTarget {
    private val exoPlayer
        get() = player.exoPlayer

    override val size: Int
        get() = exoPlayer.mediaItemCount

    override fun displayedOrder(): List<Int> {
        if (!exoPlayer.shuffleModeEnabled) return (0 until size).toList()
        val shuffleOrder = exoPlayer.shuffleOrder as CircularShuffleOrder
        return shuffleOrder.shuffledIndices.toList()
    }

    override fun currentPosition(): Int? {
        val current = exoPlayer.currentMediaItemIndex
        if (current == C.INDEX_UNSET) return null
        return displayedOrder().indexOf(current).takeIf { it != -1 }
    }

    override fun applyDisplayedOrder(order: List<Int>) {
        require(order.size == size) {
            "displayed order size ${order.size} does not match media item count $size"
        }
        require(order.toSet().size == order.size) { "displayed order contains duplicate rows" }

        if (exoPlayer.shuffleModeEnabled) {
            setShuffleOrder(order)
            return
        }

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
        val order = displayedOrder()
        require(position in order.indices) { "position $position is outside displayed order" }
        val underlyingIndex = order[position]
        val wasShuffled = exoPlayer.shuffleModeEnabled
        exoPlayer.removeMediaItem(underlyingIndex)
        if (wasShuffled) {
            // ExoPlayer also adjusts its shuffle order on removal. Set the exact displayed order
            // here so the visible list and playback order cannot drift apart.
            val newOrder = order
                .filterIndexed { index, _ -> index != position }
                .map { if (it > underlyingIndex) it - 1 else it }
            setShuffleOrder(newOrder)
        }
    }

    /**
     * Restore the displayed order captured before an edit. [order] holds row identities in
     * displayed order. [capturedItems] is the media-item order before the edit: unshuffled
     * identities are media-item positions, so they shift when items move, and the captured items
     * are needed to map rows back to where they are now. Returns false, changing nothing, when the
     * captured edit no longer matches the queue.
     */
    fun restoreDisplayedOrder(order: List<Int>, capturedItems: List<MediaItem>): Boolean {
        if (order.size != size || capturedItems.size != size) return false
        if (order.toSet().size != order.size) return false
        if (order.any { it !in 0..<size }) return false

        if (exoPlayer.shuffleModeEnabled) {
            applyDisplayedOrder(order)
            return true
        }

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

    private fun setShuffleOrder(order: List<Int>) {
        exoPlayer.setShuffleOrder(
            CircularShuffleOrder(
                player,
                order.toIntArray(),
                Random.nextLong(),
                null,
            )
        )
    }
}
