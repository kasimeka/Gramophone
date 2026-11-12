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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The single seam for durable queue operations. Every mutation is serialized here, so callers pass
 * a transform instead of a snapshot they read earlier.
 */
interface QueueRepository {
    /** All queues in stable user-controlled manual order. */
    val queues: Flow<List<Queue>>

    /** The queue currently loaded in the player, or null when none is active. */
    val lastActiveQueue: StateFlow<Queue?>

    fun queue(id: UUID): Flow<Queue?>

    /**
     * Create a new queue from [source] and its complete [rows]. This never reuses an existing
     * queue: source-derived titles collide with the smallest free suffix, and numbered sources
     * take the next monotonic counter value.
     */
    suspend fun create(
        source: QueueSource?,
        rows: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): Queue

    /** Change only a queue's title; identity, rows, bookmark, and repeat mode are unchanged. */
    suspend fun rename(
        id: UUID,
        title: String,
        now: Long = System.currentTimeMillis(),
    ): Queue?

    suspend fun delete(id: UUID): Boolean

    /**
     * Read-modify-write one queue while holding the repository lock. The block receives the
     * current stored queue, so it cannot overwrite a newer change with a stale snapshot. Returning
     * the queue unchanged is a no-op and does not bump [Queue.updatedAt].
     */
    suspend fun mutate(
        id: UUID,
        now: Long = System.currentTimeMillis(),
        block: (Queue) -> Queue,
    ): Queue?

    /**
     * Save the debounced playback bookmark and repeat mode without bumping the queue's
     * [Queue.updatedAt]. Progress is not an edit; the bookmark and the player's repeat choice
     * still persist.
     */
    suspend fun saveProgress(
        id: UUID,
        bookmark: QueueBookmark,
        repeatMode: QueueRepeatMode? = null,
    ): Queue?

    /** Mark [id] as the last active queue, or clear the marker with null. */
    suspend fun setActiveQueue(id: UUID?)

    /**
     * Record the mirror name the exporter last wrote. Like progress, this is not an edit: it does
     * not bump [Queue.updatedAt] and does not enqueue another export.
     */
    suspend fun setExportFileName(id: UUID, fileName: String?): Queue?

    /**
     * Record that [id] became active or started playing, without bumping its edit timestamp.
     * Playback activity does not reorder the queues list.
     */
    suspend fun markPlayed(id: UUID, now: Long = System.currentTimeMillis()): Queue?

    /** Move [id] to [toIndex] in the queues list and persist the new manual order. */
    suspend fun moveQueue(id: UUID, toIndex: Int): Boolean

    companion object {
        fun inMemory(): QueueRepository = StoreQueueRepository(InMemoryQueueStore())
    }
}

