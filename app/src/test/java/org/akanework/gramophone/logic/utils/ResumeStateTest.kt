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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeStateTest {
    private fun roundTrip(state: ResumeState): ResumeState? {
        return ResumeState.fromStored(state.toStored())
    }

    @Test
    fun roundTripPreservesSavedState() {
        val state = ResumeState(
            rows = listOf("row-a", "row-b", "row-c"),
            currentRow = 1,
            positionMs = 42_000L,
            repeatMode = 2,
            ended = false,
            speed = 1.25f,
            pitch = 0.9f,
        )

        assertEquals(state, roundTrip(state))
    }

    @Test
    fun endedStateKeepsLastRowAndEndPosition() {
        val state = ResumeState(
            rows = listOf("row-a", "row-b"),
            currentRow = 1,
            positionMs = 180_000L,
            repeatMode = 0,
            ended = true,
            speed = 1f,
            pitch = 1f,
        )

        val restored = roundTrip(state)

        assertEquals(1, restored?.currentRow)
        assertEquals(180_000L, restored?.positionMs)
        assertTrue(restored?.ended == true)
    }

    @Test
    fun currentRowEqualToRowCountClampsToLastRow() {
        val state = ResumeState(
            rows = listOf("row-a", "row-b", "row-c"),
            currentRow = 3,
            positionMs = 0L,
            repeatMode = 0,
            ended = false,
            speed = 1f,
            pitch = 1f,
        )

        assertEquals(2, roundTrip(state)?.currentRow)
    }

    @Test
    fun outOfRangeCurrentRowClampsToValidRow() {
        val rows = listOf("row-a", "row-b", "row-c")
        val tooHigh = ResumeState(rows, 99, 0L, 0, false, 1f, 1f)
        val negative = ResumeState(rows, -1, 0L, 0, false, 1f, 1f)

        assertEquals(2, tooHigh.normalized()?.currentRow)
        assertEquals(0, negative.normalized()?.currentRow)
    }

    @Test
    fun emptySavedStateProducesNoResumeState() {
        val empty = StoredResumeState(
            rowSet = emptySet(),
            rowOrder = "",
            currentRow = 0,
            positionMs = 0L,
            repeatMode = 0,
            ended = false,
            speed = 1f,
            pitch = 1f,
        )

        assertNull(ResumeState.fromStored(empty))
    }

    @Test
    fun malformedSavedStateProducesNoResumeState() {
        val missingRow = StoredResumeState(
            rowSet = setOf("row-a"),
            rowOrder = "not-a-real-hash",
            currentRow = 0,
            positionMs = 0L,
            repeatMode = 0,
            ended = false,
            speed = 1f,
            pitch = 1f,
        )

        assertNull(ResumeState.fromStored(missingRow))
        assertNull(roundTrip(ResumeState(listOf(""), 0, 0L, 0, false, 1f, 1f)))
        assertNull(roundTrip(ResumeState(listOf("row-a", " "), 0, 0L, 0, false, 1f, 1f)))
    }
}
