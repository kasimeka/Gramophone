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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.ui.NowPlayingPresentation

/** Queue-row inline "now playing" indicator, shared contract with View song rows. */
@Composable
fun NowPlayingInlineIcon(isPlaying: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (isPlaying) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.PlayArrow,
        contentDescription = stringResource(NowPlayingPresentation.contentDescriptionRes()),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = NowPlayingPresentation.GAP_DP.dp)
            .size(NowPlayingPresentation.ICON_DP.dp),
    )
}

/** Title color for the shared song-row now-playing treatment. */
@Composable
fun nowPlayingTitleColor(isCurrent: Boolean) =
    if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

/** Leading inset for the text block following the cover in a song row. */
@Composable
fun nowPlayingTextStartPadding(isCurrent: Boolean) =
    if (isCurrent) NowPlayingPresentation.GAP_DP.dp else NowPlayingPresentation.TEXT_START_DP.dp
