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

import android.annotation.SuppressLint
import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.akanework.gramophone.logic.getFile
import uk.akane.libphonograph.items.EXTRA_ADD_DATE
import uk.akane.libphonograph.items.EXTRA_ALBUM_ID
import uk.akane.libphonograph.items.EXTRA_ARTIST_ID
import uk.akane.libphonograph.items.EXTRA_CD_TRACK_NUMBER
import uk.akane.libphonograph.items.EXTRA_FILE
import uk.akane.libphonograph.items.EXTRA_HD_ARTWORK_URI
import uk.akane.libphonograph.items.EXTRA_MODIFIED_DATE
import uk.akane.libphonograph.items.addDate
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.items.cdTrackNumber
import uk.akane.libphonograph.items.hdArtworkUri
import uk.akane.libphonograph.items.modifiedDate
import java.nio.charset.StandardCharsets

/**
 * The persisted media-row payload. It is a string per row, so a queue survives a missing or
 * changed MediaStore entry and can be rebuilt into [MediaItem]s without an ORM.
 *
 * The wire format is the same versioned, delimiter-safe payload the old resume state used; the
 * queue store replaced that preference adapter but not the row format.
 */
object MediaRowCodec {
    private const val VERSION = 3
    private const val VERSION_PREFIX = "ver_"

    fun encode(item: MediaItem): String {
        val b = SafeDelimitedStringConcat(":")
        // Add new entries at the bottom and remember they are null for the upgrade path.
        b.writeStringUnsafe(VERSION_PREFIX + VERSION)
        b.writeStringSafe(item.mediaId)
        b.writeUri(item.localConfiguration?.uri)
        b.writeStringSafe(item.localConfiguration?.mimeType)
        b.writeStringSafe(item.mediaMetadata.title)
        b.writeStringSafe(item.mediaMetadata.artist)
        b.writeStringSafe(item.mediaMetadata.albumTitle)
        b.writeStringSafe(item.mediaMetadata.albumArtist)
        b.writeUri(item.mediaMetadata.artworkUri)
        b.writeInt(item.mediaMetadata.trackNumber)
        b.writeInt(item.mediaMetadata.discNumber)
        b.writeInt(item.mediaMetadata.recordingYear)
        b.writeInt(item.mediaMetadata.releaseYear)
        b.writeBool(item.mediaMetadata.isBrowsable)
        b.writeBool(item.mediaMetadata.isPlayable)
        b.writeLong(item.mediaMetadata.addDate)
        b.writeStringSafe(item.mediaMetadata.writer)
        b.writeStringSafe(item.mediaMetadata.compilation)
        b.writeStringSafe(item.mediaMetadata.composer)
        b.writeStringSafe(item.mediaMetadata.genre)
        b.writeInt(item.mediaMetadata.recordingDay)
        b.writeInt(item.mediaMetadata.recordingMonth)
        b.writeLong(item.mediaMetadata.artistId)
        b.writeLong(item.mediaMetadata.albumId)
        b.writeStringSafe(item.mediaMetadata.author)
        b.writeLong(item.mediaMetadata.durationMs)
        b.writeLong(item.mediaMetadata.modifiedDate)
        b.writeStringSafe(item.mediaMetadata.cdTrackNumber)
        b.writeUri(item.mediaMetadata.hdArtworkUri)
        b.writeStringSafe(item.getFile()?.path)
        return b.toString()
    }

