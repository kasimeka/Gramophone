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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Checks queue edits through the same seam the player uses. A queue has one displayed order, which
 * is also its playback, edit, and persistence order.
 */
class QueueOrderTargetTest {
    private val queue = FakeQueueOrderTarget(
        items = listOf(10, 11, 12, 13, 14),
        current = 10,
    )

    @Test
    fun playNextReordersMediaItems() {
        val before = queue.displayedOrder()
        val result = QueueOrderEditor.playNext(before, queue.currentPosition(), 4)
            as QueueEditResult.Applied

        queue.applyDisplayedOrder(result.order)

        assertEquals(listOf(10, 14, 11, 12, 13), queue.underlyingOrder())
        assertEquals(listOf(0, 1, 2, 3, 4), queue.displayedOrder())
        assertEquals(0, queue.currentPosition())
        assertEquals(0, queue.currentIdentity())
    }

    @Test
    fun undoAfterPlayNextRestoresCapturedOrder() {
        val capturedOrder = queue.displayedOrder()
        val capturedItems = queue.underlyingOrder()
        val result = QueueOrderEditor.playNext(capturedOrder, queue.currentPosition(), 4)
            as QueueEditResult.Applied
        queue.applyDisplayedOrder(result.order)

        queue.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, queue.displayedOrder())
        assertEquals(capturedItems, queue.underlyingOrder())
    }

    @Test
    fun undoAfterPlaybackAdvancedLeavesPlaybackWhereItIs() {
        val capturedOrder = queue.displayedOrder()
        val capturedItems = queue.underlyingOrder()
        val result = QueueOrderEditor.playNext(capturedOrder, queue.currentPosition(), 4)
            as QueueEditResult.Applied
        queue.applyDisplayedOrder(result.order)
        // The moved row has already started; undo must not rewind it.
        queue.setCurrent(14)

        queue.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, queue.displayedOrder())
        assertEquals(4, queue.currentIdentity())
        assertEquals(4, queue.currentPosition())
    }

    @Test
    fun removeEditsMediaItems() {
        val before = queue.displayedOrder()
        val result = QueueOrderEditor.remove(before, 2)
            as QueueEditResult.Applied

        queue.removeDisplayedRow(2)

        assertEquals(listOf(10, 11, 13, 14), queue.displayedOrderItems())
        assertEquals(0, queue.currentPosition())
        assertEquals(listOf(0, 1, 3, 4), result.order)
    }

    @Test
    fun undoAfterRemovalRestoresRowAtOriginalPosition() {
        val capturedOrder = queue.displayedOrder()
        val capturedItems = queue.underlyingOrder()
        queue.removeDisplayedRow(2)

        queue.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, queue.displayedOrder())
        assertEquals(capturedItems, queue.underlyingOrder())
    }

    @Test
    fun undoAfterRemovalLeavesAdvancedPlaybackAlone() {
        val capturedOrder = queue.displayedOrder()
        val capturedItems = queue.underlyingOrder()
        queue.removeDisplayedRow(2)
        queue.setCurrent(13)

        queue.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, queue.displayedOrder())
        assertEquals(3, queue.currentIdentity())
    }

    @Test
    fun shuffleActiveQueueAnchorsCurrentRowAndKeepsItPlaying() {
        queue.setCurrent(12)
        val capturedPosition = queue.currentPosition()
        val result = QueueOrderEditor.shuffleEverything(
            queue.displayedOrder(),
            capturedPosition,
            Random(7),
        ) as QueueEditResult.Applied

        queue.applyDisplayedOrder(result.order)

        assertEquals(12, queue.displayedOrderItems().first())
        assertEquals(0, queue.currentPosition())
        assertEquals(0, queue.currentIdentity())
        assertEquals(queue.displayedOrder().size, queue.displayedOrder().toSet().size)
        assertFalse(queue.isOriginal)
    }

    @Test
    fun undoAfterShuffleRestoresTheOneOrder() {
        val capturedOrder = queue.displayedOrder()
        val capturedItems = queue.underlyingOrder()
        val result = QueueOrderEditor.shuffleEverything(
            capturedOrder,
            queue.currentPosition(),
            Random(9),
        ) as QueueEditResult.Applied
        queue.applyDisplayedOrder(result.order)

        queue.undoCapturedOrder(capturedOrder, capturedItems)

        assertEquals(capturedOrder, queue.displayedOrder())
        assertEquals(capturedItems, queue.underlyingOrder())
    }

    @Test
    fun shufflingInactiveQueueChangesOnlyThatQueue() {
        val active = FakeQueueOrderTarget(
            items = listOf(10, 11, 12, 13, 14),
            current = 11,
            isOriginal = true,
        )
        val inactive = FakeQueueOrderTarget(
            items = listOf(20, 21, 22, 23),
            current = 22,
            isOriginal = true,
        )
        val activeOrder = active.displayedOrder()
        val activeIdentity = active.currentIdentity()
        val inactiveAnchor = inactive.currentIdentity()

        val result = QueueOrderEditor.shuffleEverything(
            inactive.displayedOrder(),
            inactive.currentPosition(),
            Random(3),
        ) as QueueEditResult.Applied
        inactive.applyDisplayedOrder(result.order)

        assertEquals(0, inactive.currentIdentity())
        assertEquals(0, inactive.currentPosition())
        assertFalse(inactive.isOriginal)
        assertEquals(activeOrder, active.displayedOrder())
        assertEquals(activeIdentity, active.currentIdentity())
        assertTrue(active.isOriginal)
        assertNotEquals(active.displayedOrderItems(), inactive.displayedOrderItems())
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
    var isOriginal: Boolean = true,
) : QueueOrderTarget {
    private val mediaItems = items.toMutableList()
    private var currentItem: Int? = current
    private var lastRemoved: Pair<Int, Int>? = null

    override val size: Int
        get() = mediaItems.size

    override fun displayedOrder(): List<Int> = mediaItems.indices.toList()

    override fun currentPosition(): Int? = currentIdentity()

    override fun applyDisplayedOrder(order: List<Int>) {
        require(order.size == mediaItems.size && order.toSet().size == order.size)
        val reordered = order.map { mediaItems[it] }.toMutableList()
        mediaItems.clear()
        mediaItems.addAll(reordered)
        isOriginal = false
    }

    override fun removeDisplayedRow(position: Int) {
        val removed = mediaItems.removeAt(position)
        lastRemoved = position to removed
        if (currentItem == removed) currentItem = null
        isOriginal = false
    }

    /** Mirrors Undo: put a removed row back, then restore the captured displayed order. */
    fun undoCapturedOrder(capturedOrder: List<Int>, capturedItems: List<Int>) {
        lastRemoved?.let { (index, item) ->
            mediaItems.add(index, item)
            lastRemoved = null
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
