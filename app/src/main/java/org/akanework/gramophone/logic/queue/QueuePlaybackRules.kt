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


/** What pressing a play control should do. */
sealed interface QueuePlaybackAction {
    data object Disabled : QueuePlaybackAction

    data object Pause : QueuePlaybackAction

    data class Play(val row: Int, val positionMs: Long) : QueuePlaybackAction
}

/** The playback-relevant state of one queue. */
data class QueuePlaybackState(
    val rowCount: Int,
    val bookmark: QueueBookmark = QueueBookmark(),
    val hasBeenPlayed: Boolean = false,
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
)

/** Outgoing bookmark saved before switching, and the action that starts the incoming queue. */
data class QueueSwitchPlan(
    val savedBookmark: QueueBookmark?,
    val action: QueuePlaybackAction,
)

/**
 * Pure playback decision table. It covers the queue detail play/pause button, the player play
 * button, library plays, library shuffle-all, and switching queues.
 */
object QueuePlaybackRules {
    /**
     * The queue play/pause button. Empty queues are disabled; an active playing queue pauses; an
     * active paused queue resumes; an inactive queue starts at its bookmark, at the first row when
     * never played, or at the first row when it ended. Pressing it on another queue switches to it.
     */
    fun queuePlayButton(state: QueuePlaybackState): QueuePlaybackAction {
        if (state.rowCount <= 0) return QueuePlaybackAction.Disabled
        if (state.isActive && state.isPlaying) return QueuePlaybackAction.Pause

        val bookmark = state.bookmark.normalized(state.rowCount)
        return when {
            bookmark.ended || !state.hasBeenPlayed ->
                QueuePlaybackAction.Play(row = 0, positionMs = 0L)

            else -> QueuePlaybackAction.Play(bookmark.row, bookmark.positionMs)
        }
    }

    /**
     * Switching queues saves the outgoing queue's bookmark before the incoming queue starts. There
     * is no saved bookmark when there is no outgoing queue or it has no rows.
     */
    fun switchQueue(
        outgoing: QueuePlaybackState?,
        incoming: QueuePlaybackState,
    ): QueueSwitchPlan {
        val savedBookmark = outgoing
            ?.takeIf { it.rowCount > 0 }
            ?.let { it.bookmark.normalized(it.rowCount) }
        val action = queuePlayButton(incoming.copy(isActive = false, isPlaying = false))
        return QueueSwitchPlan(savedBookmark = savedBookmark, action = action)
    }

    /**
     * The row Play next and shuffle anchor on in the queue detail. The active queue anchors on
     * the current player row; an inactive queue anchors on its normalized bookmark row.
     */
    fun detailAnchor(
        rowCount: Int,
        bookmark: QueueBookmark,
        isActive: Boolean,
        currentRow: Int?,
    ): Int? {
        if (rowCount <= 0) return null
        if (isActive) return currentRow?.takeIf { it in 0..<rowCount }
        return bookmark.normalized(rowCount).row
    }

    /**
     * Resolves the target row to scroll to when navigating to or requesting the now-playing track
     * in a queue detail view. Only valid indices for the active queue are returned.
     */
    fun nowPlayingScrollTarget(
        rowCount: Int,
        isActive: Boolean,
        currentRow: Int?,
    ): Int? {
        if (!isActive || rowCount <= 0) return null
        return currentRow?.takeIf { it in 0..<rowCount }
    }

    /**
     * A library song menu offers Play next only while a queue is loaded to anchor the insert.
     * With no active queue the action is hidden rather than implying a destination.
     */
    fun libraryPlayNextAvailable(activeQueue: Queue?): Boolean = activeQueue != null

    /** The queue listed after [activeId] in [ordered], or null for the last queue or an unlisted one. */
    fun nextQueueAfter(ordered: List<Queue>, activeId: UUID): Queue? {
        val index = ordered.indexOfFirst { it.id == activeId }
        return if (index < 0) null else ordered.getOrNull(index + 1)
    }
}
