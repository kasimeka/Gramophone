/*
 *     Copyright (C) 2025 nift4
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

package uk.akane.libphonograph.manipulator

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.media3.common.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.logic.queryWithPending
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.MainActivity
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.getIntOrNullIfThrow
import uk.akane.libphonograph.getLongOrNullIfThrow
import java.io.File

object ItemManipulator {
    private const val TAG = "ItemManipulator"

    suspend fun deleteSongs(context: MainActivity, list: List<Pair<File, Long>>): (() -> Unit)? {
        val uris = list.flatMap {
            val id = it.second
            val file = it.first
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.getContentUri("external"), id
            )
            // TODO maybe don't hardcode these extensions twice, here and in LrcUtils?
            val extensions = setOf("ttml", "lrc", "srt")
            if (!Flags.MEDIASTORE_IO) {
                extensions.asSequence().map {
                    file.resolveSibling("${file.nameWithoutExtension}.$it")
                }.filter { it.exists() }.map {
                    // It doesn't really make sense to have >1 subtitle file so we don't need to batch the queries.
                    getIdForPath(context, it)
                }.filter { it != null }
                    .map {
                        ContentUris.withAppendedId(
                            MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
                            it!!
                        )
                    }
                    .toList() + uri
            } else {
                val urisToDelete = mutableSetOf(uri)
                context.contentResolver.queryWithPending(
                    MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Files.FileColumns._ID),
                    "${MediaStore.Files.FileColumns.DATA} IN (" + extensions.joinToString { "?" } + ")",
                    extensions.map {
                        file.resolveSibling(file.nameWithoutExtension + ".$it").path
                    }.toTypedArray(),
                    null
                ).use {
                    if (it != null && it.moveToFirst()) {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        do {
                            urisToDelete.add(
                                ContentUris.withAppendedId(
                                    MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
                                    it.getLong(idColumn)
                                )
                            )
                        } while (it.moveToNext())
                    }
                }
                urisToDelete
            }
        }
        return delete(context, uris)
    }

    private suspend fun delete(context: MainActivity, uris: Collection<Uri>): (() -> Unit)? {
        if (uris.find { MediaStoreCompat.needRequestDelete(context, it) != null } != null) {
            val pendingIntent = MediaStoreCompat.createDeleteRequest(context, uris.toList())
            val req = Bundle().apply {
                putString(
                    "UiError", context.getString(
                        androidx.media3.session.R.string.error_message_info_cancelled
                    )
                )
            }
            withContext(Dispatchers.Main) {
                context.runIntentForDelete(pendingIntent.intentSender, req)
            }
            return null
        } else {
            return {
                CoroutineScope(Dispatchers.IO).launch {
                    val urisWithStatus = uris.map {
                        try {
                            MediaStoreCompat.delete(context, it)
                            it to (null)
                        } catch (e: SecurityException) {
                            Log.e("ItemManipulator", "failed to delete $it", e)
                            it to e
                        }
                    }
                    val notOk = urisWithStatus.filter { it.second != null }
                    val ok = notOk.isEmpty()
                    if (!ok) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.delete_failed, notOk.first().toString()),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    suspend fun continueDeleteFromPendingIntent(
        context: Context,
        resultCode: Int,
        data: Intent?,
        req: Bundle
    ) {
        // this is the callback of createDeleteRequest(), and the delete was already done if
        // resultCode is RESULT_OK. if it's not, then we just show a toast or something.
        if (resultCode == Activity.RESULT_OK || resultCode == Activity.RESULT_CANCELED) return
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context, context.getString(
                    R.string.delete_failed,
                    data?.getStringExtra("ErrorMsg") ?: req.getString("UiError")
                ), Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getIdForPath(context: Context, file: File): Long? {
        val cursor = context.contentResolver.query(
            MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
            if (hasImprovedMediaStore())
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            else arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.Files.FileColumns.DATA} = ?", arrayOf(file.absolutePath), null
        )
        if (cursor == null) return null
        cursor.use {
            if (!cursor.moveToFirst()) return null
            if (hasImprovedMediaStore()) {
                val typeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val type = cursor.getIntOrNullIfThrow(typeColumn)
                if (type != MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE) {
                    Log.e(TAG, "expected $file to be a subtitle")
                    return null
                }
            }
            val idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            return cursor.getLongOrNullIfThrow(idColumn)
        }
    }
}
