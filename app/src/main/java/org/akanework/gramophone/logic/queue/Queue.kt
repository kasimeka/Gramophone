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
 * Repeat mode stored with a queue. Values match the player's existing repeat modes, so restoring
 * a queue keeps repeat-one and repeat-all meaning.
 */
enum class QueueRepeatMode(val value: Int) {
    OFF(0),
    ONE(1),
    ALL(2),
    ;

    companion object {
        fun fromValue(value: Int): QueueRepeatMode = entries.firstOrNull { it.value == value } ?: OFF
    }
}

/**
 * The library collection a queue was created from. The kind determines the generated title; the
 * identifier is the source's library identity and never contributes to the title.
 */
enum class QueueSourceKind {
    ALBUM,
    ARTIST,
    FOLDER,
    GENRE,
    FAVOURITES,
    RECENTLY_ADDED,
    ALL_SONGS,
    SEARCH_RESULTS,
    ;

    /** Numbered grab-bags are titled from the queue counter instead of the source. */
    val isNumbered: Boolean
        get() = this == ALL_SONGS || this == SEARCH_RESULTS
}

data class QueueSource(
    val kind: QueueSourceKind,
    val identifier: String? = null,
    val displayName: String? = null,
) {
    /**
     * Base title generated from this source, or null for a numbered grab-bag. Favourites and
     * Recently added are library tabs with fixed names, not titled after a media entry.
     */
    val baseTitle: String?
        get() = when (kind) {
            QueueSourceKind.FAVOURITES -> "Favourites"
            QueueSourceKind.RECENTLY_ADDED -> "Recently added"
            QueueSourceKind.ALL_SONGS,
            QueueSourceKind.SEARCH_RESULTS,
            -> null

            else -> displayName
        }
}

/**
 * Where a queue last stopped. [row] is a position in the queue's single order, so it follows that
 * row when the order changes and clamps to a surviving row when rows are removed.
 */
data class QueueBookmark(
    val row: Int = 0,
    val positionMs: Long = 0L,
    val ended: Boolean = false,
) {
    /** Clamp an out-of-range row to the queue instead of rejecting the bookmark. */
    fun normalized(rowCount: Int): QueueBookmark {
        if (rowCount <= 0) return copy(row = 0, positionMs = 0L, ended = false)
        return copy(
            row = row.coerceIn(0, rowCount - 1),
            positionMs = positionMs.coerceAtLeast(0L),
        )
    }

    /**
     * Follow the bookmarked row through a reorder. The order lists contain row identities, so the
     * bookmark keeps pointing at the same row rather than the same position.
     */
    fun afterReorder(oldOrder: List<Int>, newOrder: List<Int>): QueueBookmark {
        val identity = oldOrder.getOrNull(row) ?: return normalized(oldOrder.size)
        val newRow = newOrder.indexOf(identity)
        return if (newRow >= 0) copy(row = newRow) else normalized(newOrder.size)
    }

    /**
     * Clamp to the nearest surviving row after [removedRow] is removed. Removing the bookmarked
     * row keeps the same position when a later row takes its place, otherwise it clamps to the new
     * last row.
     */
    fun afterRemoval(removedRow: Int, rowCountAfter: Int): QueueBookmark {
        if (removedRow !in 0..<(rowCountAfter + 1)) return normalized(rowCountAfter)
        if (rowCountAfter <= 0) return normalized(rowCountAfter)
        val movedRow = if (removedRow < row) row - 1 else row
        return copy(row = movedRow).normalized(rowCountAfter)
    }
}

/**
 * A durable, named, ordered list of songs with its own bookmark and repeat mode. [rows] are the
 * serialized media-row payloads, so a queue survives a missing or changed MediaStore entry.
 * [manualOrder] is the queue's stable user-controlled position in the queues list.
 */
data class Queue(
    val id: UUID,
    val title: String,
    val source: QueueSource?,
    val rows: List<String>,
    val bookmark: QueueBookmark = QueueBookmark(),
    val repeatMode: QueueRepeatMode = QueueRepeatMode.OFF,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val lastPlayedAt: Long? = null,
    /** Name of the queue's m3u8 mirror, as last written by the background exporter. */
    val exportFileName: String? = null,
    /** Stable position in the queues list. Lower values appear earlier. */
    val manualOrder: Long = createdAt,
    /** Whether the user has manually modified the queue's content, order, or name. */
    val isModified: Boolean = false,
) {
    val hasBeenPlayed: Boolean
        get() = lastPlayedAt != null

    fun normalizedBookmark(): QueueBookmark = bookmark.normalized(rows.size)

    /** Rename a queue. Identity, source, rows, bookmark, and repeat mode are unchanged. */
    fun renamed(title: String, now: Long = System.currentTimeMillis()): Queue =
        copy(title = title, updatedAt = now, isModified = true)

    /**
     * Apply a reorder described by the old row indices in their new sequence. Rows keep their
     * serialized payloads and the bookmark follows the row it pointed at.
     */
    fun reordered(newOrder: List<Int>, now: Long = System.currentTimeMillis()): Queue {
        require(newOrder.size == rows.size && newOrder.toSet() == rows.indices.toSet()) {
            "newOrder must be a permutation of the queue's $rows.size rows"
        }
        val newRows = newOrder.map { rows[it] }
        val newBookmark = bookmark.afterReorder(rows.indices.toList(), newOrder)
        return copy(rows = newRows, bookmark = newBookmark, updatedAt = now)
    }

    /** Add serialized rows at the end without changing the bookmark or the queue's identity. */
    fun withAppendedRows(newRows: List<String>): Queue = copy(rows = rows + newRows, isModified = true)

    /** Add a serialized row at the end without changing the bookmark or the queue's identity. */
    fun withAppendedRow(row: String): Queue = copy(rows = rows + row, isModified = true)

    /** Remove the row at [position], clamping the bookmark to a surviving row. */
    fun withoutRow(position: Int, now: Long = System.currentTimeMillis()): Queue {
        if (position !in rows.indices) return this
        val newRows = rows.toMutableList().apply { removeAt(position) }
        return copy(
            rows = newRows,
            bookmark = bookmark.afterRemoval(position, newRows.size),
            updatedAt = now,
            isModified = true,
        )
    }

    companion object {
        /** Create a queue with a new random v4 identity. The identity never changes afterwards. */
        fun create(
            title: String,
            source: QueueSource?,
            rows: List<String> = emptyList(),
            now: Long = System.currentTimeMillis(),
            id: UUID = UUID.randomUUID(),
            manualOrder: Long = now,
            isModified: Boolean = false,
        ): Queue = Queue(
            id = id,
            title = title,
            source = source,
            rows = rows,
            createdAt = now,
            updatedAt = now,
            manualOrder = manualOrder,
            isModified = isModified,
        )
    }
}
