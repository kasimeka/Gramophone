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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.UUID

class QueueExporterTest {
    private val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

    @Test
    fun rapidMutationsCoalesceIntoOneWriteOfTheLatestSnapshot() = runBlocking {
        val writer = FakeWriter()
        val exporter = exporter(writer)
        val queue = queue("First")

        exporter.enqueue(queue)
        exporter.enqueue(queue.renamed("Second"))
        exporter.enqueue(queue.renamed("Third"))
        exporter.flush()

        assertEquals(1, writer.written.size)
        val name = QueueExport.fileName("Third", id)
        assertEquals(listOf(name), writer.files.keys.toList())
        assertTrue(writer.files.getValue(name).toString(Charsets.UTF_8).contains("Third"))
    }

    @Test
    fun renameWritesTheNewNameAndRemovesTheOldFile() = runBlocking {
        val writer = FakeWriter()
        val exporter = exporter(writer)
        exporter.enqueue(queue("Old name"))
        exporter.flush()

        exporter.enqueue(queue("New name"))
        exporter.flush()

        assertEquals(listOf(QueueExport.fileName("New name", id)), writer.files.keys.toList())
        assertEquals(QueueExport.fileName("New name", id), writer.lastExported)
    }

    @Test
    fun deleteRemovesTheFileAndSkipsAPendingWrite() = runBlocking {
        val writer = FakeWriter()
        val exporter = exporter(writer)
        exporter.enqueue(queue("Doomed"))
        exporter.flush()

        exporter.enqueue(queue("Doomed"))
        exporter.delete(id)
        exporter.flush()

        assertTrue(writer.files.isEmpty())
        assertEquals(1, writer.written.size)
    }

    @Test
    fun aDeleteThatRacesAPendingWritePreventsRecreatingTheFile() = runBlocking {
        lateinit var exporter: QueueExporter
        val writer = object : QueueMirrorWriter {
            val files = mutableMapOf<String, ByteArray>()
            val written = mutableListOf<String>()

            override fun write(fileName: String, content: ByteArray) {
                files[fileName] = content
                written += fileName
            }

            override fun deleteOtherFiles(id: UUID, keep: String?) {
                val suffix = "_$id.m3u8"
                files.keys.removeAll { it.endsWith(suffix) && it != keep }
            }

            override fun lastModified(fileName: String): Long? = null
        }
        exporter = QueueExporter(
            writer = writer,
            rowSource = {
                // The delete lands after flush took the snapshot but before the write.
                exporter.delete(id)
                emptyList()
            },
            dispatcher = Dispatchers.Unconfined,
        )

        exporter.enqueue(queue("Doomed"))
        exporter.flush()
        exporter.flush()

        assertTrue("a deleted queue's pending write is skipped", writer.written.isEmpty())
        assertTrue(writer.files.isEmpty())
    }

    @Test
    fun aDeleteDuringTheWriteRemovesTheFileImmediately() = runBlocking {
        lateinit var exporter: QueueExporter
        val writer = object : QueueMirrorWriter {
            val files = mutableMapOf<String, ByteArray>()

            override fun write(fileName: String, content: ByteArray) {
                files[fileName] = content
                exporter.delete(id)
            }

            override fun deleteOtherFiles(id: UUID, keep: String?) {
                val suffix = "_$id.m3u8"
                files.keys.removeAll { it.endsWith(suffix) && it != keep }
            }

            override fun lastModified(fileName: String): Long? = null
        }
        exporter = QueueExporter(
            writer = writer,
            rowSource = { emptyList() },
            dispatcher = Dispatchers.Unconfined,
        )

        exporter.enqueue(queue("Doomed"))
        exporter.flush()

        assertTrue("the just-written mirror is removed without waiting for a retry", writer.files.isEmpty())
    }

    @Test
    fun aFailedWriteIsSilentAndRetriedOnTheNextMutation() = runBlocking {
        val writer = FakeWriter()
        val exporter = exporter(writer)
        writer.failWrites = true

        exporter.enqueue(queue("Flaky"))
        exporter.flush()
        assertTrue(writer.files.isEmpty())

        writer.failWrites = false
        assertTrue("a failed write waits for the next mutation", writer.files.isEmpty())

        exporter.enqueue(queue("Flaky"))
        exporter.flush()
        assertEquals(listOf(QueueExport.fileName("Flaky", id)), writer.files.keys.toList())
    }

