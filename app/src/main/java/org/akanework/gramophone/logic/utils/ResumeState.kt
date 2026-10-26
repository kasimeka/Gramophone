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

package org.akanework.gramophone.logic.utils

/**
 * The resume state as it is stored and restored. [rows] are serialized media rows; this model
 * deliberately does not know about MediaItem or SharedPreferences.
 */
data class ResumeState(
    val rows: List<String>,
    val currentRow: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val ended: Boolean,
    val speed: Float,
    val pitch: Float,
) {
    /**
     * Returns a usable resume state, or null when the stored state is empty or malformed.
     * An out-of-range current row is clamped to a row that exists, including the off-by-one
     * case where the saved row equals the row count.
     */
    fun normalized(): ResumeState? {
        if (rows.isEmpty() || rows.any { it.isBlank() }) return null
        return copy(currentRow = currentRow.coerceIn(0, rows.lastIndex))
    }

    /**
     * Builds the preference-shaped payload. Rows are kept as a set plus an order string, which is
     * the existing persisted shape.
     */
    fun toStored(): StoredResumeState {
        val trimmedRows = rows.map { it.trim() }
        return StoredResumeState(
            rowSet = trimmedRows.toSet(),
            rowOrder = trimmedRows.joinToString(",") { it.hashCode().toString() },
            currentRow = currentRow,
            positionMs = positionMs,
            repeatMode = repeatMode,
            ended = ended,
            speed = speed,
            pitch = pitch,
        )
    }

    companion object {
        fun fromStored(stored: StoredResumeState): ResumeState? {
            val rows = try {
                parseRows(stored.rowSet, stored.rowOrder)
            } catch (_: Exception) {
                return null
            }
            return ResumeState(
                rows = rows,
                currentRow = stored.currentRow,
                positionMs = stored.positionMs,
                repeatMode = stored.repeatMode,
                ended = stored.ended,
                speed = stored.speed,
                pitch = stored.pitch,
            ).normalized()
        }

        private fun parseRows(rowSet: Set<String>, rowOrder: String): List<String> {
            if (rowOrder.isEmpty()) return emptyList()
            return rowOrder.split(",").map { hash ->
                rowSet.firstOrNull { it.hashCode().toString() == hash }
                    ?: throw NoSuchElementException("missing saved row for $hash")
            }
        }
    }
}

/**
 * Preference-shaped resume state. This is the serialization boundary; the preference store only
 * reads and writes these fields.
 */
data class StoredResumeState(
    val rowSet: Set<String>,
    val rowOrder: String,
    val currentRow: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val ended: Boolean,
    val speed: Float,
    val pitch: Float,
)
