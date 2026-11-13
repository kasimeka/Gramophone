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
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class NextQueueAfterTest {
    private val first = queue("first")
    private val second = queue("second")
    private val third = queue("third")
    private val ordered = listOf(first, second, third)

    @Test
    fun theQueueListedAfterTheActiveOneIsNext() {
        assertEquals(second, QueuePlaybackRules.nextQueueAfter(ordered, first.id))
        assertEquals(third, QueuePlaybackRules.nextQueueAfter(ordered, second.id))
    }

    @Test
    fun theLastQueueHasNoNextQueue() {
        assertNull(QueuePlaybackRules.nextQueueAfter(ordered, third.id))
    }

    @Test
    fun anUnlistedQueueHasNoNextQueue() {
        assertNull(QueuePlaybackRules.nextQueueAfter(ordered, UUID.randomUUID()))
    }

    private fun queue(title: String) = Queue.create(
        title = title,
        source = null,
        rows = listOf("a"),
        now = 1L,
    )
}