    @Test
    fun theBackgroundWorkerCoalescesABurstIntoOneWrite() = runBlocking {
        val writer = FakeWriter()
        val exporter = QueueExporter(
            writer = writer,
            rowSource = { queue ->
                listOf(QueueExportRow("/music/${queue.id}.flac", queue.title))
            },
            dispatcher = Dispatchers.Unconfined,
            coalesceWindowMs = 50L,
        )
        val scope = CoroutineScope(Dispatchers.Unconfined)
        exporter.start(scope)

        exporter.enqueue(queue("First"))
        exporter.enqueue(queue("Second"))
        exporter.enqueue(queue("Third"))
        delay(100L)

        assertEquals(1, writer.written.size)
        assertEquals(QueueExport.fileName("Third", id), writer.files.keys.single())
        scope.cancel()
    }

    @Test
    fun reconcileWritesMissingAndStaleMirrorsOnly() = runBlocking {
        val writer = FakeWriter()
        val exporter = exporter(writer)
        val missingId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val freshId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val staleId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val missing = queue("Missing", updatedAt = 5_000L, id = missingId)
        val fresh = queue("Fresh", updatedAt = 5_000L, id = freshId).copy(
            exportFileName = QueueExport.fileName("Fresh", freshId),
        )
        val stale = queue("Stale", updatedAt = 20_000L, id = staleId).copy(
            exportFileName = QueueExport.fileName("Stale", staleId),
        )
        val freshName = QueueExport.fileName("Fresh", freshId)
        val staleName = QueueExport.fileName("Stale", staleId)
        writer.files[freshName] = ByteArray(0)
        writer.files[staleName] = ByteArray(0)
        writer.timestamps[freshName] = 5_000L
        writer.timestamps[staleName] = 10_000L

        exporter.reconcile(listOf(missing, fresh, stale))
        exporter.flush()

        assertEquals(
            setOf(freshName, staleName, QueueExport.fileName("Missing", missingId)),
            writer.files.keys,
        )
        assertEquals(2, writer.written.size)
    }

    @Test
    fun unresolvedRowsAreOmittedFromTheFileButStayInTheQueue() = runBlocking {
        val writer = FakeWriter()
        val exporter = QueueExporter(
            writer = writer,
            rowSource = { queue ->
                queue.rows.filter { it != "missing" }
                    .map { QueueExportRow("/music/$it.flac", it) }
            },
            dispatcher = Dispatchers.Unconfined,
        )
        val queue = queue("Partial").copy(rows = listOf("a", "missing", "b"))

        exporter.enqueue(queue)
        exporter.flush()

        val content = writer.files.values.single().toString(Charsets.UTF_8)
        assertTrue(content.contains("/music/a.flac"))
        assertTrue(content.contains("/music/b.flac"))
        assertTrue(!content.contains("missing"))
        assertEquals(listOf("a", "missing", "b"), queue.rows)
    }

    @Test
    fun aMirrorWrittenInTheSameSecondAsTheEditIsFresh() = runBlocking {
        val writer = FakeWriter()
        val exporter = exporter(writer)
        val id = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val queue = queue("Seconds", updatedAt = 5_750L, id = id)
        val fileName = QueueExport.fileName("Seconds", id)
        writer.files[fileName] = ByteArray(0)
        // MediaStore truncates DATE_MODIFIED to whole seconds.
        writer.timestamps[fileName] = 5_000L

        exporter.reconcile(listOf(queue.copy(exportFileName = fileName)))
        exporter.flush()

        assertTrue("a mirror in the edit's second is not rebuilt", writer.written.isEmpty())
    }

    private fun exporter(writer: FakeWriter) = QueueExporter(
        writer = writer,
        rowSource = { queue ->
            listOf(QueueExportRow("/music/${queue.id}.flac", queue.title, "Artist", 1_000L))
        },
        onExported = { _, fileName -> writer.lastExported = fileName },
        dispatcher = Dispatchers.Unconfined,
    )

    private fun queue(
        title: String,
        updatedAt: Long = 1_000L,
        id: UUID = this.id,
    ) = Queue.create(
        title = title,
        source = null,
        rows = listOf("row-${title.length}"),
        now = updatedAt,
        id = id,
    )

    private class FakeWriter : QueueMirrorWriter {
        val files = LinkedHashMap<String, ByteArray>()
        val timestamps = mutableMapOf<String, Long>()
        val written = mutableListOf<String>()
        var failWrites = false
        var lastExported: String? = null

        override fun write(fileName: String, content: ByteArray) {
            if (failWrites) throw IOException("disk full")
            files[fileName] = content
            timestamps[fileName] = System.currentTimeMillis()
            written += fileName
        }

        override fun deleteOtherFiles(id: UUID, keep: String?) {
            val suffix = "_$id.m3u8"
            files.keys.removeAll { it.endsWith(suffix) && it != keep }
        }

        override fun lastModified(fileName: String): Long? = timestamps[fileName]
    }
}
