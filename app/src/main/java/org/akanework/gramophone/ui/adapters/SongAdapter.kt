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

package org.akanework.gramophone.ui.adapters

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.queue.QueuePlaybackRules
import org.akanework.gramophone.logic.requireMediaStoreId
import org.akanework.gramophone.logic.setMediaItemsSeamlessly
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.MediaRowCodec
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.NowPlayingPresentation
import org.akanework.gramophone.ui.NowPlayingState
import org.akanework.gramophone.ui.NowPlayingViewBinder
import org.akanework.gramophone.ui.SongPickerActivity
import org.akanework.gramophone.ui.components.AddToQueueSheet
import org.akanework.gramophone.ui.fragments.ArtistSubFragment
import org.akanework.gramophone.ui.fragments.DetailDialogFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import org.akanework.gramophone.ui.fragments.SearchFragment
import uk.akane.libphonograph.items.addDate
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.items.modifiedDate
import uk.akane.libphonograph.manipulator.ItemManipulator
import java.io.File
import java.util.GregorianCalendar

/**
 * [SongAdapter] is an adapter for displaying songs.
 */
class SongAdapter(
    val fragment: Fragment?,
    val queueTitle: Flow<String>?,
    songList: Flow<List<MediaItem>?> = (fragment?.requireContext() ?: fallbackContext!!)
        .gramophoneApplication.reader.songListFlow,
    helper: Sorter.NaturalOrderHelper<MediaItem>? = null,
    isSubFragment: Int? = null,
    allowDiffUtils: Boolean = false,
    rawOrderExposed: Sorter.Type? = if (isSubFragment == null) Sorter.Type.ByTitleAscending else null,
    val folder: Boolean = false,
    fallbackContext: AppCompatActivity? = null,
    val queueSource: Flow<org.akanework.gramophone.logic.queue.QueueSource?>? = null,
) : BaseAdapter<MediaItem>
    (
    fragment,
    liveData = songList,
    sortHelper = MediaItemHelper,
    naturalOrderHelper = helper,
    initialSortType =
        (if (helper != null) Sorter.Type.NaturalOrder else (if (folder) Sorter.Type
            .ByFilePathAscending else rawOrderExposed ?: Sorter.Type.ByTitleAscending)),
    canSort = true,
    pluralStr = R.plurals.songs,
    defaultLayoutType = LayoutType.COMPACT_LIST,
    isSubFragment = isSubFragment,
    rawOrderExposed = rawOrderExposed,
    allowDiffUtils = allowDiffUtils,
    hasMenu = isSubFragment != R.id.songs,
    fallbackContext = fallbackContext
), SharedPreferences.OnSharedPreferenceChangeListener {

    init {
        lateInit()
    }

    fun getSongList() = list?.second ?: emptyList()

    fun getActivity() = mainActivity

    private var showFileNames = false
    private var idToPosMap: HashMap<String, List<Int?>>? = null
    private var currentMediaItem: String? = null
        set(value) {
            if (field != value) {
                val oldValue = field
                field = value
                if (idToPosMap != null) {
                    val oldPos = idToPosMap!![oldValue]
                    val newPos = idToPosMap!![value]
                    oldPos?.forEach {
                        it?.let {
                            notifyItemChanged(it, true)
                        }
                    }
                    newPos?.forEach {
                        it?.let {
                            notifyItemChanged(it, true)
                        }
                    }
                }
            }
        }
    private var currentIsPlaying: Boolean? = null
        set(value) {
            if (field != value) {
                field = value
                if (value != null && currentMediaItem != null) {
                    idToPosMap?.get(currentMediaItem)?.forEach {
                        it?.let {
                            notifyItemChanged(it, false)
                        }
                    }
                }
            }
        }

    init {
        if (fragment != null) {
            val mediaControllerViewModel: MediaControllerViewModel by fragment.activityViewModels()
            mediaControllerViewModel.addRecreationalPlayerListener(
                fragment.viewLifecycleOwner.lifecycle,
                object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentMediaItem = mediaItem?.mediaId
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        val controller = mediaControllerViewModel.get()!!
                        currentIsPlaying = NowPlayingPresentation.isEffectivelyPlaying(
                            playWhenReady,
                            controller.playbackState,
                        )
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val controller = mediaControllerViewModel.get()!!
                        currentIsPlaying = NowPlayingPresentation.isEffectivelyPlaying(
                            controller.playWhenReady,
                            playbackState,
                        )
                    }
                }
            ) {
                currentMediaItem = it.currentMediaItem?.mediaId
                currentIsPlaying = NowPlayingPresentation.isEffectivelyPlaying(
                    it.playWhenReady,
                    it.playbackState,
                )
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (folder) {
            prefs.registerOnSharedPreferenceChangeListener(this)
            showFileNames = prefs.getBooleanStrict("show_file_names", true)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (folder)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if ((key == null || key == "show_file_names") && folder) {
            showFileNames = this.prefs.getBooleanStrict("show_file_names", true)
            notifyDataSetChanged()
        }
    }

    fun getPlayingSong(): Int? {
        return if (currentMediaItem != null) {
            idToPosMap?.get(currentMediaItem)?.firstOrNull()
        } else null
    }

    override fun onListUpdated() {
        // TODO run this method on a different thread / in advance
        idToPosMap = hashMapOf()
        list!!.second.forEachIndexed { i, item ->
            idToPosMap!![item.mediaId] = (idToPosMap!![item.mediaId] ?: emptyList()).plus(listOf(i))
        }
    }

    override fun virtualTitleOf(item: MediaItem): String {
        return "null"
    }

    override fun titleOf(item: MediaItem): String? {
        return if (showFileNames) item.getFile()?.name else super.titleOf(item)
    }

    override fun onClick(item: MediaItem, position: Int) {
        if (isSubFragment == R.id.songs) {
            (context as SongPickerActivity).onSelected(item)
            return
        }
        val mediaController = mainActivity.getPlayer() ?: return
        val songList = getSongList()
        val currentItem = mediaController.currentMediaItem
        CoroutineScope(Dispatchers.Main).launch {
            val title = queueTitle?.first() ?: ""
            val source = queueSource?.first()
            mediaController.setMediaItemsSeamlessly(songList, position, title, source = source)
            mediaController.prepare()
            mediaController.play()
            if (currentItem?.mediaId == songList[position].mediaId && fragment !is SearchFragment) {
                mainActivity.playerBottomSheet.open()
            }
        }
    }

    override fun onMenu(item: MediaItem, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu)
        val menuContext = org.akanework.gramophone.ui.SongMenuContext.forLibrary(mainActivity)
        org.akanework.gramophone.ui.SongMenuLogic.configure(popupMenu, item, menuContext)

        popupMenu.setOnMenuItemClickListener { it1 ->
            org.akanework.gramophone.ui.SongMenuLogic.handle(
                mainActivity,
                item,
                it1.itemId,
                menuContext,
            )
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        }
        val isCurrent = currentMediaItem != null &&
            getSongList().getOrNull(position)?.mediaId == currentMediaItem
        NowPlayingViewBinder.apply(
            holder,
            NowPlayingState(isCurrent = isCurrent, isPlaying = currentIsPlaying == true),
        )
    }

    object MediaItemHelper : Sorter.Helper<MediaItem>(
        setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.ByArtistDescending, Sorter.Type.ByArtistAscending,
            Sorter.Type.ByAlbumTitleDescending, Sorter.Type.ByAlbumTitleAscending,
            Sorter.Type.ByAlbumArtistDescending, Sorter.Type.ByAlbumArtistAscending,
            Sorter.Type.ByAlbumArtistYearDescending, Sorter.Type.ByAlbumArtistYearAscending,
            Sorter.Type.ByAlbumYearDescending, Sorter.Type.ByAlbumYearAscending,
            Sorter.Type.ByAddDateDescending, Sorter.Type.ByAddDateAscending,
            Sorter.Type.ByReleaseDateDescending, Sorter.Type.ByReleaseDateAscending,
            Sorter.Type.ByModifiedDateDescending, Sorter.Type.ByModifiedDateAscending,
            Sorter.Type.ByFilePathDescending, Sorter.Type.ByFilePathAscending,
            Sorter.Type.ByDiscAndTrack
        )
    ) {
        override fun getId(item: MediaItem): String {
            return item.mediaId
        }

        override fun getFile(item: MediaItem): File {
            return item.getFile()!!
        }

        override fun getTitle(item: MediaItem): String {
            return item.mediaMetadata.title.toString()
        }

        override fun getArtist(item: MediaItem): String? {
            return item.mediaMetadata.artist?.toString()
        }

        override fun getAlbumTitle(item: MediaItem): String {
            return item.mediaMetadata.albumTitle?.toString() ?: ""
        }

        override fun getAlbumArtist(item: MediaItem): String {
            return item.mediaMetadata.albumArtist?.toString() ?: ""
        }

        override fun getAlbumYear(item: MediaItem): Int? {
            return item.mediaMetadata.releaseYear
        }

        override fun getCover(item: MediaItem): Uri? {
            return item.mediaMetadata.artworkUri
        }

        override fun getDiscAndTrack(item: MediaItem): Int {
            return (item.mediaMetadata.discNumber ?: 0) * 1000 + (item.mediaMetadata.trackNumber
                ?: 0)
        }

        override fun getAddDate(item: MediaItem): Long {
            return item.mediaMetadata.addDate ?: -1
        }

        override fun getReleaseDate(item: MediaItem): Long {
            if (item.mediaMetadata.releaseYear == null && item.mediaMetadata.releaseMonth == null
                && item.mediaMetadata.releaseDay == null
            ) {
                return GregorianCalendar(
                    item.mediaMetadata.recordingYear ?: 0,
                    (item.mediaMetadata.recordingMonth ?: 1) - 1,
                    item.mediaMetadata.recordingDay ?: 0, 0, 0, 0
                )
                    .timeInMillis
            }
            return GregorianCalendar(
                item.mediaMetadata.releaseYear ?: 0,
                (item.mediaMetadata.releaseMonth ?: 1) - 1,
                item.mediaMetadata.releaseDay ?: 0, 0, 0, 0
            )
                .timeInMillis
        }

        override fun getModifiedDate(item: MediaItem): Long {
            return item.mediaMetadata.modifiedDate ?: -1
        }
    }
}
