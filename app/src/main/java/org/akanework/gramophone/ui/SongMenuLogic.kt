/*
 *     Copyright (C) 2024 Akane Foundation
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

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ShareCompat
import androidx.media3.common.MediaItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.queue.QueuePlaybackRules
import org.akanework.gramophone.logic.utils.MediaRowCodec
import org.akanework.gramophone.ui.components.AddToQueueSheet
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.ArtistSubFragment
import org.akanework.gramophone.ui.fragments.DetailDialogFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.manipulator.ItemManipulator

/** Context for the regular song overflow menu (Songs tab, queue detail rows, etc.). */
data class SongMenuContext(
    val canPlayNext: Boolean,
    val showRemoveFromQueue: Boolean,
) {
    companion object {
        fun forLibrary(activity: MainActivity): SongMenuContext = SongMenuContext(
            canPlayNext = QueuePlaybackRules.libraryPlayNextAvailable(
                activity.gramophoneApplication.queueRepository.lastActiveQueue.value
            ),
            showRemoveFromQueue = false,
        )

        fun forQueue(canPlayNext: Boolean): SongMenuContext = SongMenuContext(
            canPlayNext = canPlayNext,
            showRemoveFromQueue = true,
        )
    }
}

object SongMenuLogic {
    fun canGoToAlbum(item: MediaItem?): Boolean = item?.mediaMetadata?.albumId != null
    fun canGoToArtist(item: MediaItem?): Boolean = item?.mediaMetadata?.artistId != null
    fun canDelete(item: MediaItem?): Boolean =
        item?.getFile() != null && item.mediaId.toLongOrNull() != null
    fun canShare(item: MediaItem?): Boolean =
        item?.requestMetadata?.mediaUri != null || item?.localConfiguration?.uri != null

    fun configure(popupMenu: PopupMenu, item: MediaItem?, menuContext: SongMenuContext) {
        val menu = popupMenu.menu
        menu.findItem(R.id.play_next)?.apply {
            isVisible = true
            isEnabled = menuContext.canPlayNext
        }
        menu.findItem(R.id.add_to_queue)?.isVisible = true
        menu.findItem(R.id.album)?.apply {
            isVisible = true
            isEnabled = canGoToAlbum(item)
        }
        menu.findItem(R.id.artist)?.apply {
            isVisible = true
            isEnabled = canGoToArtist(item)
        }
        menu.findItem(R.id.details)?.apply {
            isVisible = true
            isEnabled = item != null
        }
        menu.findItem(R.id.delete)?.apply {
            isVisible = true
            isEnabled = canDelete(item)
        }
        menu.findItem(R.id.share)?.apply {
            isVisible = true
            isEnabled = canShare(item)
        }
        menu.findItem(R.id.remove_from_queue)?.isVisible = menuContext.showRemoveFromQueue
    }

    /**
     * Handle standard song menu actions. Returns true if handled.
     * [onRemoveFromQueue] is invoked only when remove-from-queue is selected and allowed.
     */
    fun handle(
        activity: MainActivity,
        item: MediaItem,
        itemId: Int,
        menuContext: SongMenuContext,
        onRemoveFromQueue: (() -> Unit)? = null,
    ): Boolean = when (itemId) {
        R.id.play_next -> {
            if (!menuContext.canPlayNext) return true
            val mediaController = activity.getPlayer()
            mediaController?.addMediaItem(
                mediaController.currentMediaItemIndex + 1,
                item,
            )
            true
        }

        R.id.add_to_queue -> {
            AddToQueueSheet(activity, listOf(MediaRowCodec.encode(item))).show()
            true
        }

        R.id.album -> {
            item.mediaMetadata.albumId?.let { albumId ->
                activity.startFragment(GeneralSubFragment()) {
                    putString("Id", albumId.toString())
                    putInt("Item", R.id.album)
                }
            }
            true
        }

        R.id.artist -> {
            item.mediaMetadata.artistId?.let { artistId ->
                activity.startFragment(ArtistSubFragment()) {
                    putString("Id", artistId.toString())
                    putInt("Item", R.id.artist)
                }
            }
            true
        }

        R.id.details -> {
            activity.startFragment(DetailDialogFragment()) {
                putString("Id", item.mediaId)
            }
            true
        }

        R.id.delete -> {
            confirmDelete(activity, item)
            true
        }

        R.id.share -> {
            share(activity, item)
            true
        }

        R.id.remove_from_queue -> {
            if (menuContext.showRemoveFromQueue) onRemoveFromQueue?.invoke()
            true
        }

        else -> false
    }

    fun confirmDelete(activity: MainActivity, item: MediaItem) {
        val file = item.getFile() ?: return
        val mediaStoreId = item.mediaId.toLongOrNull() ?: return
        CoroutineScope(Dispatchers.Default).launch {
            val res = ItemManipulator.deleteSongs(activity, listOf(file to mediaStoreId))
            if (res != null) {
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.delete)
                        .setMessage(
                            activity.getString(
                                R.string.delete_really,
                                item.mediaMetadata.title ?: activity.getString(R.string.unknown_title),
                            ),
                        )
                        .setPositiveButton(R.string.delete) { _, _ -> res.invoke() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    fun share(activity: MainActivity, item: MediaItem) {
        val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri ?: return
        val mimeType = item.localConfiguration?.mimeType ?: "audio/*"
        try {
            ShareCompat.IntentBuilder(activity)
                .setType(mimeType)
                .setStream(uri)
                .setChooserTitle("Share audio file")
                .startChooser()
        } catch (e: Exception) {
            Toast.makeText(
                activity,
                "Unable to share: ${e.message}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
