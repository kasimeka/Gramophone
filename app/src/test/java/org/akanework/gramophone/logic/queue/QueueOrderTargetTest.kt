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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks the service's editor application through the same seam the player uses: displayed order
 * and playback order must agree in both unshuffled and shuffled mode, including undo.
 */
class QueueOrderTargetTest {
    private val unshuffled = FakeQueueOrderTarget(
        items = listOf(10, 11, 12, 13, 14),
        current = 10,
    )
    private val shuffled = FakeQueueOrderTarget(
        items = listOf(10, 11, 12, 13, 14),
        current = 11,
        shuffleOrder = listOf(2, 1, 4, 0, 3),
    )

    @Test
    fun playNextReordersMediaItemsWhenUnshuffled() {
        val before = unshuffled.displayedOrder()
        val result = QueueOrderEditor.playNext(before, unshuffled.currentPosition(), 4)
            as QueueEditResult.Applied

        unshuffled.applyDisplayedOrder(result.order)

        assertEquals(listOf(10, 14, 11, 12, 13), unshuffled.underlyingOrder())
        assertEquals(listOf(0, 1, 2, 3, 4), unshuffled.displayedOrder())
        assertEquals(0, unshuffled.currentPosition())
        assertEquals(0, unshuffled.currentIdentity())
    }

    @Test
    fun playNextReordersShuffleOrderWithoutChangingMediaItems() {
        // Displayed rows: [2, 1, 4, 0, 3], current row identity 1 is at position 1.
        val before = shuffled.displayedOrder()
        val result = QueueOrderEditor.playNext(before, shuffled.currentPosition(), 4)
            as QueueEditResult.Applied

        shuffled.applyDisplayedOrder(result.order)

        assertEquals(listOf(2, 1, 3, 4, 0), shuffled.displayedOrder())
        assertEquals(listOf(10, 11, 12, 13, 14), shuffled.underlyingOrder())
        assertEquals(1, shuffled.currentPosition())
        assertEquals(1, shuffled.currentIdentity())
    }

    @Test
    fun undoAfterPlayNextRestoresCapturedOrder() {
        val capturedOrder = unshuffled.displayedOrder()
        val capturedItems = unshuffled.underlyingOrder()
        val result = QueueOrderEditor.playNext(capturedOrder, unshuffled.currentPosition(), 4)
            as QueueEditResult.Applied
        unshuffled.applyDisplayedOrder(result.order)

        unshuffled.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, unshuffled.displayedOrder())
        assertEquals(capturedItems, unshuffled.underlyingOrder())
    }

    @Test
    fun undoAfterPlayNextRestoresShuffleOrder() {
        val capturedOrder = shuffled.displayedOrder()
        val capturedItems = shuffled.underlyingOrder()
        val result = QueueOrderEditor.playNext(capturedOrder, shuffled.currentPosition(), 4)
            as QueueEditResult.Applied
        shuffled.applyDisplayedOrder(result.order)

        shuffled.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, shuffled.displayedOrder())
        assertEquals(capturedItems, shuffled.underlyingOrder())
    }

    @Test
    fun undoAfterPlaybackAdvancedLeavesPlaybackWhereItIs() {
        val capturedOrder = unshuffled.displayedOrder()
        val capturedItems = unshuffled.underlyingOrder()
        val result = QueueOrderEditor.playNext(capturedOrder, unshuffled.currentPosition(), 4)
            as QueueEditResult.Applied
        unshuffled.applyDisplayedOrder(result.order)
        // The moved row has already started; undo must not rewind it.
        unshuffled.setCurrent(14)

        unshuffled.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, unshuffled.displayedOrder())
        assertEquals(4, unshuffled.currentIdentity())
        assertEquals(4, unshuffled.currentPosition())
    }

    @Test
    fun removeEditsMediaItemsWhenUnshuffled() {
        val before = unshuffled.displayedOrder()
        val result = QueueOrderEditor.remove(before, 2)
            as QueueEditResult.Applied

        unshuffled.removeDisplayedRow(2)

        assertEquals(listOf(10, 11, 13, 14), unshuffled.displayedOrderItems())
        assertEquals(0, unshuffled.currentPosition())
        assertEquals(listOf(0, 1, 3, 4), result.order)
    }

    @Test
    fun removeEditsShuffleOrderWhenShuffled() {
        // Displayed rows: [2, 1, 4, 0, 3], remove displayed position 2 (identity 4).
        val result = QueueOrderEditor.remove(shuffled.displayedOrder(), 2)
            as QueueEditResult.Applied

        shuffled.removeDisplayedRow(2)

        assertEquals(listOf(2, 1, 0, 3), shuffled.displayedOrder())
        assertEquals(listOf(10, 11, 12, 13), shuffled.underlyingOrder())
        assertEquals(listOf(2, 1, 0, 3), result.order)
    }

    @Test
    fun undoAfterUnshuffledRemovalRestoresRowAtOriginalPosition() {
        val capturedOrder = unshuffled.displayedOrder()
        val capturedItems = unshuffled.underlyingOrder()
        unshuffled.removeDisplayedRow(2)

        unshuffled.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, unshuffled.displayedOrder())
        assertEquals(capturedItems, unshuffled.underlyingOrder())
    }

    @Test
    fun undoAfterShuffledRemovalRestoresRowAndShuffleOrder() {
        val capturedOrder = shuffled.displayedOrder()
        val capturedItems = shuffled.underlyingOrder()
        shuffled.removeDisplayedRow(2)

        shuffled.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, shuffled.displayedOrder())
        assertEquals(capturedItems, shuffled.underlyingOrder())
    }

    @Test
    fun undoAfterShuffledRemovalLeavesAdvancedPlaybackAlone() {
        val capturedOrder = shuffled.displayedOrder()
        val capturedItems = shuffled.underlyingOrder()
        shuffled.removeDisplayedRow(3)
        // Playback advanced from identity 0 to identity 3 before the user tapped Undo.
        shuffled.setCurrent(13)

        shuffled.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, shuffled.displayedOrder())
        assertEquals(3, shuffled.currentIdentity())
    }
}

