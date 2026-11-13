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

package org.akanework.gramophone.logic.utils

import androidx.media3.common.MediaItem

/** Whether the song's title, album, or artist contains [text], ignoring case. Shared by search screens. */
fun MediaItem.matchesSearch(text: String): Boolean {
    val metadata = mediaMetadata
    return metadata.title?.contains(text, true) == true ||
        metadata.albumTitle?.contains(text, true) == true ||
        metadata.artist?.contains(text, true) == true
}
