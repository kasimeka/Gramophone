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

package org.akanework.gramophone.ui.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.ui.SongMenuContext
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId

/**
 * Song row used in queue detail, reusing the regular song row's content, menu, and actions with
 * queue-specific affordances (drag handle/reorder and "Remove from queue").
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun QueueSongRow(
    item: MediaItem?,
    isDragging: Boolean,
    dragTranslation: Float,
    isCurrent: Boolean,
    isPlaying: Boolean,
    canPlayNext: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onDetails: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemoveFromQueue()
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isDragging,
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragTranslation },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.remove_from_queue),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { currentOnDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            currentOnDrag(amount.y)
                        },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragCancel() },
                    )
                }
                .clickable(onClick = onPlay)
                .padding(start = 24.dp, end = 6.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item?.mediaMetadata?.artworkUri)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_default_cover)
                    .error(R.drawable.ic_default_cover)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
            if (isCurrent) {
                NowPlayingInlineIcon(isPlaying = isPlaying)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = nowPlayingTextStartPadding(isCurrent)),
            ) {
                Text(
                    text = item?.mediaMetadata?.title?.toString()
                        ?: context.getString(R.string.unknown_title),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = nowPlayingTitleColor(isCurrent),
                )
                Text(
                    text = item?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.play_next)) },
                        enabled = canPlayNext,
                        onClick = {
                            menuOpen = false
                            onPlayNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_to_queue)) },
                        onClick = {
                            menuOpen = false
                            onAddToQueue()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.go_to_album)) },
                        enabled = org.akanework.gramophone.ui.SongMenuLogic.canGoToAlbum(item),
                        onClick = {
                            menuOpen = false
                            onGoToAlbum()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.go_to_artist)) },
                        enabled = org.akanework.gramophone.ui.SongMenuLogic.canGoToArtist(item),
                        onClick = {
                            menuOpen = false
                            onGoToArtist()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.details)) },
                        enabled = item != null,
                        onClick = {
                            menuOpen = false
                            onDetails()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        enabled = org.akanework.gramophone.ui.SongMenuLogic.canDelete(item),
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        enabled = org.akanework.gramophone.ui.SongMenuLogic.canShare(item),
                        onClick = {
                            menuOpen = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_from_queue)) },
                        onClick = {
                            menuOpen = false
                            onRemoveFromQueue()
                        },
                    )
                }
            }
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(24.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { currentOnDragStart() },
                            onDrag = { change, amount ->
                                change.consume()
                                currentOnDrag(amount.y)
                            },
                            onDragEnd = { currentOnDragEnd() },
                            onDragCancel = { currentOnDragCancel() },
                        )
                    },
            )
        }
    }
}
