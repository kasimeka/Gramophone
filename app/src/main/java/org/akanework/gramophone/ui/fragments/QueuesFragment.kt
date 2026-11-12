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

package org.akanework.gramophone.ui.fragments

import org.akanework.gramophone.logic.queue.QueueDragReorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ShareCompat
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.deleteQueue
import org.akanework.gramophone.logic.discardQueueEditUndo
import org.akanework.gramophone.logic.editQueue
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.moveQueueRow
import org.akanework.gramophone.logic.playQueue
import org.akanework.gramophone.logic.queue.Queue
import org.akanework.gramophone.logic.queue.QueueEditOperation
import org.akanework.gramophone.logic.queue.QueueOrderEditor
import org.akanework.gramophone.logic.queue.QueuePlaybackAction
import org.akanework.gramophone.logic.queue.QueuePlaybackRules
import org.akanework.gramophone.logic.queue.QueuePlaybackState
import org.akanework.gramophone.logic.queue.QueueRepository
import org.akanework.gramophone.logic.renameQueue
import org.akanework.gramophone.logic.reorderQueue
import org.akanework.gramophone.logic.setMediaItemsWithTitle
import org.akanework.gramophone.logic.shuffleQueue
import org.akanework.gramophone.logic.undoQueueEdit
import org.akanework.gramophone.logic.ui.DecorPanelRules
import org.akanework.gramophone.logic.utils.MediaRowCodec
import org.akanework.gramophone.ui.GramophoneTheme
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.NowPlayingPresentation
import org.akanework.gramophone.ui.SongMenuLogic
import org.akanework.gramophone.ui.adapters.Sorter
import org.akanework.gramophone.ui.components.AddToQueueSheet
import org.akanework.gramophone.ui.components.QueueListContent
import org.akanework.gramophone.ui.components.compose.QueueSongRow
import org.akanework.gramophone.ui.fragments.ArtistSubFragment
import org.akanework.gramophone.ui.fragments.DetailDialogFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import org.akanework.gramophone.ui.pureDark
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId
import java.util.UUID
import kotlin.math.roundToInt

/** The Queues home tab: lists every durable queue. */
class QueuesFragment : BaseFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        enableEdgeToEdgePaddingListener()
        setContent {
            GramophoneTheme(pureDark = pureDark(requireContext())) {
                QueuesScreen(
                    activity = requireActivity() as MainActivity,
                    lifecycleOwner = viewLifecycleOwner,
                )
            }
        }
    }
}

@Composable
private fun QueuesScreen(
    activity: MainActivity,
    lifecycleOwner: LifecycleOwner,
) {
    val repository = remember { activity.gramophoneApplication.queueRepository }
    val queues by repository.queues.collectAsState(initial = emptyList())
    val activeQueue by repository.lastActiveQueue.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(activity.getPlayer()?.isPlaying == true) }
    var activeCurrentRow by remember { mutableStateOf(activity.getPlayer()?.currentMediaItemIndex?.takeIf { it >= 0 }) }

    DisposableEffect(lifecycleOwner) {
        activity.controllerViewModel.addRecreationalPlayerListener(
            lifecycleOwner.lifecycle,
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    activeCurrentRow = player.currentMediaItemIndex.takeIf { it >= 0 }
                }
            },
        ) { controller ->
            isPlaying = controller.isPlaying
            activeCurrentRow = controller.currentMediaItemIndex.takeIf { it >= 0 }
        }
        onDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        QueueListContent(
            queues = queues,
            activeQueueId = activeQueue?.id,
            isPlaying = isPlaying,
            activeCurrentRow = activeCurrentRow,
            onQueueClick = { openQueue(activity, it.id) },
            onPlayQueue = { queue ->
                scope.launch(Dispatchers.IO) { activity.getPlayer()?.playQueue(queue.id) }
            },
            onShuffleQueue = { queue ->
                scope.launch(Dispatchers.IO) {
                    activity.getPlayer()?.shuffleQueue(
                        queue.id,
                        shufflePlay = true,
                        playDifferentSong = true,
                    )
                }
            },
            listState = listState,
            onMoveQueue = { from, to ->
                val queue = queues.getOrNull(from) ?: return@QueueListContent
                scope.launch(Dispatchers.IO) { repository.moveQueue(queue.id, to) }
            },
        )
    }

}

