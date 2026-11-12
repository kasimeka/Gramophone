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

/**
 * Queue-order target for a stored (inactive) queue. Row identities are positions in the queue's
 * [`Queue.rows`] list, the serialized payloads themselves, so the displayed order is the stored
 * order. Edits produce a new [Queue] with the bookmark following its row, exactly like the
 * player-backed target keeps the playing row.
 */
class StoredQueueOrderTarget(initial: Queue) : QueueOrderTarget {
    var queue: Queue = initial
        private set

    override val size: Int
        get() = queue.rows.size

    override fun displayedOrder(): List<Int> = queue.rows.indices.toList()

    override fun currentPosition(): Int? {
        if (queue.rows.isEmpty()) return null
        return queue.normalizedBookmark().row
    }

    override fun applyDisplayedOrder(order: List<Int>) {
        queue = queue.reordered(order)
    }

    override fun removeDisplayedRow(position: Int) {
        queue = queue.withoutRow(position)
    }
}
