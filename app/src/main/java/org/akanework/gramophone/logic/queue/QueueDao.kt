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

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

/**
 * SQLite implementation of [QueueStore]. It owns only rows and metadata; title generation,
 * mutation ordering, and flow publication belong to the repository.
 */
class QueueDao(private val openDatabase: () -> SQLiteDatabase) : QueueStore {
    private val db: SQLiteDatabase
        get() = openDatabase()

    override fun queues(): List<Queue> = transaction {
        queryHeaders().map { it.toQueue(rows(it.id)) }
    }

    override fun queue(id: UUID): Queue? = transaction {
        val header = queryHeaders("id = ?", arrayOf(id.toString())).firstOrNull()
        header?.toQueue(rows(id))
    }

    override fun insert(queue: Queue) = transaction {
        db.insertOrThrow("queues", null, queue.headerValues().apply { put("active", 0) })
        insertRows(queue)
    }

    override fun update(queue: Queue) = transaction {
        val changed = db.update(
            "queues",
            queue.headerValues(),
            "id = ?",
            arrayOf(queue.id.toString()),
        )
        require(changed == 1) { "queue ${queue.id} does not exist" }
        db.delete("queue_rows", "queue_id = ?", arrayOf(queue.id.toString()))
        insertRows(queue)
    }

    override fun delete(id: UUID) = transaction {
        val wasActive = activeQueueId() == id
        db.delete("queues", "id = ?", arrayOf(id.toString()))
        if (wasActive) writeActiveQueueId(null)
    }

    override fun numberedCounter(): Long = transaction {
        db.rawQuery(
            "SELECT numbered_counter FROM queue_meta WHERE id = 1",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else QueueTitles.FIRST_NUMBERED_COUNTER
        }
    }

    override fun setNumberedCounter(counter: Long) = transaction {
        db.execSQL(
            "UPDATE queue_meta SET numbered_counter = ? WHERE id = 1",
            arrayOf(counter),
        )
    }

    override fun activeQueueId(): UUID? = transaction {
        db.rawQuery(
            "SELECT last_active_id FROM queue_meta WHERE id = 1",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                UUID.fromString(cursor.getString(0))
            } else {
                null
            }
        }
    }

    override fun setActiveQueueId(id: UUID?) = transaction {
        writeActiveQueueId(id)
    }

    private fun writeActiveQueueId(id: UUID?) {
        db.execSQL("UPDATE queues SET active = 0")
        if (id != null) {
            db.execSQL(
                "UPDATE queues SET active = 1 WHERE id = ?",
                arrayOf(id.toString()),
            )
        }
        db.execSQL(
            "UPDATE queue_meta SET last_active_id = ? WHERE id = 1",
            arrayOf(id?.toString()),
        )
    }

    private fun queryHeaders(
        selection: String? = null,
        args: Array<String>? = null,
    ): List<QueueHeader> {
        val headers = mutableListOf<QueueHeader>()
        db.query(
            "queues",
            HEADER_COLUMNS,
            selection,
            args,
            null,
            null,
            "manual_order ASC, created_at ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) headers.add(cursor.toHeader())
        }
        return headers
    }

    private fun rows(id: UUID): List<String> {
        val rows = mutableListOf<String>()
        db.query(
            "queue_rows",
            arrayOf("payload"),
            "queue_id = ?",
            arrayOf(id.toString()),
            null,
            null,
            "position ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) rows.add(cursor.getString(0))
        }
        return rows
    }

    private fun insertRows(queue: Queue) {
        queue.rows.forEachIndexed { position, payload ->
            db.insertOrThrow(
                "queue_rows",
                null,
                ContentValues().apply {
                    put("queue_id", queue.id.toString())
                    put("position", position)
                    put("payload", payload)
                },
            )
        }
    }

    private inline fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        return try {
            block().also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    private data class QueueHeader(
        val id: UUID,
        val title: String,
        val source: QueueSource?,
        val repeatMode: QueueRepeatMode,
        val bookmark: QueueBookmark,
        val createdAt: Long,
        val updatedAt: Long,
        val lastPlayedAt: Long?,
        val exportFileName: String?,
        val manualOrder: Long,
        val isModified: Boolean,
    ) {
        fun toQueue(rows: List<String>) = Queue(
            id = id,
            title = title,
            source = source,
            rows = rows,
            bookmark = bookmark,
            repeatMode = repeatMode,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastPlayedAt = lastPlayedAt,
            exportFileName = exportFileName,
            manualOrder = manualOrder,
            isModified = isModified,
        )
    }

    private fun Cursor.toHeader(): QueueHeader {
        val sourceKind = getString(getColumnIndexOrThrow("source_kind"))
        return QueueHeader(
            id = UUID.fromString(getString(getColumnIndexOrThrow("id"))),
            title = getString(getColumnIndexOrThrow("title")),
            source = sourceKind?.let { kind ->
                QueueSource(
                    kind = QueueSourceKind.valueOf(kind),
                    identifier = getString(getColumnIndexOrThrow("source_identifier")),
                    displayName = getString(getColumnIndexOrThrow("source_display_name")),
                )
            },
            repeatMode = QueueRepeatMode.fromValue(
                getInt(getColumnIndexOrThrow("repeat_mode"))
            ),
            bookmark = QueueBookmark(
                row = getInt(getColumnIndexOrThrow("bookmark_row")),
                positionMs = getLong(getColumnIndexOrThrow("bookmark_position_ms")),
                ended = getInt(getColumnIndexOrThrow("bookmark_ended")) != 0,
            ),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            lastPlayedAt = getColumnIndexOrThrow("last_played_at")
                .let { if (isNull(it)) null else getLong(it) },
            exportFileName = getColumnIndexOrThrow("export_file_name")
                .let { if (isNull(it)) null else getString(it) },
            manualOrder = getLong(getColumnIndexOrThrow("manual_order")),
            isModified = getInt(getColumnIndexOrThrow("is_modified")) != 0,
        )
    }

    private fun Queue.headerValues(): ContentValues = ContentValues().apply {
        put("id", id.toString())
        put("title", title)
        source?.let {
            put("source_kind", it.kind.name)
            put("source_identifier", it.identifier)
            put("source_display_name", it.displayName)
        } ?: run {
            putNull("source_kind")
            putNull("source_identifier")
            putNull("source_display_name")
        }
        put("repeat_mode", repeatMode.value)
        put("bookmark_row", bookmark.row)
        put("bookmark_position_ms", bookmark.positionMs)
        put("bookmark_ended", if (bookmark.ended) 1 else 0)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        if (lastPlayedAt != null) put("last_played_at", lastPlayedAt) else putNull("last_played_at")
        if (exportFileName != null) put("export_file_name", exportFileName) else putNull("export_file_name")
        put("manual_order", manualOrder)
        put("is_modified", if (isModified) 1 else 0)
    }

    private companion object {
        val HEADER_COLUMNS = arrayOf(
            "id",
            "title",
            "source_kind",
            "source_identifier",
            "source_display_name",
            "repeat_mode",
            "bookmark_row",
            "bookmark_position_ms",
            "bookmark_ended",
            "created_at",
            "updated_at",
            "last_played_at",
            "export_file_name",
            "manual_order",
            "is_modified",
        )
    }
}