/** Opens the queue's detail without changing the active queue or starting playback. */
internal fun openQueue(activity: MainActivity, id: UUID) {
    activity.startFragment(QueueDetailFragment()) {
        putString(QueueDetailFragment.ARG_QUEUE_ID, id.toString())
    }
}

/** Rename through the service so the player's active title follows; fall back to the repository. */
private suspend fun renameQueueFromUi(
    activity: MainActivity,
    repository: QueueRepository,
    id: UUID,
    title: String,
) {
    val controller = activity.getPlayer()
    withContext(Dispatchers.IO) {
        val renamed = controller?.renameQueue(id, title) ?: false
        if (!renamed) repository.rename(id, title)
    }
}

/** Delete through the service so active-queue deletion stops playback and clears the player. */
private suspend fun deleteQueueFromUi(
    activity: MainActivity,
    repository: QueueRepository,
    id: UUID,
): Boolean {
    val controller = activity.getPlayer()
    return withContext(Dispatchers.IO) {
        val deleted = controller?.deleteQueue(id) ?: false
        deleted || repository.delete(id)
    }
}

@Composable
private fun RenameQueueDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    val trimmed = title.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_queue)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(stringResource(R.string.queue_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty() && trimmed != initialTitle.trim(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun DeleteQueueDialog(
    queueTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_really, queueTitle)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * The queue detail destination: transport, ordered rows, and the queue's editing actions. The old
 * queue sheet and multi-queue preview remain untouched for the later retirement ticket.
 */
class QueueDetailFragment : BaseFragment() {
    companion object {
        const val ARG_QUEUE_ID = "QueueId"
        const val ARG_SCROLL_TO_CURRENT = "ScrollToCurrent"
    }

    val queueId: UUID?
        get() = arguments?.getString(ARG_QUEUE_ID)?.let(UUID::fromString)

    private val scrollRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun requestScrollToNowPlaying() {
        scrollRequests.tryEmit(Unit)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        enableEdgeToEdgePaddingListener()
        val id = queueId
        val initialScrollToCurrent = arguments?.getBoolean(ARG_SCROLL_TO_CURRENT, false) ?: false
        val lifecycleOwner = viewLifecycleOwner
        setContent {
            GramophoneTheme(pureDark = pureDark(requireContext())) {
                QueueDetailScreen(id, lifecycleOwner, initialScrollToCurrent, scrollRequests)
            }
        }
    }
}

private data class DragRow(val key: Long, val encoded: String)

private data class DetailPlayerState(
    val currentRow: Int? = null,
    val isPlaying: Boolean = false,
)

private class QueueDragState {
    var draggingKey by mutableStateOf<Long?>(null)
    var dragStartIndex by mutableStateOf<Int?>(null)
    var dragStartRows by mutableStateOf<List<String>>(emptyList())
    var dragOffset by mutableFloatStateOf(0f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueDetailScreen(
    id: UUID?,
    lifecycleOwner: LifecycleOwner,
    initialScrollToCurrent: Boolean = false,
    scrollRequests: Flow<Unit> = emptyFlow(),
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val repository = remember { context.gramophoneApplication.queueRepository }
    val queue by remember(id) {
        if (id == null) kotlinx.coroutines.flow.flowOf(null) else repository.queue(id)
    }.collectAsState(initial = null)
    val activeQueue by repository.lastActiveQueue.collectAsState()
    val loadedQueue = queue
    if (id == null || loadedQueue == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.queue_detail_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    val isActive = activeQueue?.id == loadedQueue.id
    var playerState by remember {
        val controller = activity.getPlayer()
        mutableStateOf(
            DetailPlayerState(
                currentRow = controller?.currentMediaItemIndex?.takeIf { it >= 0 },
                isPlaying = controller != null && NowPlayingPresentation.isEffectivelyPlaying(
                    controller.playWhenReady,
                    controller.playbackState,
                ),
            )
        )
    }
    var currentPlayingKey by remember(loadedQueue.id) { mutableStateOf<Long?>(null) }
    var actionsOpen by remember { mutableStateOf(false) }
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    var hasScrolledInitial by rememberSaveable(loadedQueue.id) { mutableStateOf(false) }
    // Local optimistic row order for drag/reorder. Reorder rows in place during drag (stable keys,
    // no forced scroll on drop) so the dragged item stays under the pointer/at its drop position,
    // matching upstream notifyItemMoved semantics as closely as LazyColumn allows.
    var rowKeySeed by remember(loadedQueue.id) { mutableLongStateOf(0L) }
    var displayRows by remember(loadedQueue.id) {
        mutableStateOf(loadedQueue.rows.map { DragRow(rowKeySeed++, it) })
    }
    val dragState = remember(loadedQueue.id) { QueueDragState() }
    // Tracks an in-flight local reorder as (previous, committed). Prevents the stale pre-move
    // repository snapshot from clobbering the optimistic order while the async move commits.
    var pendingMove by remember(loadedQueue.id) {
        mutableStateOf<Pair<List<String>, List<String>>?>(null)
    }
    val latestDisplayRowsState = androidx.compose.runtime.rememberUpdatedState(displayRows)

    LaunchedEffect(initialScrollToCurrent, loadedQueue.id, displayRows.size, playerState.currentRow, isActive) {
        if (initialScrollToCurrent && !hasScrolledInitial && isActive) {
            val target = QueuePlaybackRules.nowPlayingScrollTarget(
                rowCount = displayRows.size,
                isActive = isActive,
                currentRow = playerState.currentRow
                    ?: activity.getPlayer()?.currentMediaItemIndex?.takeIf { it >= 0 },
            )
            if (target != null) {
                hasScrolledInitial = true
                val listTarget = target + 1
                snapshotFlow { listState.layoutInfo.totalItemsCount }
                    .filter { it > listTarget }
                    .first()
                listState.animateScrollToItem(listTarget)
            }
        }
    }

    LaunchedEffect(scrollRequests, loadedQueue.id) {
        scrollRequests.collect {
            val target = QueuePlaybackRules.nowPlayingScrollTarget(
                rowCount = latestDisplayRowsState.value.size,
                isActive = isActive,
                currentRow = playerState.currentRow
                    ?: activity.getPlayer()?.currentMediaItemIndex?.takeIf { it >= 0 },
            )
            if (target != null) {
                val listTarget = target + 1
                snapshotFlow { listState.layoutInfo.totalItemsCount }
                    .filter { it > listTarget }
                    .first()
                listState.animateScrollToItem(listTarget)
            }
        }
    }

    fun resolveCurrentPlayingKey(rows: List<DragRow>, currentRow: Int?): Long? {
        val index = currentRow ?: playerState.currentRow ?: return currentPlayingKey
        return rows.getOrNull(index)?.key ?: currentPlayingKey
    }

    LaunchedEffect(loadedQueue.rows, dragState.draggingKey, pendingMove) {
        if (dragState.draggingKey != null) return@LaunchedEffect
        val repoRows = loadedQueue.rows
        val pending = pendingMove
        if (pending != null) {
            val (previousRows, committedRows) = pending
            when (repoRows) {
                committedRows -> pendingMove = null
                previousRows -> return@LaunchedEffect
                else -> pendingMove = null // External/concurrent update won; accept repository state.
            }
        }
        if (displayRows.map { it.encoded } != repoRows) {
            val newRows = repoRows.map { DragRow(rowKeySeed++, it) }
            displayRows = newRows
            if (isActive) {
                val controller = activity.getPlayer()
                val currentRow = controller?.currentMediaItemIndex?.takeIf { it >= 0 } ?: playerState.currentRow
                currentPlayingKey = newRows.getOrNull(currentRow ?: -1)?.key ?: currentPlayingKey
            }
        }
    }
    val rows = displayRows.map { it.encoded }

    DisposableEffect(lifecycleOwner) {
        activity.controllerViewModel.addRecreationalPlayerListener(
            lifecycleOwner.lifecycle,
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    val currentRow = player.currentMediaItemIndex.takeIf { it >= 0 }
                    playerState = DetailPlayerState(
                        currentRow = currentRow,
                        isPlaying = NowPlayingPresentation.isEffectivelyPlaying(
                            player.playWhenReady,
                            player.playbackState,
                        ),
                    )
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        currentPlayingKey = latestDisplayRowsState.value.getOrNull(currentRow ?: -1)?.key
                    } else if (currentPlayingKey == null &&
                        (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED))
                    ) {
                        currentPlayingKey = latestDisplayRowsState.value.getOrNull(currentRow ?: -1)?.key
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // Actual track changes update playing identity; reorders keep the same item.
                    // Key resolution is handled in onEvents against the latest display rows.
                }
            },
        ) { controller ->
            val currentRow = controller.currentMediaItemIndex.takeIf { it >= 0 }
            playerState = DetailPlayerState(
                currentRow = currentRow,
                isPlaying = NowPlayingPresentation.isEffectivelyPlaying(
                    controller.playWhenReady,
                    controller.playbackState,
                ),
            )
            currentPlayingKey = latestDisplayRowsState.value.getOrNull(currentRow ?: -1)?.key
        }
        onDispose { }
    }

    LaunchedEffect(isActive, loadedQueue.id) {
        if (!isActive) {
            currentPlayingKey = null
        } else if (currentPlayingKey == null) {
            // The player may still hold the previous queue here; the stored bookmark is this queue's row.
            currentPlayingKey = displayRows.getOrNull(loadedQueue.normalizedBookmark().row)?.key
        }
    }
    // Undo is owned by this screen and cannot outlive it.
    DisposableEffect(Unit) {
        onDispose {
            scope.launch(Dispatchers.IO) { activity.getPlayer()?.discardQueueEditUndo() }
        }
    }

    val bookmark = loadedQueue.normalizedBookmark()
    val playbackAction = QueuePlaybackRules.queuePlayButton(
        QueuePlaybackState(
            rowCount = rows.size,
            bookmark = bookmark,
            hasBeenPlayed = loadedQueue.hasBeenPlayed || isActive,
            isActive = isActive,
            isPlaying = isActive && playerState.isPlaying,
        )
    )
    val anchor = QueuePlaybackRules.detailAnchor(
        rowCount = rows.size,
        bookmark = bookmark,
        isActive = isActive,
        currentRow = if (isActive) playerState.currentRow else null,
    )
    val canShuffle = QueueOrderEditor.canShuffleEverything(rows.size, anchor)

    fun showUndo(messageRes: Int) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = context.getString(messageRes),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                withContext(Dispatchers.IO) {
                    activity.getPlayer()?.undoQueueEdit(loadedQueue.id)
                }
            }
        }
    }

    fun playNext(position: Int) {
        scope.launch(Dispatchers.IO) {
            val ok = activity.getPlayer()
                ?.editQueue(loadedQueue.id, QueueEditOperation.PLAY_NEXT, position) == true
            if (ok) withContext(Dispatchers.Main) { showUndo(R.string.will_play_next) }
        }
    }

    fun removeRow(position: Int) {
        scope.launch(Dispatchers.IO) {
            val ok = activity.getPlayer()
                ?.editQueue(loadedQueue.id, QueueEditOperation.REMOVE, position) == true
            if (ok) withContext(Dispatchers.Main) { showUndo(R.string.removed_from_queue) }
        }
    }

    fun moveRow(from: Int, to: Int, committedRows: List<String>, previousRows: List<String>) {
        scope.launch {
            pendingMove = previousRows to committedRows
            val ok = withContext(Dispatchers.IO) {
                activity.getPlayer()?.moveQueueRow(loadedQueue.id, from, to) == true
            }
            if (!ok) {
                // Revert the optimistic order if the service rejected the move.
                pendingMove = null
                if (dragState.draggingKey == null) {
                    displayRows = previousRows.map { DragRow(rowKeySeed++, it) }
                }
            }
        }
    }

    fun shuffleRows() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                activity.getPlayer()?.shuffleQueue(loadedQueue.id, shufflePlay = false) == true
            }
            if (ok) showUndo(R.string.queue_shuffled)
        }
    }

    fun reorderQueueBy(sortType: String) {
        scope.launch(Dispatchers.IO) {
            activity.getPlayer()?.reorderQueue(loadedQueue.id, sortType)
        }
    }

    fun addAllToQueue() {
        val currentRows = latestDisplayRowsState.value.map { it.encoded }
        if (DecorPanelRules.canAddToQueue(currentRows.size)) {
            AddToQueueSheet(activity, currentRows).show()
        }
    }

    fun addToQueue(encoded: String) {
        AddToQueueSheet(activity, listOf(encoded)).show()
    }

    fun goToAlbum(item: MediaItem) {
        item.mediaMetadata.albumId?.let { albumId ->
            activity.startFragment(GeneralSubFragment()) {
                putString("Id", albumId.toString())
                putInt("Item", R.id.album)
            }
        }
    }

    fun goToArtist(item: MediaItem) {
        item.mediaMetadata.artistId?.let { artistId ->
            activity.startFragment(ArtistSubFragment()) {
                putString("Id", artistId.toString())
                putInt("Item", R.id.artist)
            }
        }
    }

    fun showDetails(item: MediaItem) {
        activity.startFragment(DetailDialogFragment()) {
            putString("Id", item.mediaId)
        }
    }

    fun shareItem(item: MediaItem) {
        SongMenuLogic.share(activity, item)
    }

    fun deleteItem(item: MediaItem) {
        SongMenuLogic.confirmDelete(activity, item)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val bottomPaddingDp = with(LocalDensity.current) {
        activity.playerBottomSheet.getBottomPadding().toDp()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        scope.launch {
                            scrollBehavior.state.heightOffset = 0f
                            listState.animateScrollToItem(0)
                        }
                    },
                    title = { Text(loadedQueue.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { activity.supportFragmentManager.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { actionsOpen = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = actionsOpen,
                                onDismissRequest = { actionsOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename)) },
                                    onClick = {
                                        actionsOpen = false
                                        renameOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = {
                                        actionsOpen = false
                                        deleteOpen = true
                                    },
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (isActive) {
                    val currentRow = playerState.currentRow
                    if (currentRow != null && currentRow in displayRows.indices) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(bottom = bottomPaddingDp),
                        ) {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text(stringResource(R.string.shuffle))
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                SmallFloatingActionButton(
                                    onClick = { shuffleRows() },
                                ) {
                                    Icon(
                                        Icons.Rounded.Shuffle,
                                        contentDescription = stringResource(R.string.shuffle),
                                    )
                                }
                            }
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text(stringResource(R.string.scroll_to_now_playing))
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                FloatingActionButton(
                                    onClick = {
                                        scope.launch {
                                            val target = QueuePlaybackRules.nowPlayingScrollTarget(
                                                rowCount = latestDisplayRowsState.value.size,
                                                isActive = isActive,
                                                currentRow = playerState.currentRow,
                                            )
                                            target?.let { listState.animateScrollToItem(it + 1) }
                                        }
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_music_history),
                                        contentDescription = stringResource(R.string.scroll_to_now_playing),
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.padding(bottom = bottomPaddingDp)) {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text(stringResource(R.string.shuffle))
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                FloatingActionButton(
                                    onClick = { shuffleRows() },
                                ) {
                                    Icon(
                                        Icons.Rounded.Shuffle,
                                        contentDescription = stringResource(R.string.shuffle),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(bottom = bottomPaddingDp),
                    ) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = {
                                PlainTooltip {
                                    Text(stringResource(R.string.shuffle))
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            SmallFloatingActionButton(
                                onClick = { shuffleRows() },
                            ) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    contentDescription = stringResource(R.string.shuffle),
                                )
                            }
                        }
                        val pause = playbackAction == QueuePlaybackAction.Pause
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = {
                                PlainTooltip {
                                    Text(stringResource(if (pause) R.string.pause else R.string.play))
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        activity.getPlayer()?.playQueue(loadedQueue.id)
                                    }
                                },
                            ) {
                                Icon(
                                    if (pause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(
                                        if (pause) R.string.pause else R.string.play
                                    ),
                                )
                            }
                        }
                    }
                }
            },
            snackbarHost = {},
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
        if (displayRows.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.queue_detail_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            val rowHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
            val edgeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
            val latestDisplayRows by androidx.compose.runtime.rememberUpdatedState(displayRows)

            fun onDragDelta(deltaY: Float, isScrollCompensation: Boolean = false) {
                val key = dragState.draggingKey ?: return
                val currentRows = latestDisplayRows
                val currentIndex = currentRows.indexOfFirst { it.key == key }
                if (currentIndex < 0) return

                val step = QueueDragReorder.onDragDelta(
                    state = QueueDragReorder.State(
                        items = currentRows,
                        currentIndex = currentIndex,
                        dragOffset = dragState.dragOffset,
                    ),
                    deltaY = deltaY,
                    rowHeightPx = rowHeightPx,
                    isScrollCompensation = isScrollCompensation,
                    firstVisibleItemIndex = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0),
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                )

                dragState.dragOffset = step.state.dragOffset
                if (step.swapped) {
                    displayRows = step.state.items
                    if (step.requiresScrollAnchorReset) {
                        scope.launch {
                            listState.scrollToItem(0, 0)
                        }
                    }
                }
            }

            // Incremental in-drag reordering with edge auto-scroll.
            LaunchedEffect(dragState.draggingKey) {
                if (dragState.draggingKey == null) return@LaunchedEffect
                while (dragState.draggingKey != null) {
                    val key = dragState.draggingKey ?: break
                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                    if (info != null) {
                        val viewportStart = listState.layoutInfo.viewportStartOffset
                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                        val draggedTop = info.offset + dragState.dragOffset
                        val draggedBottom = draggedTop + info.size
                        val delta = when {
                            draggedTop < viewportStart + edgeThresholdPx ->
                                (draggedTop - (viewportStart + edgeThresholdPx)) * 0.2f
                            draggedBottom > viewportEnd - edgeThresholdPx ->
                                (draggedBottom - (viewportEnd - edgeThresholdPx)) * 0.2f
                            else -> 0f
                        }
                        if (delta != 0f &&
                            ((delta < 0f && listState.canScrollBackward) ||
                                (delta > 0f && listState.canScrollForward))
                        ) {
                            val consumed = listState.scrollBy(delta)
                            if (consumed != 0f) {
                                // List content scrolled by `consumed`. Compensate visual translation so the
                                // dragged item remains fixed under the finger.
                                onDragDelta(consumed, isScrollCompensation = true)
                            }
                        }
                    }
                    kotlinx.coroutines.delay(16L)
                }
            }

            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        start = padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        end = padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        bottom = padding.calculateBottomPadding() + 88.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "queue_decor_panel") {
                        QueueDetailDecorPanel(
                            songCount = displayRows.size,
                            canScrollToCurrent = isActive && playerState.currentRow != null,
                            onScrollToCurrent = {
                                scope.launch {
                                    val target = QueuePlaybackRules.nowPlayingScrollTarget(
                                        rowCount = latestDisplayRowsState.value.size,
                                        isActive = isActive,
                                        currentRow = playerState.currentRow,
                                    )
                                    target?.let { listState.animateScrollToItem(it + 1) }
                                }
                            },
                            onAddAllToQueue = ::addAllToQueue,
                            onReorder = ::reorderQueueBy,
                        )
                    }
                    itemsIndexed(items = displayRows, key = { _, row -> row.key }) { index, row ->
                        val encoded = row.encoded
                        val decoded = remember(encoded) { MediaRowCodec.decode(encoded) }
                        val isDragging = dragState.draggingKey == row.key
                        QueueSongRow(
                            item = decoded,
                            isDragging = isDragging,
                            dragTranslation = if (isDragging) dragState.dragOffset else 0f,
                            isCurrent = isActive && currentPlayingKey == row.key,
                            isPlaying = isActive && playerState.isPlaying,
                            canPlayNext = QueueOrderEditor.canPlayNext(displayRows.size, anchor, index),
                            onPlay = {
                                scope.launch(Dispatchers.IO) {
                                    activity.getPlayer()?.playQueue(loadedQueue.id, index)
                                }
                            },
                            onPlayNext = { playNext(index) },
                            onAddToQueue = { addToQueue(encoded) },
                            onGoToAlbum = { decoded?.let(::goToAlbum) },
                            onGoToArtist = { decoded?.let(::goToArtist) },
                            onDetails = { decoded?.let(::showDetails) },
                            onShare = { decoded?.let(::shareItem) },
                            onDelete = { decoded?.let(::deleteItem) },
                            onRemoveFromQueue = { removeRow(index) },
                            onDrag = { amount ->
                                onDragDelta(amount)
                            },
                            onDragEnd = {
                                val key = dragState.draggingKey
                                val startIndex = dragState.dragStartIndex
                                val previousRows = dragState.dragStartRows
                                dragState.draggingKey = null
                                dragState.dragStartIndex = null
                                dragState.dragStartRows = emptyList()
                                dragState.dragOffset = 0f
                                if (key == null || startIndex == null) return@QueueSongRow
                                val finalRows = latestDisplayRows
                                val to = finalRows.indexOfFirst { it.key == key }
                                if (to < 0 || to == startIndex) return@QueueSongRow
                                // Commit reorder once on drop. No post-drop snap/scroll compensation: the
                                // dragged item was reordered incrementally and is already at its drop slot.
                                moveRow(startIndex, to, finalRows.map { it.encoded }, previousRows)
                            },
                            onDragCancel = {
                                dragState.draggingKey = null
                                dragState.dragStartIndex = null
                                dragState.dragStartRows = emptyList()
                                dragState.dragOffset = 0f
                            },
                            onDragStart = {
                                val index = displayRows.indexOfFirst { it.key == row.key }
                                dragState.draggingKey = row.key
                                dragState.dragStartIndex = index.takeIf { it >= 0 }
                                dragState.dragStartRows = displayRows.map { it.encoded }
                                dragState.dragOffset = 0f
                            },
                        )
                    }
                }

                QueueFastScrollbar(
                    listState = listState,
                    totalItems = displayRows.size + 1,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + 88.dp,
                            end = padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        ),
                )
            }
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPaddingDp),
        ) { data -> SwipeDismissibleSnackbar(data) }
    }

    if (renameOpen) {
        RenameQueueDialog(
            initialTitle = loadedQueue.title,
            onDismiss = { renameOpen = false },
            onConfirm = { title ->
                renameOpen = false
                scope.launch { renameQueueFromUi(activity, repository, loadedQueue.id, title) }
            },
        )
    }
    if (deleteOpen) {
        DeleteQueueDialog(
            queueTitle = loadedQueue.title,
            onDismiss = { deleteOpen = false },
            onConfirm = {
                deleteOpen = false
                scope.launch {
                    deleteQueueFromUi(activity, repository, loadedQueue.id)
                    activity.supportFragmentManager.popBackStack()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDismissibleSnackbar(data: SnackbarData) {
    key(data) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    data.dismiss()
                    true
                } else {
                    false
                }
            },
        )
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
        ) {
            Snackbar(data)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueDetailDecorPanel(
    songCount: Int,
    canScrollToCurrent: Boolean,
    onScrollToCurrent: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onReorder: (String) -> Unit,
) {
    var reorderMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 24.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.songs, songCount, songCount),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (canScrollToCurrent) {
                        Modifier.clickable(onClick = onScrollToCurrent)
                    } else {
                        Modifier
                    }
                ),
        )
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(stringResource(R.string.add_to_queue)) } },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = onAddAllToQueue,
                enabled = DecorPanelRules.canAddToQueue(songCount),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_playlist_add),
                    contentDescription = stringResource(R.string.add_to_queue),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Box {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(stringResource(R.string.sort)) } },
                state = rememberTooltipState(),
            ) {
                IconButton(
                    onClick = { reorderMenuOpen = true },
                    enabled = songCount > 1,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sort),
                        contentDescription = stringResource(R.string.sort),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = reorderMenuOpen,
                onDismissRequest = { reorderMenuOpen = false },
            ) {
                val options = listOf(
                    R.string.sort_by_name to Sorter.Type.ByTitleAscending.name,
                    R.string.sort_by_artist to Sorter.Type.ByArtistAscending.name,
                    R.string.sort_by_album to Sorter.Type.ByAlbumTitleAscending.name,
                    R.string.sort_by_album_artist to Sorter.Type.ByAlbumArtistAscending.name,
                    R.string.sort_by_album_year to Sorter.Type.ByAlbumYearDescending.name,
                    R.string.sort_by_add_date to Sorter.Type.ByAddDateDescending.name,
                    R.string.sort_by_release_date to Sorter.Type.ByReleaseDateDescending.name,
                    R.string.sort_by_modified_date to Sorter.Type.ByModifiedDateDescending.name,
                )
                options.forEach { (labelRes, sortType) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(labelRes)) },
                        onClick = {
                            reorderMenuOpen = false
                            onReorder(sortType)
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reverse_order)) },
                    onClick = {
                        reorderMenuOpen = false
                        onReorder("REVERSE")
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueFastScrollbar(
    listState: LazyListState,
    totalItems: Int,
    modifier: Modifier = Modifier,
) {
    if (totalItems <= 0) return

    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (visibleItems.size >= totalItems && !listState.canScrollForward && !listState.canScrollBackward) {
        return
    }

    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragThumbTopPx by remember { mutableFloatStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress, isDragging) {
        if (listState.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(1500L)
            isVisible = false
        }
    }

    BoxWithConstraints(modifier = modifier.width(24.dp)) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        if (trackHeightPx <= 0f) return@BoxWithConstraints

        val visibleCount = visibleItems.size.coerceAtLeast(1)
        val fractionVisible = (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
        val minThumbHeightPx = with(LocalDensity.current) { 48.dp.toPx() }
        val thumbHeightPx = (trackHeightPx * fractionVisible).coerceIn(minThumbHeightPx, trackHeightPx)
        val scrollableTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        val firstItemSize = visibleItems.firstOrNull()?.size?.toFloat() ?: 1f
        val exactFirstItemPosition = firstIndex.toFloat() + (firstOffset.toFloat() / firstItemSize).coerceIn(0f, 1f)
        val maxScrollItems = (totalItems - visibleCount).coerceAtLeast(1).toFloat()
        val scrollProgress = (exactFirstItemPosition / maxScrollItems).coerceIn(0f, 1f)

        val idleThumbTopPx = when {
            !listState.canScrollBackward -> 0f
            !listState.canScrollForward -> scrollableTrackPx
            else -> scrollProgress * scrollableTrackPx
        }

        val thumbTopPx = if (isDragging) dragThumbTopPx else idleThumbTopPx

        val thumbWidth by animateDpAsState(
            targetValue = if (isDragging) 6.dp else 4.dp,
            animationSpec = tween(durationMillis = 150),
            label = "scrollbarThumbWidth",
        )
        val thumbAlpha by animateFloatAsState(
            targetValue = if (isVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 150),
            label = "scrollbarThumbAlpha",
        )

        val density = LocalDensity.current
        val thumbHeightDp = remember(density, thumbHeightPx) {
            with(density) { thumbHeightPx.toDp() }
        }

        val currentTotalItems by androidx.compose.runtime.rememberUpdatedState(totalItems)
        val currentThumbHeightPx by androidx.compose.runtime.rememberUpdatedState(thumbHeightPx)
        val currentScrollableTrackPx by androidx.compose.runtime.rememberUpdatedState(scrollableTrackPx)
        val currentThumbTopPx by androidx.compose.runtime.rememberUpdatedState(thumbTopPx)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        val trackPx = currentScrollableTrackPx
                        val thumbH = currentThumbHeightPx
                        val grabOffset = if (down.position.y in currentThumbTopPx..(currentThumbTopPx + thumbH)) {
                            down.position.y - currentThumbTopPx
                        } else {
                            thumbH / 2f
                        }
                        var currentY = (down.position.y - grabOffset).coerceIn(0f, trackPx)
                        dragThumbTopPx = currentY
                        val total = currentTotalItems
                        if (total > 0 && trackPx > 0f) {
                            val initialTarget = ((currentY / trackPx) * (total - 1))
                                .roundToInt()
                                .coerceIn(0, total - 1)
                            coroutineScope.launch {
                                listState.scrollToItem(initialTarget)
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                break
                            }
                            val delta = change.position.y - change.previousPosition.y
                            if (delta != 0f) {
                                val activeTrackPx = currentScrollableTrackPx
                                val activeTotal = currentTotalItems
                                currentY = (currentY + delta).coerceIn(0f, activeTrackPx)
                                dragThumbTopPx = currentY
                                if (activeTotal > 0 && activeTrackPx > 0f) {
                                    val target = ((currentY / activeTrackPx) * (activeTotal - 1))
                                        .roundToInt()
                                        .coerceIn(0, activeTotal - 1)
                                    coroutineScope.launch {
                                        listState.scrollToItem(target)
                                    }
                                }
                                change.consume()
                            }
                        }
                        isDragging = false
                    }
                },
        ) {
            if (thumbAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 3.dp)
                        .offset { IntOffset(x = 0, y = thumbTopPx.roundToInt()) }
                        .width(thumbWidth)
                        .height(thumbHeightDp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = thumbAlpha),
                            shape = RoundedCornerShape(percent = 50),
                        ),
                )
            }
        }
    }
}