class StoreQueueRepository(
    private val store: QueueStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val exports: QueueExportSink? = null,
) : QueueRepository {
    private val mutex = Mutex()

    private val _queues = MutableStateFlow(emptyList<Queue>())
    override val queues: StateFlow<List<Queue>> = _queues

    private val _lastActiveQueue = MutableStateFlow<Queue?>(null)
    override val lastActiveQueue: StateFlow<Queue?> = _lastActiveQueue

    init {
        publish()
    }

    override fun queue(id: UUID): Flow<Queue?> =
        queues.map { all -> all.firstOrNull { it.id == id } }.distinctUntilChanged()

    override suspend fun create(
        source: QueueSource?,
        rows: List<String>,
        now: Long,
    ): Queue = locked {
        val existing = store.queues()
        val existingTitles = existing.map { it.title }
        val numbered = source == null || source.kind.isNumbered
        var nextCounter: Long? = null
        val title = if (numbered) {
            QueueTitles.nextNumberedTitle(store.numberedCounter()).also {
                nextCounter = it.nextCounter
            }.title
        } else {
            QueueTitles.sourceTitle(requireNotNull(source), existingTitles)
        }

        val manualOrder = (existing.maxOfOrNull { it.manualOrder } ?: 0L) + 1L
        val queue = Queue.create(
            title = title,
            source = source,
            rows = rows,
            now = now,
            manualOrder = manualOrder,
        )
        store.insert(queue)
        nextCounter?.let(store::setNumberedCounter)
        publish()
        exports?.enqueue(queue)
        queue
    }

    override suspend fun rename(
        id: UUID,
        title: String,
        now: Long,
    ): Queue? = locked {
        val current = store.queue(id) ?: return@locked null
        val others = store.queues().filter { it.id != id }.map { it.title }
        val resolved = QueueTitles.renameTitle(title, others) ?: return@locked current
        if (resolved == current.title) return@locked current
        commit(current, current.renamed(resolved, now), now)
    }

    override suspend fun delete(id: UUID): Boolean = locked {
        if (store.queue(id) == null) {
            false
        } else {
            store.delete(id)
            publish()
            exports?.delete(id)
            true
        }
    }

    override suspend fun mutate(
        id: UUID,
        now: Long,
        block: (Queue) -> Queue,
    ): Queue? = locked {
        val current = store.queue(id) ?: return@locked null
        val changed = block(current)
        if (changed == current) return@locked current
        commit(current, changed, now)
    }

    /** Persist one changed queue under the repository lock: normalize, publish, and export. */
    private fun commit(current: Queue, changed: Queue, now: Long): Queue {
        val updated = changed.copy(id = current.id, updatedAt = now)
        val normalized = updated.copy(bookmark = updated.bookmark.normalized(updated.rows.size))
        store.update(normalized)
        publish()
        exports?.enqueue(normalized)
        return normalized
    }

    override suspend fun saveProgress(
        id: UUID,
        bookmark: QueueBookmark,
        repeatMode: QueueRepeatMode?,
    ): Queue? = locked {
        val current = store.queue(id) ?: return@locked null
        val changed = current.copy(
            bookmark = bookmark.normalized(current.rows.size),
            repeatMode = repeatMode ?: current.repeatMode,
        )
        if (changed != current) {
            store.update(changed)
            publish()
        }
        changed
    }

    override suspend fun setActiveQueue(id: UUID?) = locked {
        val existing = id?.takeIf { store.queue(it) != null }
        store.setActiveQueueId(existing)
        publish()
    }

    override suspend fun setExportFileName(id: UUID, fileName: String?): Queue? = locked {
        val current = store.queue(id) ?: return@locked null
        val changed = current.copy(exportFileName = fileName)
        if (changed != current) {
            store.update(changed)
            publish()
        }
        changed
    }

    override suspend fun markPlayed(id: UUID, now: Long): Queue? = locked {
        val current = store.queue(id) ?: return@locked null
        val changed = current.copy(lastPlayedAt = now)
        if (changed != current) {
            store.update(changed)
            publish()
        }
        changed
    }

    override suspend fun moveQueue(id: UUID, toIndex: Int): Boolean = locked {
        val ordered = store.queues().toMutableList()
        val fromIndex = ordered.indexOfFirst { it.id == id }
        if (fromIndex < 0 || ordered.isEmpty()) return@locked false
        val target = toIndex.coerceIn(0, ordered.lastIndex)
        if (fromIndex == target) return@locked true
        ordered.add(target, ordered.removeAt(fromIndex))
        ordered.forEachIndexed { index, queue ->
            val desired = index.toLong() + 1L
            if (queue.manualOrder != desired) {
                store.update(queue.copy(manualOrder = desired))
            }
        }
        publish()
        true
    }

    private fun publish() {
        val all = store.queues()
        val activeId = store.activeQueueId()
        _queues.value = QueueListRules.ordered(all)
        _lastActiveQueue.value = all.firstOrNull { it.id == activeId }
    }

    private suspend fun <T> locked(block: () -> T): T =
        mutex.withLock { withContext(ioDispatcher) { block() } }
}
