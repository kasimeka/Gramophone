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

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SorterDurationTest {
    private class DurationHelper : Sorter.Helper<Long>(
        setOf(Sorter.Type.ByDurationAscending, Sorter.Type.ByDurationDescending)
    ) {
        override fun getTitle(item: Long): String? = null
        override fun getId(item: Long): String = item.toString()
        override fun getCover(item: Long): Uri? = null
        override fun getDuration(item: Long): Long = item
    }

    private val durations = listOf(9_000L, 1_000L, 5_000L)

    @Test
    fun ascendingOrdersShortestFirst() {
        val (comparator, reverse) = Sorter(DurationHelper(), null)
            .getComparator(Sorter.Type.ByDurationAscending)

        assertFalse(reverse)
        assertEquals(listOf(1_000L, 5_000L, 9_000L), durations.sortedWith(comparator!!))
    }

    @Test
    fun descendingOrdersLongestFirst() {
        val (comparator, reverse) = Sorter(DurationHelper(), null)
            .getComparator(Sorter.Type.ByDurationDescending)

        assertFalse(reverse)
        assertEquals(listOf(9_000L, 5_000L, 1_000L), durations.sortedWith(comparator!!))
    }

    @Test
    fun durationTypesHaveEachOthersInverse() {
        assertEquals(Sorter.Type.ByDurationAscending, Sorter.Type.inverse(Sorter.Type.ByDurationDescending))
        assertEquals(Sorter.Type.ByDurationDescending, Sorter.Type.inverse(Sorter.Type.ByDurationAscending))
        assertTrue(Sorter(DurationHelper(), null).getSupportedTypes().contains(Sorter.Type.ByDurationAscending))
    }
}
