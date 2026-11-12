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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class QueueRepositoryExportTest {
    private val sink = RecordingSink()

    private fun repository(): QueueRepository = StoreQueueRepository(
        InMemoryQueueStore(),
        Dispatchers.Unconfined,
        sink,
    )

    @Test
    fun createMutateAndDeleteReachTheExporter() = runBlocking {
        val repository = repository()
        val queue = repository.create(source = null, rows = listOf("a"), now = 10L)

        assertEquals(listOf(queue.id), sink.enqueued.map { it.id })

        repository.rename(queue.id, "Renamed", now = 20L)
        assertEquals("Renamed", sink.enqueued.last().title)
        assertNull(sink.deleted)

        repository.delete(queue.id)
        assertEquals(queue.id, sink.deleted)
    }

    @Test
    fun savingProgressOrRecordingTheMirrorNameDoesNotEnqueueAnExport() = runBlocking {
        val repository = repository()
        val queue = repository.create(source = null, rows = listOf("a"), now = 10L)
        sink.enqueued.clear()

        repository.saveProgress(queue.id, QueueBookmark(row = 1))
        repository.setExportFileName(queue.id, "queue_${queue.id}.m3u8")

        assertEquals(0, sink.enqueued.size)
        val stored = repository.queue(queue.id).first()
        assertEquals("queue_${queue.id}.m3u8", stored?.exportFileName)
        assertEquals(10L, stored?.updatedAt)
    }

    private class RecordingSink : QueueExportSink {
        val enqueued = mutableListOf<Queue>()
        var deleted: UUID? = null

        override fun enqueue(queue: Queue) {
            enqueued += queue
        }

        override fun delete(id: UUID) {
            deleted = id
        }
    }
}
