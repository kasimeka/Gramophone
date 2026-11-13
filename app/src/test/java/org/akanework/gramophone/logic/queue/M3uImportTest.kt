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

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uImportTest {
    @Test
    fun entriesKeepFileOrderAndSkipDirectivesAndBlankLines() {
        val text = "#EXTM3U\n#EXTINF:215,Artist - Song\n/music/a.mp3\n\n#EXTINF:90,B\n/music/b.flac\n"

        assertEquals(listOf("/music/a.mp3", "/music/b.flac"), M3uImport.entries(text))
    }

    @Test
    fun entriesHandleByteOrderMarkCrlfAndFileUris() {
        val text = "\uFEFF#EXTM3U\r\n/music/a.mp3\r\n  file:///music/b.mp3  \r\n"

        assertEquals(listOf("/music/a.mp3", "/music/b.mp3"), M3uImport.entries(text))
    }

    @Test
    fun emptyPlaylistHasNoEntries() {
        assertEquals(emptyList<String>(), M3uImport.entries("#EXTM3U\n\n"))
    }

    @Test
    fun resolveKeepsDuplicatesInOrderAndCountsUnknownEntries() {
        val library = mapOf("/music/a.mp3" to "A", "/music/b.mp3" to "B")

        val result = M3uImport.resolve(
            listOf("/music/b.mp3", "/missing.mp3", "/music/a.mp3", "/music/b.mp3"),
        ) { library[it] }

        assertEquals(listOf("B", "A", "B"), result.matches)
        assertEquals(1, result.unmatched)
    }

    @Test
    fun resolveOfNoKnownSongsMatchesNothing() {
        val result = M3uImport.resolve(listOf("/missing.mp3")) { null as String? }

        assertEquals(emptyList<String>(), result.matches)
        assertEquals(1, result.unmatched)
    }
}
