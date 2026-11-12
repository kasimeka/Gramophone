/*
 *     Copyright (C) 2026 The Gramophone authors
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic.queue

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** The repository's fire-and-forget seam: never touches storage on the caller's thread. */
interface QueueExportSink {
    fun enqueue(queue: Queue)
    fun delete(id: UUID)
}

/** The thin storage adapter beneath the exporter: MediaStore on API 29+, files plus scan below. */
interface QueueMirrorWriter {
    fun write(fileName: String, content: ByteArray)

    /** Remove every mirror for [id] except [keep], so a renamed queue leaves one file. */
    fun deleteOtherFiles(id: UUID, keep: String?)

    fun lastModified(fileName: String): Long?
}

sealed interface PendingExport {
    data class Write(val queue: Queue) : PendingExport
    data class Remove(val id: UUID) : PendingExport
}

/**
 * The one background mirror writer. Mutations only add a dirty marker; storage work happens on
 * [dispatcher] so editing and playback never wait for it. Failures are silent and stay pending
 * until the next mutation (or the next explicit [flush]).
 */
class QueueExporter(
    private val writer: QueueMirrorWriter,
    private val rowSource: (Queue) -> List<QueueExportRow>,
    private val onExported: suspend (UUID, String) -> Unit = { _, _ -> },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coalesceWindowMs: Long = 300L,
) : QueueExportSink {
    private val pending = LinkedHashMap<UUID, PendingExport>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile
    private var started = false

    override fun enqueue(queue: Queue) {
        synchronized(pending) {
            pending[queue.id] = PendingExport.Write(queue)
        }
        wake.trySend(Unit)
    }

    override fun delete(id: UUID) {
        synchronized(pending) {
            pending[id] = PendingExport.Remove(id)
        }
        wake.trySend(Unit)
    }

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                wake.receive()
                // Collect a burst of mutations into one latest-snapshot write.
                delay(coalesceWindowMs)
                flush()
            }
        }
    }

    /** Write every pending snapshot now, bypassing the coalescing window. */
    suspend fun flush() {
        val batch = synchronized(pending) {
            val copy = pending.values.toList()
            pending.clear()
            copy
        }
        withContext(dispatcher) {
            batch.forEach { entry ->
                try {
                    process(entry)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    val id = when (entry) {
                        is PendingExport.Write -> entry.queue.id
                        is PendingExport.Remove -> entry.id
                    }
                    synchronized(pending) { pending.putIfAbsent(id, entry) }
                }
            }
        }
    }

    /**
     * Rebuild mirrors that are missing or older than the queue's last edit. Runs off the main
     * thread; the exporter remains an exporter only, so existing mirror contents are not imported.
     */
    suspend fun reconcile(queues: List<Queue>) {
        withContext(dispatcher) {
            queues.forEach { queue ->
                val fileName = queue.exportFileName
                // MediaStore reports modification time in whole seconds, so a mirror written in
                // the same second as the edit can look up to a second older than the edit.
                val fresh = fileName != null && writer.lastModified(fileName)?.let { modified ->
                    modified + MIRROR_PRECISION_MS >= queue.updatedAt
                } == true
                if (!fresh) {
                    synchronized(pending) {
                        pending[queue.id] = PendingExport.Write(queue)
                    }
                }
            }
        }
        wake.trySend(Unit)
    }

    private suspend fun process(entry: PendingExport) {
        when (entry) {
            is PendingExport.Write -> {
                val queue = entry.queue
                synchronized(pending) {
                    if (pending[queue.id] is PendingExport.Remove) return
                }
                val fileName = QueueExport.fileName(queue.title, queue.id)
                val content = QueueExport.content(rowSource(queue))
                synchronized(pending) {
                    if (pending[queue.id] is PendingExport.Remove) return
                }
                writer.write(fileName, content)
                synchronized(pending) {
                    if (pending[queue.id] is PendingExport.Remove) {
                        // The delete landed during the write; do not keep the file it raced.
                        writer.deleteOtherFiles(queue.id, null)
                        return
                    }
                }
                writer.deleteOtherFiles(queue.id, fileName)
                onExported(queue.id, fileName)
            }

            is PendingExport.Remove -> writer.deleteOtherFiles(entry.id, null)
        }
    }

    private companion object {
        /** MediaStore stores [android.provider.MediaStore.MediaColumns.DATE_MODIFIED] in seconds. */
        const val MIRROR_PRECISION_MS = 1_000L
    }
}
