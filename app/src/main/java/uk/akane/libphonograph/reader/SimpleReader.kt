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

package uk.akane.libphonograph.reader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Item

object SimpleReader {
    fun readFromMediaStore(
        context: Context,
        minSongLengthSeconds: Long = 0,
        blackListSet: Set<String> = setOf(),
        whiteListSet: Set<String> = setOf(),
        shouldUseEnhancedCoverReading: Boolean? = false, // null means load if permission is granted
        recentlyAddedFilterSecond: Long? = 1_209_600, // null means don't generate recently added
        favoriteSongIds: Set<String> = emptySet(),
    ): SimpleReaderResult {
        val result = runBlocking {
            withContext(Dispatchers.Default) {
                Reader.readFromMediaStore(
                    context, minSongLengthSeconds, blackListSet, whiteListSet,
                    shouldUseEnhancedCoverReading,
                    shouldLoadIdMap = false,
                    shouldLoadPathMap = false
                )
            }
        }
        val dynamicItems = buildList<Item> {
            if (recentlyAddedFilterSecond != null) {
                add(
                    RecentlyAdded(
                        (System.currentTimeMillis() / 1000L) - recentlyAddedFilterSecond,
                        result.songList
                    )
                )
            }
            add(
                Favorite(
                    null,
                    favoriteSongIds.mapNotNull { id -> result.songList.find { it.mediaId == id } }
                )
            )
        }
        return SimpleReaderResult(
            result.songList,
            result.albumList!!,
            result.albumArtistList!!,
            result.artistList!!,
            result.genreList!!,
            result.dateList!!,
            dynamicItems,
            result.folderStructure!!,
            result.shallowFolder!!,
            result.folders!!,
            result.foldersForWhitelist!!
        )
    }
}