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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class QueueExportTest {
    @Test
    fun slugsAreLowercaseUnderscoreSeparatedAndStripUnsafeCharacters() {
        assertEquals("abbey_road", QueueExport.slug("Abbey Road"))
        assertEquals("ac_dc_live", QueueExport.slug("AC/DC: Live"))
        assertEquals("dont_stop_me_now", QueueExport.slug("Don't Stop Me Now!"))
        assertEquals("new_queue_3", QueueExport.slug("new queue #3"))
        assertEquals("multiple_spaces", QueueExport.slug("  Multiple   spaces  "))
    }

    @Test
    fun anEmptySafeSlugFallsBackToQueue() {
        assertEquals("queue", QueueExport.slug(""))
        assertEquals("queue", QueueExport.slug("!!!"))
        assertEquals("queue", QueueExport.slug("日本語"))
    }

    @Test
    fun theFilenameKeepsTheUuidSuffixStable() {
        val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        assertEquals("abbey_road_$id.m3u8", QueueExport.fileName("Abbey Road", id))
        assertEquals("queue_$id.m3u8", QueueExport.fileName("", id))
    }

    @Test
    fun contentStartsWithExtM3uAndKeepsRowOrder() {
        val content = QueueExport.content(
            listOf(
                QueueExportRow("/music/b.flac", "Second", "Artist", 125_000L),
                QueueExportRow("/music/a.mp3", "First", "Other", 60_000L),
            ),
        ).toString(Charsets.UTF_8)

        assertEquals(
            "#EXTM3U\r\n" +
                "#EXTINF:125,Artist - Second\r\n/music/b.flac\r\n" +
                "#EXTINF:60,Other - First\r\n/music/a.mp3\r\n",
            content,
        )
    }

    @Test
    fun anEmptyQueueStillProducesAValidHeader() {
        assertEquals("#EXTM3U\r\n", QueueExport.content(emptyList()).toString(Charsets.UTF_8))
    }

    @Test
    fun missingMetadataFallsBackWithoutBreakingTheLine() {
        val content = QueueExport.content(
            listOf(
                QueueExportRow("/music/no-tags.flac"),
                QueueExportRow("/music/title-only.flac", title = "Title"),
                QueueExportRow("/music/artist-only.flac", artist = "Artist"),
            ),
        ).toString(Charsets.UTF_8)

        assertTrue(content.contains("#EXTINF:0,no-tags\r\n/music/no-tags.flac\r\n"))
        assertTrue(content.contains("#EXTINF:0,Title\r\n/music/title-only.flac\r\n"))
        assertTrue(content.contains("#EXTINF:0,Artist\r\n/music/artist-only.flac\r\n"))
    }
}
