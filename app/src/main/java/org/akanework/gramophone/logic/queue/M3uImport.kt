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

/** The songs an m3u or m3u8 playlist matched, in playlist order, and how many entries matched none. */
data class M3uImportResult<T>(val matches: List<T>, val unmatched: Int)

/** Reads the song entries of an m3u or m3u8 playlist. */
object M3uImport {
    /** Song paths in file order. `#` lines and blank lines are skipped; a `file://` prefix is removed. */
    fun entries(text: String): List<String> =
        text.removePrefix("\uFEFF").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.removePrefix("file://") }
            .toList()

    /** Resolves each entry with [lookup]. Duplicates stay in place; unknown entries are counted. */
    fun <T : Any> resolve(entries: List<String>, lookup: (String) -> T?): M3uImportResult<T> {
        val matches = mutableListOf<T>()
        var unmatched = 0
        for (entry in entries) {
            val item = lookup(entry)
            if (item == null) unmatched++ else matches.add(item)
        }
        return M3uImportResult(matches, unmatched)
    }
}
