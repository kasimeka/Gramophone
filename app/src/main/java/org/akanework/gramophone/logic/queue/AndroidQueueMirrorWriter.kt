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

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.utils.MediaRowCodec
import java.io.File
import java.io.IOException
import java.util.UUID

/** Decode persisted rows to resolvable absolute-path rows, dropping unresolvable rows. */
fun queueExportRows(queue: Queue): List<QueueExportRow> =
    queue.rows.mapNotNull { MediaRowCodec.decode(it) }
        .mapNotNull { item ->
            val path = item.getFile()?.absolutePath ?: return@mapNotNull null
            QueueExportRow(
                path = path,
                title = item.mediaMetadata.title?.toString(),
                artist = item.mediaMetadata.artist?.toString(),
                durationMs = item.mediaMetadata.durationMs,
            )
        }

/**
 * Writes queue mirrors under `Android/media/<applicationId>/Queues/`. MediaStore owns the file on
 * API 29+, so it is indexed and readable by other players; below 29 the legacy file plus media
 * scanner path is used. The scanner's folder filter excludes `Android/media` from the library.
 */
class AndroidQueueMirrorWriter(private val context: Context) : QueueMirrorWriter {
    private val relativePath = "Android/media/${context.packageName}/Queues"
    private val legacyDirectory = File(Environment.getExternalStorageDirectory(), relativePath)

    override fun write(fileName: String, content: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeMediaStore(fileName, content)
        } else {
            writeLegacy(fileName, content)
        }
    }

    override fun deleteOtherFiles(id: UUID, keep: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deleteMediaStore(id, keep)
        } else {
            deleteLegacy(id, keep)
        }
    }

    override fun lastModified(fileName: String): Long? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = findMediaStoreUri(fileName) ?: return null
            return context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) null
                else cursor.getLong(0) * 1000L
            }
        }
        val file = File(legacyDirectory, fileName)
        return if (file.exists()) file.lastModified() else null
    }

    private fun writeLegacy(fileName: String, content: ByteArray) {
        legacyDirectory.mkdirs()
        val file = File(legacyDirectory, fileName)
        file.writeBytes(content)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(MIME_TYPE),
            null,
        )
    }

    private fun deleteLegacy(id: UUID, keep: String?) {
        val suffix = suffix(id)
        legacyDirectory.listFiles()?.forEach { file ->
            if (file.name.endsWith(suffix) && file.name != keep) {
                if (file.delete()) {
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                }
            }
        }
    }

    private fun writeMediaStore(fileName: String, content: ByteArray) {
        val resolver = context.contentResolver
        val existing = findMediaStoreUri(fileName)
        val uri = existing ?: resolver.insert(
            collection(),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: throw IOException("could not create queue mirror $fileName")
        try {
            resolver.openOutputStream(uri, "wt")?.use { it.write(content) }
                ?: throw IOException("could not open queue mirror $fileName")
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
                },
                null,
                null,
            )
        } catch (t: Throwable) {
            if (existing == null) resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun deleteMediaStore(id: UUID, keep: String?) {
        val suffix = suffix(id)
        val resolver = context.contentResolver
        resolver.query(
            collection(),
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(relativePath, "$relativePath/"),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                if (!name.endsWith(suffix) || name == keep) continue
                resolver.delete(
                    ContentUris.withAppendedId(collection(), cursor.getLong(idColumn)),
                    null,
                    null,
                )
            }
        }
    }

    private fun findMediaStoreUri(fileName: String): Uri? {
        context.contentResolver.query(
            collection(),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "(${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?)",
            arrayOf(fileName, relativePath, "$relativePath/"),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(collection(), cursor.getLong(0))
            }
        }
        return null
    }

    private fun collection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

    private fun suffix(id: UUID): String = "_$id.m3u8"

    private companion object {
        const val MIME_TYPE = "audio/x-mpegurl"
    }
}
