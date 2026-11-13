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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRepeatModeTest {
    @Test
    fun shuffleAllIsStoredAsItsOwnValueAndRunsAsRepeatAll() {
        assertEquals(QueueRepeatMode.SHUFFLE_ALL, QueueRepeatMode.fromValue(3))
        assertEquals(QueueRepeatMode.ALL.value, QueueRepeatMode.SHUFFLE_ALL.playerValue)
        assertTrue(QueueRepeatMode.SHUFFLE_ALL.shufflesOnWrap)
    }

    @Test
    fun playerModesKeepTheirValuesAndDoNotShuffle() {
        for (mode in listOf(QueueRepeatMode.OFF, QueueRepeatMode.ONE, QueueRepeatMode.ALL)) {
            assertEquals(mode, QueueRepeatMode.fromValue(mode.value))
            assertEquals(mode.value, mode.playerValue)
            assertFalse(mode.shufflesOnWrap)
        }
    }

    @Test
    fun playerStateReadsBackAsShuffleOnlyForRepeatAllWithTheFlag() {
        assertEquals(QueueRepeatMode.SHUFFLE_ALL, QueueRepeatMode.fromPlayer(2, shuffleOnWrap = true))
        assertEquals(QueueRepeatMode.ALL, QueueRepeatMode.fromPlayer(2, shuffleOnWrap = false))
        assertEquals(QueueRepeatMode.ONE, QueueRepeatMode.fromPlayer(1, shuffleOnWrap = true))
        assertEquals(QueueRepeatMode.OFF, QueueRepeatMode.fromPlayer(0, shuffleOnWrap = true))
    }

    @Test
    fun unknownValuesFallBackToOff() {
        assertEquals(QueueRepeatMode.OFF, QueueRepeatMode.fromValue(42))
    }
}
