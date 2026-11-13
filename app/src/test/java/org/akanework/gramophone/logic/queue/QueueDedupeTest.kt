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

class QueueDedupeTest {
    @Test
    fun laterRepeatsAreReportedAndTheFirstOccurrenceIsKept() {
        assertEquals(listOf(2, 4), QueueDedupe.duplicatePositions(listOf("a", "b", "a", "c", "b")))
    }

    @Test
    fun uniqueRowsHaveNoDuplicates() {
        assertEquals(emptyList<Int>(), QueueDedupe.duplicatePositions(listOf("a", "b", "c")))
    }

    @Test
    fun rowsWithoutAKeyAreNeverDuplicates() {
        assertEquals(listOf(3), QueueDedupe.duplicatePositions(listOf(null, null, "a", "a")))
    }

    @Test
    fun emptyQueueHasNoDuplicates() {
        assertEquals(emptyList<Int>(), QueueDedupe.duplicatePositions(emptyList()))
    }
}
