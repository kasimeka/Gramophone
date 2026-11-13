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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The durable queue database. The schema is defined in its current form; backwards compatibility
 * and migration paths are intentionally not maintained yet.
 */
class QueueDatabase(
    context: Context,
    name: String? = DATABASE_NAME,
) : SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE queues (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                source_kind TEXT,
                source_identifier TEXT,
                source_display_name TEXT,
                repeat_mode INTEGER NOT NULL DEFAULT 0,
                bookmark_row INTEGER NOT NULL DEFAULT 0,
                bookmark_position_ms INTEGER NOT NULL DEFAULT 0,
                bookmark_ended INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_played_at INTEGER,
                export_file_name TEXT,
                active INTEGER NOT NULL DEFAULT 0,
                manual_order INTEGER NOT NULL DEFAULT 0,
                is_modified INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE queue_rows (
                queue_id TEXT NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
                position INTEGER NOT NULL,
                payload TEXT NOT NULL,
                PRIMARY KEY (queue_id, position)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE queue_meta (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                numbered_counter INTEGER NOT NULL,
                last_active_id TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO queue_meta (id, numbered_counter, last_active_id) VALUES (1, ?, NULL)",
            arrayOf(QueueTitles.FIRST_NUMBERED_COUNTER),
        )
        db.execSQL(
            """
            CREATE TABLE system_queues (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                export_file_name TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE system_queue_rows (
                queue_id TEXT NOT NULL REFERENCES system_queues(id) ON DELETE CASCADE,
                position INTEGER NOT NULL,
                payload TEXT NOT NULL,
                PRIMARY KEY (queue_id, position)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Backwards compatibility/migrations are intentionally not maintained yet.
    }

    companion object {
        const val DATABASE_NAME = "queues.db"
        const val DATABASE_VERSION = 1
    }
}