    fun decode(row: String): MediaItem? = try {
        val b = SafeDelimitedStringDecat(":", row)
        val versionStr = b.readStringUnsafe()
        val version = versionStr?.let {
            if (it.startsWith(VERSION_PREFIX)) it.substring(VERSION_PREFIX.length).toInt() else 0
        } ?: 0
        val mediaId = if (version == 0) {
            "MediaStore:$versionStr" // used to be mediaId
        } else {
            b.readStringSafe()
        }
        var uri = b.readUri()
        if (version == 0 && uri?.toString()?.let {
                it.startsWith("/storage/") || it.startsWith("/mnt/") ||
                        (@SuppressLint("SdCardPath") it.startsWith("/sdcard/"))
            } == true
        ) {
            uri = uri.buildUpon().scheme("file").build()
        }
        val mimeType = b.readStringSafe()
        val title = b.readStringSafe()
        val artist = b.readStringSafe()
        val album = b.readStringSafe()
        val albumArtist = b.readStringSafe()
        val imgUri = b.readUri()
        val trackNumber = b.readInt()
        val discNumber = b.readInt()
        val recordingYear = b.readInt()
        val releaseYear = b.readInt()
        val isBrowsable = b.readBool()
        val isPlayable = b.readBool()
        val addDate = b.readLong()
        val writer = b.readStringSafe()
        val compilation = b.readStringSafe()
        val composer = b.readStringSafe()
        val genre = b.readStringSafe()
        val recordingDay = b.readInt()
        val recordingMonth = b.readInt()
        val artistId = b.readLong()
        val albumId = b.readLong()
        if (version < 1) b.skip() // used to be GenreId
        val author = b.readStringSafe()
        if (version < 1) b.skip() // used to be CdTrackNumber
        val duration = b.readLong()
        if (version < 1) b.skip() // used to be Path
        val modifiedDate = b.readLong()
        val cdTrackNumber = b.readStringSafe()
        if (version < 3) b.skip() // used to be AlbumYear
        val hdArtworkUri = b.readUri()
        val file = if (version >= 2) {
            b.readStringSafe()
        } else {
            uri.let {
                uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId!!.substring("MediaStore:".length).toLong(),
                )
                it!!.toFile().path
            }
        }
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(mediaId!!)
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setWriter(writer)
                    .setAuthor(author)
                    .setComposer(composer)
                    .setGenre(genre)
                    .setCompilation(compilation)
                    .setRecordingDay(recordingDay)
                    .setRecordingMonth(recordingMonth)
                    .setAlbumTitle(album)
                    .setAlbumArtist(albumArtist)
                    .setArtworkUri(imgUri)
                    .setTrackNumber(trackNumber)
                    .setDiscNumber(discNumber)
                    .setRecordingYear(recordingYear)
                    .setReleaseYear(releaseYear)
                    .setDurationMs(duration)
                    .setIsBrowsable(isBrowsable)
                    .setIsPlayable(isPlayable)
                    .setExtras(
                        Bundle().apply {
                            if (addDate != null) putLong(EXTRA_ADD_DATE, addDate)
                            if (artistId != null) putLong(EXTRA_ARTIST_ID, artistId)
                            if (albumId != null) putLong(EXTRA_ALBUM_ID, albumId)
                            putString(EXTRA_CD_TRACK_NUMBER, cdTrackNumber)
                            if (modifiedDate != null) putLong(EXTRA_MODIFIED_DATE, modifiedDate)
                            if (hdArtworkUri != null) {
                                putParcelable(EXTRA_HD_ARTWORK_URI, hdArtworkUri)
                            }
                            if (file != null) putString(EXTRA_FILE, file)
                        }
                    )
                    .build()
            )
            .build()
    } catch (_: Exception) {
        null
    }
}

private class SafeDelimitedStringConcat(private val delimiter: String) {
    private val b = StringBuilder()
    private var hadFirst = false

    private fun append(s: String?) {
        if (s?.contains(delimiter, false) == true) {
            throw IllegalArgumentException("argument must not contain delimiter")
        }
        if (hadFirst) {
            b.append(delimiter)
        } else {
            hadFirst = true
        }
        s?.let { b.append(it) }
    }

    override fun toString(): String = b.toString()

    fun writeStringUnsafe(s: CharSequence?) = append(s?.toString())
    fun writeStringSafe(s: CharSequence?) =
        writeBase64(s?.toString()?.toByteArray(StandardCharsets.UTF_8))

    fun writeInt(i: Int?) = append(i?.toString())
    fun writeLong(i: Long?) = append(i?.toString())
    fun writeBool(b: Boolean?) = append(b?.toString())
    fun writeUri(u: Uri?) = writeStringSafe(u?.toString())
    fun skip() = append(null)

    private fun writeBase64(b: ByteArray?) =
        append(b?.let { Base64.encodeToString(it, Base64.DEFAULT) })
}

private class SafeDelimitedStringDecat(delimiter: String, str: String) {
    private val items = str.split(delimiter)
    private var pos = 0

    private fun read(): String? {
        if (pos == items.size) return null
        return items[pos++].ifEmpty { null }
    }

    fun readStringUnsafe(): String? = read()
    fun readStringSafe(): String? =
        read()?.let { Base64.decode(it, Base64.DEFAULT) }?.toString(StandardCharsets.UTF_8)

    fun readInt(): Int? = read()?.toInt()
    fun readLong(): Long? = read()?.toLong()
    fun readBool(): Boolean? = read()?.toBooleanStrict()
    fun readUri(): Uri? = readStringSafe()?.toUri()
    fun skip() {
        read()
    }
}
