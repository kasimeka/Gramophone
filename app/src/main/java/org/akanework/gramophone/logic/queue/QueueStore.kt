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

import java.util.UUID

/**
 * Synchronous persistence seam behind [QueueRepository]. It stores whole queues and the two pieces
 * of queue metadata the repository needs; serialization and title generation live above it.
 */
interface QueueStore {
    fun queues(): List<Queue>

    fun queue(id: UUID): Queue?

    fun insert(queue: Queue)

    fun update(queue: Queue)

    fun delete(id: UUID)

    /** Current value of the monotonic numbered-queue counter. */
    fun numberedCounter(): Long

    fun setNumberedCounter(counter: Long)

    fun activeQueueId(): UUID?

    fun setActiveQueueId(id: UUID?)
}

/** Store used by tests and by the in-memory repository factory. */
class InMemoryQueueStore : QueueStore {
    private val queues = LinkedHashMap<UUID, Queue>()

    // ponytail: single process-local map; the app uses QueueDao for durability
    private var counter = QueueTitles.FIRST_NUMBERED_COUNTER
    private var activeId: UUID? = null

    override fun queues(): List<Queue> =
        queues.values.sortedWith(compareBy<Queue> { it.manualOrder }.thenBy { it.createdAt }.thenBy { it.id.toString() })

    override fun queue(id: UUID): Queue? = queues[id]

    override fun insert(queue: Queue) {
        require(queue.id !in queues) { "queue ${queue.id} already exists" }
        queues[queue.id] = queue
    }

    override fun update(queue: Queue) {
        require(queue.id in queues) { "queue ${queue.id} does not exist" }
        queues[queue.id] = queue
    }

    override fun delete(id: UUID) {
        queues.remove(id)
        if (activeId == id) activeId = null
    }

    override fun numberedCounter(): Long = counter

    override fun setNumberedCounter(counter: Long) {
        this.counter = counter
    }

    override fun activeQueueId(): UUID? = activeId

    override fun setActiveQueueId(id: UUID?) {
        activeId = id
    }
}
