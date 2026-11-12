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

package org.akanework.gramophone.logic.ui

/**
 * Domain rules and logic for decor panel actions across library views (albums, artists, songs, search results).
 */
object DecorPanelRules {

    /**
     * Bulk media actions (add to queue, play all, shuffle all) are enabled when supported by the
     * provider view and not suppressed by a picker context.
     */
    fun canPerformMediaActions(
        isSupportedByProvider: Boolean,
        isSongPicker: Boolean,
    ): Boolean = isSupportedByProvider && !isSongPicker

    /**
     * Formats the playback queue title, wrapping with the shuffled template when required.
     */
    fun formatQueueTitle(
        title: String,
        shuffle: Boolean,
        applyShuffledTemplate: Boolean,
        shuffledTemplate: (String) -> String,
    ): String {
        return if (shuffle && applyShuffledTemplate) {
            shuffledTemplate(title)
        } else {
            title
        }
    }

    /**
     * Adding to queue is only valid when there are represented songs to add.
     */
    fun canAddToQueue(songCount: Int): Boolean = songCount > 0
}
