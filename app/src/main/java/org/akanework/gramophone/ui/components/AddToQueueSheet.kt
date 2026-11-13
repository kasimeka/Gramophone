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

package org.akanework.gramophone.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.queue.Queue
import org.akanework.gramophone.logic.queue.QueueTitles
import org.akanework.gramophone.logic.ui.SheetScrollRules
import org.akanework.gramophone.logic.utils.MediaRowCodec
import org.akanework.gramophone.ui.GramophoneTheme
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.pureDark

/**
 * Destination picker for one song or a whole library group. It is the Queues tab's list in a
 * bottom sheet: choosing a queue appends the rows, and the plus creates a numbered queue that
 * already contains them. Neither path plays or changes the active queue.
 */
class AddToQueueSheet(
    private val activity: MainActivity,
    private val songRows: List<String>,
    private val preloadedItems: List<MediaItem>? = null,
) : BottomSheetDialog(activity) {

    constructor(activity: MainActivity, items: List<MediaItem>) : this(
        activity = activity,
        songRows = items.map(MediaRowCodec::encode),
        preloadedItems = items,
    )

    init {
        val context = activity
        setContentView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GramophoneTheme(
                    pureDark = pureDark(context)
                ) {
                    AddToQueuePicker(
                        activity = activity,
                        songRows = songRows,
                        preloadedItems = preloadedItems,
                        onDismiss = { dismiss() },
                    )
                }
            }
        })
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }
}

@Composable
private fun AddToQueuePicker(
    activity: MainActivity,
    songRows: List<String>,
    preloadedItems: List<MediaItem>? = null,
    onDismiss: () -> Unit,
) {
    val repository = remember { activity.gramophoneApplication.queueRepository }
    val queues by repository.queues.collectAsState(initial = emptyList())
    val activeQueue by repository.lastActiveQueue.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showCreateDialog by remember { mutableStateOf(false) }

    fun choose(queue: Queue) {
        if (queue.id == activeQueue?.id) {
            // The player is the mutation seam for the active queue; the service mirrors it back.
            val player = activity.getPlayer()
            val items = preloadedItems ?: songRows.mapNotNull(MediaRowCodec::decode)
            if (items.isNotEmpty()) {
                // An ended, play-when-ready queue would otherwise resume when a row is appended.
                if (player?.playbackState == Player.STATE_ENDED) player.pause()
                player?.addMediaItems(items)
            }
        } else {
            // Dismiss only after the append: dismissing disposes this composition and its scope.
            scope.launch {
                repository.mutate(queue.id) { it.withAppendedRows(songRows) }
                onDismiss()
            }
            return
        }
        onDismiss()
    }

    fun create(title: String) {
        scope.launch {
            repository.create(source = null, rows = songRows, title = title)
            onDismiss()
        }
    }

    if (showCreateDialog) {
        val nextNumber = (queues.size + 1).toLong()
        val defaultName = stringResource(R.string.new_queue_n, nextNumber)
        CreateQueueDialog(
            initialTitle = defaultName,
            onDismiss = { showCreateDialog = false },
            onConfirm = { title ->
                showCreateDialog = false
                create(title)
            },
        )
    }

    val interopConnection = rememberNestedScrollInteropConnection()
    val sheetScrollConnection = remember(listState, interopConnection) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // When the list can scroll backward, it must handle downward scrolls itself;
                // do not dispatch to the parent BottomSheetBehavior beforehand.
                val consumedY = SheetScrollRules.calculatePreScrollConsumedY(
                    availableY = available.y,
                    canScrollBackward = listState.canScrollBackward,
                )
                if (consumedY != 0f) {
                    return Offset.Zero
                }
                return interopConnection.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                return interopConnection.onPostScroll(consumed, available, source)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // When the list can scroll backward, downward flings belong to the list;
                // consume or avoid dispatching to the parent BottomSheetBehavior so it does not dismiss.
                val consumedY = SheetScrollRules.calculatePreFlingConsumedY(
                    velocityY = available.y,
                    canScrollBackward = listState.canScrollBackward,
                )
                if (consumedY != 0f) {
                    return Velocity.Zero
                }
                return interopConnection.onPreFling(available)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return interopConnection.onPostFling(consumed, available)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .nestedScroll(sheetScrollConnection),
    ) {
        Text(
            text = stringResource(R.string.add_to_queue),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            QueueListContent(
                queues = queues,
                activeQueueId = activeQueue?.id,
                isPlaying = activity.getPlayer()?.isPlaying == true,
                onQueueClick = ::choose,
                listState = listState,
            )
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_queue))
            }
        }
    }
}

@Composable
private fun CreateQueueDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    val trimmed = title.trim()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_queue)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.focusRequester(focusRequester),
                singleLine = true,
                label = { Text(stringResource(R.string.queue_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
