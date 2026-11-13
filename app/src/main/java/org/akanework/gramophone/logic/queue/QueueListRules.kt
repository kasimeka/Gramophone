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

import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import org.akanework.gramophone.logic.utils.MediaRowCodec

/** What a queue row says about the queue right now. */
enum class QueueRowState {
    PLAYING,
    PAUSED,
    NEVER_PLAYED,
    ENDED,
    EMPTY,
}

/**
 * Everything a queue row needs to render. The row is text-led: a title, a count, the queue's
 * playback state, and its bookmark position. There is deliberately no artwork here.
 */
data class QueueRowPresentation(
    val title: String,
    val songCount: Int,
    val state: QueueRowState,
    /** One-based row of the bookmark, or null when there is no resumable position. */
    val bookmarkRow: Int?,
    /** Formatted bookmark offset, or null when there is no resumable position. */
    val bookmarkPositionText: String?,
    /** Title of the bookmarked song, or null when unavailable or not applicable. */
    val bookmarkSongTitle: String?,
)

/**
 * Pure rules for the Queues tab and the add-to-queue picker. Queue order is stable and
 * user-controlled; callers must provide queues in persisted manual order.
 */
object QueueListRules {
    private val manualOrder = compareBy<Queue> { it.manualOrder }
        .thenBy { it.createdAt }
        .thenBy { it.id.toString() }

    fun ordered(queues: Collection<Queue>): List<Queue> = queues.sortedWith(manualOrder)

    /**
     * The row for one queue. The active queue reports PLAYING only while the player is actually
     * playing; an ended queue keeps its finished state, and an empty queue is a normal empty row.
     */
    fun row(
        queue: Queue,
        isActive: Boolean,
        isPlaying: Boolean,
    ): QueueRowPresentation {
        val empty = queue.rows.isEmpty()
        val bookmark = queue.normalizedBookmark()
        val state = when {
            empty -> QueueRowState.EMPTY
            isActive && isPlaying -> QueueRowState.PLAYING
            bookmark.ended -> QueueRowState.ENDED
            !queue.hasBeenPlayed -> QueueRowState.NEVER_PLAYED
            else -> QueueRowState.PAUSED
        }
        val resumable = !empty && queue.hasBeenPlayed && !bookmark.ended
        val bookmarkSongTitle = if (resumable) {
            queue.rows.getOrNull(bookmark.row)
                ?.let(MediaRowCodec::decode)
                ?.mediaMetadata
                ?.title
                ?.toString()
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return QueueRowPresentation(
            title = queue.title,
            songCount = queue.rows.size,
            state = state,
            bookmarkRow = if (resumable) bookmark.row + 1 else null,
            bookmarkPositionText = if (resumable) {
                convertDurationToTimeStamp(bookmark.positionMs)
            } else {
                null
            },
            bookmarkSongTitle = bookmarkSongTitle,
        )
    }
}
