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

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaRowCodecTest {
    @Test
    fun roundTripPreservesTheQueueRow() {
        val item = MediaItem.Builder()
            .setMediaId("MediaStore:42")
            .setUri("content://media/external/audio/media/42")
            .setMimeType("audio/mpeg")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Song: with a colon")
                    .setArtist("Artist")
                    .setAlbumTitle("Album")
                    .setDurationMs(123_000L)
                    .setTrackNumber(3)
                    .build()
            )
            .build()

        val decoded = MediaRowCodec.decode(MediaRowCodec.encode(item))!!

        assertEquals(item.mediaId, decoded.mediaId)
        assertEquals(item.localConfiguration?.uri, decoded.localConfiguration?.uri)
        assertEquals(item.localConfiguration?.mimeType, decoded.localConfiguration?.mimeType)
        assertEquals(item.mediaMetadata.title, decoded.mediaMetadata.title)
        assertEquals(item.mediaMetadata.artist, decoded.mediaMetadata.artist)
        assertEquals(item.mediaMetadata.albumTitle, decoded.mediaMetadata.albumTitle)
        assertEquals(item.mediaMetadata.durationMs, decoded.mediaMetadata.durationMs)
        assertEquals(item.mediaMetadata.trackNumber, decoded.mediaMetadata.trackNumber)
    }

    @Test
    fun malformedRowDecodesToNull() {
        assertNull(MediaRowCodec.decode("ver_3"))
    }
}
