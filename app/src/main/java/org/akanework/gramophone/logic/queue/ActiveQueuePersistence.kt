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

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.akanework.gramophone.logic.utils.MediaRowCodec
import java.util.UUID

/**
 * The player fields the active-queue mirror needs, so the mirror can be tested without ExoPlayer.
 */
interface ActiveQueuePlayer {
    fun mediaItemCount(): Int
    fun getMediaItemAt(index: Int): MediaItem
    fun currentMediaItemIndex(): Int
    fun currentPosition(): Long
    fun playbackState(): Int
    fun repeatMode(): Int
}

/** A stored active queue rebuilt into player items. */
data class RestoredActiveQueue(
    val queue: Queue,
    val items: List<MediaItem>,
    val startRow: Int,
    val positionMs: Long,
    val ended: Boolean,
)

/**
 * Keeps the player's one active queue mirrored into the durable repository. Row changes are
 * written immediately; playback progress is written periodically while playing, so process death
 * loses at most one [progressIntervalMs] window. The repository remains the source of truth for
 * restore, bookmark, repeat, and the last-active marker.
 */
class ActiveQueuePersistence(
    private val repository: QueueRepository,
    private val player: ActiveQueuePlayer,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val progressIntervalMs: Long = 1_000L,
) {
    private val mutex = Mutex()
    private var activeId: UUID? = null
    private var lastMirroredRows: List<String> = emptyList()
    private var progressJob: Job? = null

    fun activeQueueId(): UUID? = activeId

    /** Read the stored last active queue without adopting it into this player session. */
    suspend fun readRestored(): RestoredActiveQueue? =
        repository.lastActiveQueue.first()?.let(::rebuild)

    /** Make [queue] the active durable queue and record the activity. */
    suspend fun activate(queue: Queue) {
        activeId = queue.id
        lastMirroredRows = queue.rows
        repository.setActiveQueue(queue.id)
        repository.markPlayed(queue.id)
    }

    /** Remember that the player's current queue is [queue] without rewriting its active marker. */
    fun adopt(queue: Queue) {
        activeId = queue.id
        lastMirroredRows = queue.rows
    }

    /** Clear the durable last-active marker, for example when the active queue is cleared. */
    fun detach() {
        activeId = null
        lastMirroredRows = emptyList()
        stopProgressSaving(save = false)
        scope.launch {
            // A queue activated after this call keeps its own marker.
            if (activeId == null) repository.setActiveQueue(null)
        }
    }

    /** Mirror the player's current rows, bookmark, and repeat now. */
    fun saveNow() {
        val snapshot = snapshot() ?: return
        val id = activeId ?: return
        scope.launch { persist(id, snapshot) }
    }

    /** Awaitable variant used before switching queues. */
    suspend fun saveNowAwait(): Queue? {
        val snapshot = snapshot() ?: return null
        val id = activeId ?: return null
        return persist(id, snapshot)
    }

    /**
     * Persist a queue whose last row was just removed. The player has no snapshot for an empty
     * queue, so write the cleared rows explicitly. Takes the queue identity because the timeline
     * listener may detach the active marker before this runs.
     */
    suspend fun saveClearedNow(queueUuid: UUID): Queue? = mutex.withLock {
        val saved = repository.mutate(queueUuid) {
            it.copy(rows = emptyList(), bookmark = QueueBookmark())
        }
        if (saved != null && activeId == queueUuid) lastMirroredRows = emptyList()
        saved
    }

    /** Save the debounced playback position and repeat mode now. */
    fun saveProgressNow() {
        val snapshot = snapshot() ?: return
        val id = activeId ?: return
        scope.launch { persistProgress(id, snapshot) }
    }

    /** Awaitable progress save, used by tests before switching queues. */
    internal suspend fun saveProgressNowAwait(): Queue? {
        val snapshot = snapshot() ?: return null
        val id = activeId ?: return null
        return persistProgress(id, snapshot)
    }

    /** Start the periodic position save while playback runs. */
    fun startProgressSaving() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                delay(progressIntervalMs)
                val snapshot = snapshot() ?: continue
                val id = activeId ?: continue
                persistProgress(id, snapshot)
            }
        }
    }

    /** Stop the periodic save, writing the final position when [save] is true. */
    fun stopProgressSaving(save: Boolean) {
        progressJob?.cancel()
        progressJob = null
        if (save) saveProgressNow()
    }

    private fun rebuild(queue: Queue): RestoredActiveQueue? {
        val point = QueueRestoreRules.restorePoint(queue) ?: return null
        val items = queue.rows.mapNotNull(MediaRowCodec::decode)
        if (items.isEmpty()) return null
        return RestoredActiveQueue(
            queue = queue,
            items = items,
            startRow = point.row.coerceIn(0, items.lastIndex),
            positionMs = point.positionMs,
            ended = point.ended,
        )
    }

    private fun snapshot(): Snapshot? {
        val count = player.mediaItemCount()
        if (count <= 0) return null
        val rows = (0 until count).map { MediaRowCodec.encode(player.getMediaItemAt(it)) }
        return Snapshot(
            rows = rows,
            bookmark = QueueRestoreRules.bookmarkFromPlayer(
                row = player.currentMediaItemIndex(),
                positionMs = player.currentPosition(),
                ended = player.playbackState() == Player.STATE_ENDED,
                rowCount = count,
            ),
            repeatMode = QueueRepeatMode.fromValue(player.repeatMode()),
        )
    }

    private suspend fun persist(id: UUID, snapshot: Snapshot): Queue? = mutex.withLock {
        // A stale snapshot can arrive after the active queue was cleared; never resurrect rows
        // that the user just removed.
        if (player.mediaItemCount() == 0 && snapshot.rows.isNotEmpty()) return@withLock null
        if (QueueRestoreRules.rowsChanged(lastMirroredRows, snapshot.rows)) {
            repository.mutate(id) {
                it.copy(
                    rows = snapshot.rows,
                    bookmark = snapshot.bookmark,
                    repeatMode = snapshot.repeatMode,
                )
            }?.also { lastMirroredRows = snapshot.rows }
        } else {
            saveProgressLocked(id, snapshot)
        }
    }

    private suspend fun persistProgress(id: UUID, snapshot: Snapshot) = mutex.withLock {
        // A progress tick can race a queue switch: the player may still hold the outgoing rows
        // after the incoming queue became active. Never write those rows' position elsewhere.
        if (QueueRestoreRules.rowsChanged(lastMirroredRows, snapshot.rows)) return@withLock null
        saveProgressLocked(id, snapshot)
    }

    private suspend fun saveProgressLocked(id: UUID, snapshot: Snapshot): Queue? =
        repository.saveProgress(id, snapshot.bookmark, snapshot.repeatMode)

    private data class Snapshot(
        val rows: List<String>,
        val bookmark: QueueBookmark,
        val repeatMode: QueueRepeatMode,
    )
}
