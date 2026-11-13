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

package org.akanework.gramophone.logic.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorNameTest {
    @Test
    fun favouritesSystemQueueKeepsTheFamiliarMirrorName() {
        val favourites = SystemQueue(
            id = SystemQueueKind.FAVOURITES.id,
            title = "Favourites",
            kind = SystemQueueKind.FAVOURITES,
            rows = emptyList(),
            updatedAt = 1L,
            exportFileName = null,
        )

        assertEquals("gramophone_favourite.m3u8", QueueExport.mirrorName(favourites))
    }

    @Test
    fun durableQueuesKeepTheSlugAndIdName() {
        val queue = Queue.create(title = "Road Trip", source = null, rows = listOf("a"), now = 1L)

        assertEquals("road_trip_${queue.id}.m3u8", QueueExport.mirrorName(queue))
    }

    @Test
    fun durableQueueWithFavouritesSourceAlsoGetsTheFixedName() {
        val queue = Queue.create(
            title = "Favourites",
            source = QueueSource(kind = QueueSourceKind.FAVOURITES),
            rows = listOf("a"),
            now = 1L,
        )

        assertEquals("gramophone_favourite.m3u8", QueueExport.mirrorName(queue))
    }
}
