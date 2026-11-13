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

package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.setExpandedAppBarHeight
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.logic.utils.flows.provideReplayCacheInvalidationManager
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.adapters.Sorter
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded

/**
 * GeneralSubFragment:
 *   Inherited from [BaseFragment]. Sub fragment of all
 * possible item types. TODO: Artist / AlbumArtist
 *
 * @see BaseFragment
 * @author AkaneTan, nift4
 */
class GeneralSubFragment : BaseFragment() {
    companion object {
        private const val TAG = "GeneralSubFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        lateinit var itemList: Flow<List<MediaItem>?>

        val rootView = inflater.inflate(R.layout.fragment_general_sub, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val collapsingToolbarLayout =
            rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
        collapsingToolbarLayout.setExpandedAppBarHeight()
        val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()

        val bundle = requireArguments()
        val itemType = bundle.getInt("Item")
        val id = bundle.getString("Id")?.toLong()

        val title: Flow<String>
        val source: Flow<org.akanework.gramophone.logic.queue.QueueSource?>
        var rawOrderExposed: Sorter.Type? = null

        when (itemType) {
            R.id.album -> {
                val item = mainActivity.reader.albumListFlow.map { it.find { it.id == id } }
                title = item.map { it?.title ?: requireContext().getString(R.string.unknown_album) }
                source = item.map {
                    org.akanework.gramophone.logic.queue.QueueSource(
                        kind = org.akanework.gramophone.logic.queue.QueueSourceKind.ALBUM,
                        identifier = id?.toString(),
                        displayName = it?.title,
                    )
                }
                itemList = item.map { it?.songList }
                rawOrderExposed = Sorter.Type.ByAlbumTitleAscending
            }

            /*R.id.artist -> {
                val item = libraryViewModel.artistItemList.value!![position]
                title = item.title ?: requireContext().getString(R.string.unknown_artist)
                itemList = item.songList
            } TODO */

            R.id.genre -> {
                // Genres
                val item = mainActivity.reader.genreListFlow.map { it.find { it.id == id } }
                title = item.map { it?.title ?: requireContext().getString(R.string.unknown_genre) }
                source = item.map {
                    org.akanework.gramophone.logic.queue.QueueSource(
                        kind = org.akanework.gramophone.logic.queue.QueueSourceKind.GENRE,
                        identifier = id?.toString(),
                        displayName = it?.title,
                    )
                }
                itemList = item.map { it?.songList }
            }

            R.id.date -> {
                // Dates
                val item = mainActivity.reader.dateListFlow.map { it.find { it.id == id } }
                title = item.map { it?.title ?: requireContext().getString(R.string.unknown_year) }
                source = item.map {
                    org.akanework.gramophone.logic.queue.QueueSource(
                        kind = org.akanework.gramophone.logic.queue.QueueSourceKind.ALL_SONGS,
                        identifier = id?.let { "date:$it" },
                        displayName = it?.title,
                    )
                }
                itemList = item.map { it?.songList }
            }

            /*R.id.album_artist -> {
                // Album artists
                val item = libraryViewModel.albumArtistItemList.value!![position]
                title = item.title ?: requireContext().getString(R.string.unknown_artist)
                itemList = item.songList
            } TODO */

            R.id.playlist -> {
                // Legacy dynamic library items (Favourites, Recently added) opened as a song list.
                val clazz = arguments?.getString("Class") ?: "null"
                val item = mainActivity.reader.dynamicItemsFlow.map { items ->
                    items.find {
                        if (id != null) it.id == id else it.javaClass.name == clazz
                    }
                }
                    .provideReplayCacheInvalidationManager()
                    .sharePauseableIn(
                        CoroutineScope(Dispatchers.Default),
                        WhileSubscribed(),
                        replay = 1
                    )
                title = item.map {
                    when (it) {
                        is RecentlyAdded -> requireContext().getString(R.string.recently_added)
                        is Favorite -> requireContext().getString(R.string.playlist_favourite)
                        else -> it?.title ?: requireContext().getString(R.string.unknown_queue)
                    }
                }
                source = item.map {
                    when (it) {
                        is RecentlyAdded -> org.akanework.gramophone.logic.queue.QueueSource(
                            kind = org.akanework.gramophone.logic.queue.QueueSourceKind.RECENTLY_ADDED,
                            identifier = "recently_added",
                            displayName = "Recently added",
                        )
                        is Favorite -> org.akanework.gramophone.logic.queue.QueueSource(
                            kind = org.akanework.gramophone.logic.queue.QueueSourceKind.FAVOURITES,
                            identifier = "favourites",
                            displayName = "Favourites",
                        )
                        else -> null
                    }
                }
                itemList = item.map { it?.songList }
                rawOrderExposed = Sorter.Type.NaturalOrder
            }

            else -> throw IllegalArgumentException()
        }

        val sharedTitle = title.sharePauseableIn(lifecycleScope + Dispatchers.Default,
            WhileSubscribed(), replay = 1)
        lifecycleScope.launch(Dispatchers.Default) {
            sharedTitle.collect {
                withContext(Dispatchers.Main) {
                    // Show title text.
                    collapsingToolbarLayout.title = it
                }
            }
        }

        val songAdapter =
            SongAdapter(
                this,
                sharedTitle,
                itemList,
                rawOrderExposed = rawOrderExposed,
                isSubFragment = itemType,
                queueSource = source,
            )

        recyclerView.enableEdgeToEdgePaddingListener()
        recyclerView.setAppBar(appBarLayout)
        recyclerView.adapter = songAdapter.concatAdapter

        // Build FastScroller.
        recyclerView.fastScroll(songAdapter, songAdapter.itemHeightHelper)

        topAppBar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        return rootView
    }
}
