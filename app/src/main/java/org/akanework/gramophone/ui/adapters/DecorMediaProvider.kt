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

package org.akanework.gramophone.ui.adapters

import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.Flow
import org.akanework.gramophone.logic.queue.QueueSource

/**
 * Interface implemented by adapters representing playable and queueable media items.
 * Allows decor panels to operate on represented songs uniformly without concrete type checks.
 */
interface DecorMediaProvider {
    val supportsMediaActions: Boolean get() = true
    fun getRepresentedSongs(): List<MediaItem>
    val queueTitleFlow: Flow<String>? get() = null
    val queueSourceFlow: Flow<QueueSource?>? get() = null
    val useShuffledTitleTemplate: Boolean get() = false
}
