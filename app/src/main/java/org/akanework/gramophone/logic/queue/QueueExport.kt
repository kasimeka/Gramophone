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

import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/** One resolvable row of a queue mirror; rows without a path never reach this type. */
data class QueueExportRow(
    val path: String,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
)

/**
 * The pure mirror format: a stable filename derived from the queue title and id, and the m3u8
 * bytes. It writes absolute paths with basic title, artist, and duration metadata.
 */
object QueueExport {
    private val separators = Regex("[\\s._/\\\\:\\-–—]+")
    private val unsafe = Regex("[^a-z0-9_]+")
    private val repeatedUnderscores = Regex("_+")

    /**
     * Lowercase the title, collapse spaces and separators to underscores, strip everything else.
     * A title with no safe characters falls back to `queue`.
     */
    fun slug(title: String): String {
        val separated = separators.replace(title.lowercase(Locale.ROOT), "_")
        val safe = unsafe.replace(separated, "")
        return repeatedUnderscores.replace(safe, "_").trim('_').ifEmpty { "queue" }
    }

    /** The mirror name is stable for a queue id; only the slug follows the title. */
    fun fileName(title: String, id: UUID): String = "${slug(title)}_$id.m3u8"

    /** `#EXTM3U`, then one `#EXTINF:<seconds>,<artist> - <title>` and absolute path per row. */
    fun content(rows: List<QueueExportRow>): ByteArray {
        val out = StringBuilder("#EXTM3U\r\n")
        rows.forEach { row ->
            val seconds = (row.durationMs ?: 0L).coerceAtLeast(0L) / 1000L
            out.append("#EXTINF:").append(seconds).append(',')
                .append(displayName(row)).append("\r\n")
                .append(row.path).append("\r\n")
        }
        return out.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun displayName(row: QueueExportRow): String {
        val artist = row.artist?.trim().orEmpty()
        val title = row.title?.trim().orEmpty()
        return when {
            artist.isNotEmpty() && title.isNotEmpty() -> "$artist - $title"
            title.isNotEmpty() -> title
            artist.isNotEmpty() -> artist
            else -> row.path.substringAfterLast('/').substringBeforeLast('.')
        }
    }
}
