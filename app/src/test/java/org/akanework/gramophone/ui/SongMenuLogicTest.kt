/*
 *     Copyright (C) 2026 Akane Foundation
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

package org.akanework.gramophone.ui

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.mediaStoreId
import org.akanework.gramophone.logic.utils.MediaRowCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.akane.libphonograph.items.EXTRA_FILE

@RunWith(RobolectricTestRunner::class)
class SongMenuLogicTest {

    @Test
    fun canDelete_withMediaStorePrefixedId_returnsTrue() {
        val item = MediaItem.Builder()
            .setMediaId("MediaStore:12345")
            .setUri("content://media/external/audio/media/12345")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Sample Song")
                    .setExtras(Bundle().apply { putString(EXTRA_FILE, "/storage/emulated/0/Music/song.mp3") })
                    .build()
            )
            .build()

        assertTrue(SongMenuLogic.canDelete(item))
        assertEquals(12345L, item.mediaStoreId())
        assertEquals("/storage/emulated/0/Music/song.mp3", item.getFile()?.path)
    }

    @Test
    fun canDelete_withMediaRowCodecDecodedItem_returnsTrue() {
        val original = MediaItem.Builder()
            .setMediaId("MediaStore:9876")
            .setUri("content://media/external/audio/media/9876")
            .setMimeType("audio/mpeg")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Queue Song")
                    .setArtist("Queue Artist")
                    .setExtras(Bundle().apply { putString(EXTRA_FILE, "/sdcard/Music/test.mp3") })
                    .build()
            )
            .build()

        val encoded = MediaRowCodec.encode(original)
        val decoded = MediaRowCodec.decode(encoded)

        assertNotNull(decoded)
        assertTrue(SongMenuLogic.canDelete(decoded))
        assertEquals(9876L, decoded?.mediaStoreId())
    }

    @Test
    fun canDelete_withContentUriWithoutExtras_returnsTrue() {
        val item = MediaItem.Builder()
            .setMediaId("content://media/external/audio/media/555")
            .setUri("content://media/external/audio/media/555")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("No Extra").build())
            .build()

        assertTrue(SongMenuLogic.canDelete(item))
        assertEquals(555L, item.mediaStoreId())
    }

    @Test
    fun canDelete_withRawNumericMediaId_returnsTrue() {
        val item = MediaItem.Builder()
            .setMediaId("777")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Numeric Id").build())
            .build()

        assertTrue(SongMenuLogic.canDelete(item))
        assertEquals(777L, item.mediaStoreId())
    }

    @Test
    fun canDelete_withFileUriItem_returnsTrue() {
        val item = MediaItem.Builder()
            .setMediaId("file_song")
            .setUri("file:///storage/emulated/0/Music/local.flac")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Local File").build())
            .build()

        assertTrue(SongMenuLogic.canDelete(item))
        assertEquals("/storage/emulated/0/Music/local.flac", item.getFile()?.path)
    }

    @Test
    fun canDelete_withNullItem_returnsFalse() {
        assertFalse(SongMenuLogic.canDelete(null))
    }

    @Test
    fun mediaStoreId_resolvesFromDifferentFormats() {
        val mediaStorePrefixed = MediaItem.Builder().setMediaId("MediaStore:101").build()
        val numericId = MediaItem.Builder().setMediaId("202").build()
        val contentUriItem = MediaItem.Builder()
            .setMediaId("custom")
            .setUri("content://media/external/audio/media/303")
            .build()
        val nonMediaStoreItem = MediaItem.Builder()
            .setMediaId("custom")
            .setUri("https://example.com/audio.mp3")
            .build()

        assertEquals(101L, mediaStorePrefixed.mediaStoreId())
        assertEquals(202L, numericId.mediaStoreId())
        assertEquals(303L, contentUriItem.mediaStoreId())
        assertNull(nonMediaStoreItem.mediaStoreId())
    }
}
