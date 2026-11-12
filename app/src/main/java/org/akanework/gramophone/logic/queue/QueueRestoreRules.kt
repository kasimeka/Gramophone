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
 * Where a restored queue starts in the player. A saved ended queue always shows its final row;
 * pressing play then restarts the queue from row one.
 */
data class QueueRestorePoint(
    val row: Int,
    val positionMs: Long,
    val ended: Boolean,
)

/** Pure active-queue snapshot and restore decisions, independent of player and storage. */
object QueueRestoreRules {
    /** The row and position to load a stored queue at, or null when it has no rows. */
    fun restorePoint(queue: Queue): QueueRestorePoint? {
        if (queue.rows.isEmpty()) return null
        val bookmark = queue.normalizedBookmark()
        return if (bookmark.ended) {
            QueueRestorePoint(
                row = queue.rows.lastIndex,
                positionMs = bookmark.positionMs,
                ended = true,
            )
        } else {
            QueueRestorePoint(bookmark.row, bookmark.positionMs, ended = false)
        }
    }

    /**
     * Snapshot the player's bookmark. An ended queue is stored on its final row so a cold launch
     * shows the truth even if the player reports a different row at end of playback.
     */
    fun bookmarkFromPlayer(
        row: Int,
        positionMs: Long,
        ended: Boolean,
        rowCount: Int,
    ): QueueBookmark {
        if (rowCount <= 0) return QueueBookmark()
        val bookmark = QueueBookmark(
            row = row.coerceIn(0, rowCount - 1),
            positionMs = positionMs.coerceAtLeast(0L),
            ended = ended,
        )
        return if (ended) bookmark.copy(row = rowCount - 1) else bookmark
    }

    /** Rows need mirroring only when the player's row payloads differ from the stored ones. */
    fun rowsChanged(stored: List<String>, playerRows: List<String>): Boolean = stored != playerRows
}
