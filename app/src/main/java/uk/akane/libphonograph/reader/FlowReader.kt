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

package uk.akane.libphonograph.reader

import android.content.Context
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.emitOrDie
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.utils.flows.Invalidation
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.logic.utils.flows.conflateAndBlockWhenPaused
import org.akanework.gramophone.logic.utils.flows.provideReplayCacheInvalidationManager
import org.akanework.gramophone.logic.utils.flows.repeatUntilDoneWhenUnpaused
import org.akanework.gramophone.logic.utils.flows.requireReplayCacheInvalidationManager
import uk.akane.libphonograph.contentObserverVersioningFlow
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.FileNode
import uk.akane.libphonograph.items.Genre
import uk.akane.libphonograph.items.Item

/**
 * Flow-based reader with focus on efficiency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowReader(
    context: Context,
    minSongLengthSecondsFlow: SharedFlow<Long>,
    blackListSetFlow: SharedFlow<Set<String>>,
    whiteListSetFlow: SharedFlow<Set<String>>,
    shouldUseEnhancedCoverReadingFlow: SharedFlow<Boolean?>, // null means load if permission is granted
    recentlyAddedFilterSecondFlow: SharedFlow<Long?>, // null means don't generate recently added
    favoriteSongIdsFlow: SharedFlow<Set<String>> = MutableStateFlow(emptySet()),
) {
    // IMPORTANT: Do not use distinctUntilChanged() or StateFlow here because equals() on thousands
    // of MediaItems is very, very expensive!
    private var awaitingRefresh = false
    var hadFirstRefresh = false
        private set
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineName("FlowReader"))
    private val finishRefreshTrigger = MutableSharedFlow<Unit>(replay = 0)
    private val manualRefreshTrigger = MutableSharedFlow<Unit>(replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        manualRefreshTrigger.emitOrDie(Unit)
    }

    private val mediaVersionFlow = contentObserverVersioningFlow(
        context, scope, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true
    ).shareIn(scope, Eagerly, replay = 1)

    private suspend fun maybeDoRead(
        context: Context,
        minSongLengthSeconds: Long,
        blackListSet: Set<String>,
        whiteListSet: Set<String>,
        shouldUseEnhancedCoverReading: Boolean?
    ) =
        // TODO repeatUntilDoneWhenUnpaused makes no sense with non-cancelable
        //  function, make it cancelable
        try {
            if (context.hasAudioPermission())
                Reader.readFromMediaStore(
                    context,
                    minSongLengthSeconds,
                    blackListSet,
                    whiteListSet,
                    shouldUseEnhancedCoverReading
                )
            else ReaderResult.emptyReaderResult()
        } catch (e: IllegalArgumentException) {
            // MediaStore.getExternalVolumeNames().contains("external_primary") can return
            // true but this exception might still be thrown. There's no API returning the
            // exact state that MediaProvider uses, so any checking is prone to races. This
            // case is one where try-catch works the best.
            if (e.message == "Volume external_primary not found")
                ReaderResult.emptyReaderResult()
            else
                throw e
        }

    // These expensive Reader calls are only done if we have someone (UI) observing the result AND
    // something changed. The PauseableFlows mechanism allows us to skip any unnecessary work.
    private val readerFlow: Flow<ReaderResult> =
        shouldUseEnhancedCoverReadingFlow.distinctUntilChanged()
            .flatMapLatest { shouldUseEnhancedCoverReading ->
                minSongLengthSecondsFlow.distinctUntilChanged()
                    .flatMapLatest { minSongLengthSeconds ->
                        blackListSetFlow.distinctUntilChanged()
                            .flatMapLatest { blackListSet ->
                                whiteListSetFlow.distinctUntilChanged()
                                    .flatMapLatest { whiteListSet ->
                                        mediaVersionFlow
                                            .onEach { requireReplayCacheInvalidationManager().invalidate() }
                                            .conflateAndBlockWhenPaused()
                                            .flatMapLatest {
                                                // manual refresh may for whatever reason
                                                // run in background, but all others
                                                // shouldn't trigger background runs
                                                manualRefreshTrigger.mapLatest { _ ->
                                                    repeatUntilDoneWhenUnpaused {
                                                        maybeDoRead(
                                                            context,
                                                            minSongLengthSeconds,
                                                            blackListSet,
                                                            whiteListSet,
                                                            shouldUseEnhancedCoverReading
                                                        )
                                                    }
                                                }
                                            }
                                    }
                            }
                    }
            }
            .onEach {
                finishRefreshTrigger.emit(Unit)
                awaitingRefresh = true
            }
            .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
            .sharePauseableIn(scope, WhileSubscribed(20000), WhileSubscribed(2000), replay = 1)
    val idMapFlow: Flow<Map<Long, MediaItem>> = readerFlow.map { it.idMap!! }
    val pathMapFlow = readerFlow.map { it.pathMap!! }
    val songListFlow: Flow<List<MediaItem>> = readerFlow.map { it.songList }
    val recentlyAddedFlow = recentlyAddedFilterSecondFlow.distinctUntilChanged()
        .onEach { requireReplayCacheInvalidationManager().invalidate() }
        .combine(songListFlow) { recentlyAddedFilterSecond, songList ->
            if (recentlyAddedFilterSecond != null)
                RecentlyAdded(
                    (System.currentTimeMillis() / 1000L) - recentlyAddedFilterSecond,
                    songList
                )
            else
                null
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
        .sharePauseableIn(scope, WhileSubscribed(20000), WhileSubscribed(2000), replay = 1)
    val favoriteFlow: Flow<Favorite?> = favoriteSongIdsFlow.distinctUntilChanged()
        .combine(songListFlow) { ids, songList ->
            val ordered = ids.mapNotNull { id -> songList.find { it.mediaId == id } }
            if (ids.isEmpty() && ordered.isEmpty()) Favorite(null, emptyList())
            else Favorite(null, ordered)
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
        .sharePauseableIn(scope, WhileSubscribed(20000), WhileSubscribed(2000), replay = 1)
    /** Dynamic library collections that are not playlists (Favourites, Recently added). */
    val dynamicItemsFlow: Flow<List<Item>> = combine(favoriteFlow, recentlyAddedFlow) { fav, recent ->
        listOfNotNull(recent, fav)
    }
    val albumListFlow: Flow<List<Album>> = readerFlow.map { it.albumList!! }
    val albumArtistListFlow: Flow<List<Artist>> = readerFlow.map { it.albumArtistList!! }
    val artistListFlow: Flow<List<Artist>> = readerFlow.map { it.artistList!! }
    val genreListFlow: Flow<List<Genre>> = readerFlow.map { it.genreList!! }
    val dateListFlow: Flow<List<Date>> = readerFlow.map { it.dateList!! }
    val folderStructureFlow: Flow<FileNode> = readerFlow.map { it.folderStructure!! }
    val shallowFolderFlow: Flow<FileNode> = readerFlow.map { it.shallowFolder!! }
    val foldersFlow: Flow<Set<String>> = readerFlow.map { it.folders!! }
    val foldersForWhitelistFlow: Flow<Set<String>> = readerFlow.map { it.foldersForWhitelist!! }

    /**
     * If the library hasn't been loaded yet, forces a load of the library. Otherwise forces a
     * manual refresh of the library. Suspends until new data is available.
     */
    suspend fun refresh() {
        hadFirstRefresh = true
        coroutineScope {
            if (!awaitingRefresh) {
                readerFlow.first()
                return@coroutineScope
            }
            val waiter = launch {
                finishRefreshTrigger.first()
            }
            manualRefreshTrigger.emit(Unit)
            waiter.join()
        }
    }
}