/**
 * In-memory stand-in for the player's ordering primitives. Row identities are positions in the
 * underlying media list, exactly like [org.akanework.gramophone.logic.utils.exoplayer.PlayerQueueOrderTarget].
 * [mediaItems] holds distinguishable stand-ins for media items so moves can be checked.
 */
private class FakeQueueOrderTarget(
    items: List<Int>,
    current: Int,
    shuffleOrder: List<Int>? = null,
) : QueueOrderTarget {
    private val mediaItems = items.toMutableList()
    private var shuffleOrder = shuffleOrder?.toMutableList()
    private var currentItem: Int? = current
    private var lastRemoved: Pair<Int, Int>? = null

    override val size: Int
        get() = mediaItems.size

    override fun displayedOrder(): List<Int> =
        shuffleOrder?.toList() ?: mediaItems.indices.toList()

    override fun currentPosition(): Int? {
        val underlying = currentIdentity() ?: return null
        return if (shuffleOrder == null) {
            underlying
        } else {
            shuffleOrder!!.indexOf(underlying).takeIf { it != -1 }
        }
    }

    override fun applyDisplayedOrder(order: List<Int>) {
        require(order.size == mediaItems.size && order.toSet().size == order.size)
        if (shuffleOrder != null) {
            shuffleOrder = order.toMutableList()
            return
        }
        val reordered = order.map { mediaItems[it] }.toMutableList()
        mediaItems.clear()
        mediaItems.addAll(reordered)
    }

    override fun removeDisplayedRow(position: Int) {
        val order = displayedOrder()
        val underlyingIndex = order[position]
        val removed = mediaItems.removeAt(underlyingIndex)
        lastRemoved = underlyingIndex to removed
        if (shuffleOrder != null) {
            shuffleOrder = order
                .filterIndexed { index, _ -> index != position }
                .map { if (it > underlyingIndex) it - 1 else it }
                .toMutableList()
        }
        if (currentItem == removed) currentItem = null
    }

    /** Mirrors the service undo: put a removed row back, then restore the captured displayed order. */
    fun undoCapturedOrder(capturedOrder: List<Int>, capturedItems: List<Int>) {
        lastRemoved?.let { (index, item) ->
            mediaItems.add(index, item)
            lastRemoved = null
        }
        if (shuffleOrder != null) {
            applyDisplayedOrder(capturedOrder)
            return
        }
        val remaining = mediaItems.indices.toMutableList()
        val targetOrder = capturedItems.map { captured ->
            val position = remaining.indexOfFirst { mediaItems[it] == captured }
            remaining.removeAt(position)
        }
        applyDisplayedOrder(targetOrder)
    }

    fun underlyingOrder(): List<Int> = mediaItems.toList()

    fun displayedOrderItems(): List<Int> = displayedOrder().map { mediaItems[it] }

    fun currentIdentity(): Int? =
        currentItem?.let { item -> mediaItems.indexOf(item).takeIf { it != -1 } }

    fun setCurrent(item: Int?) {
        currentItem = item
    }
}
