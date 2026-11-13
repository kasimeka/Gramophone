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
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

/** Reads and writes [SystemQueue]s in the `system_queues` and `system_queue_rows` tables. */
class SystemQueueDao(private val openDatabase: () -> SQLiteDatabase) {
    private val db: SQLiteDatabase
        get() = openDatabase()

    fun load(kind: SystemQueueKind): SystemQueue? {
        db.query(
            "system_queues",
            arrayOf("title", "updated_at", "export_file_name"),
            "id = ?",
            arrayOf(kind.id.toString()),
            null, null, null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return SystemQueue(
                id = kind.id,
                title = cursor.getString(0),
                kind = kind,
                rows = rows(kind.id),
                updatedAt = cursor.getLong(1),
                exportFileName = if (cursor.isNull(2)) null else cursor.getString(2),
            )
        }
    }

    /** Store [rows] as the whole list, creating the queue on first save. Export names are kept. */
    fun save(kind: SystemQueueKind, rows: List<String>, now: Long) {
        val id = kind.id.toString()
        db.beginTransaction()
        try {
            val updated = db.update(
                "system_queues",
                ContentValues().apply { put("updated_at", now) },
                "id = ?",
                arrayOf(id),
            )
            if (updated == 0) {
                db.insertOrThrow(
                    "system_queues",
                    null,
                    ContentValues().apply {
                        put("id", id)
                        put("title", kind.title)
                        put("updated_at", now)
                    },
                )
            }
            db.delete("system_queue_rows", "queue_id = ?", arrayOf(id))
            rows.forEachIndexed { position, payload ->
                db.insertOrThrow(
                    "system_queue_rows",
                    null,
                    ContentValues().apply {
                        put("queue_id", id)
                        put("position", position)
                        put("payload", payload)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setExportFileName(id: UUID, fileName: String) {
        db.update(
            "system_queues",
            ContentValues().apply { put("export_file_name", fileName) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    private fun rows(id: UUID): List<String> {
        db.query(
            "system_queue_rows",
            arrayOf("payload"),
            "queue_id = ?",
            arrayOf(id.toString()),
            null, null,
            "position ASC",
        ).use { cursor ->
            val rows = ArrayList<String>(cursor.count)
            while (cursor.moveToNext()) rows.add(cursor.getString(0))
            return rows
        }
    }
}
