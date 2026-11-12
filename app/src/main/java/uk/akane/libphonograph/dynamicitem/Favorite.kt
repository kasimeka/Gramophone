/*
 *     Copyright (C) 2025 The Gramophone authors
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

package uk.akane.libphonograph.dynamicitem

import androidx.media3.common.MediaItem
import uk.akane.libphonograph.items.Item

/**
 * The user's Favourites library collection. It is a dynamic library item, not a playlist entity.
 */
class Favorite(
    override val id: Long?,
    override val songList: List<MediaItem>,
) : Item {
    override val title: String? = null

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + songList.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Favorite
        if (id != other.id) return false
        if (songList != other.songList) return false
        return true
    }
}