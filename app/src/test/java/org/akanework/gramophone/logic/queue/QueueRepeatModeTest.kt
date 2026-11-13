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
    fun playNextQueueIsStoredAsItsOwnValueAndRunsAsRepeatOff() {
        assertEquals(QueueRepeatMode.PLAY_NEXT_QUEUE, QueueRepeatMode.fromValue(4))
        assertEquals(QueueRepeatMode.OFF.value, QueueRepeatMode.PLAY_NEXT_QUEUE.playerValue)
        assertTrue(QueueRepeatMode.PLAY_NEXT_QUEUE.playsNextQueue)
        assertFalse(QueueRepeatMode.PLAY_NEXT_QUEUE.shufflesOnWrap)
    }

    @Test
    fun playerModesKeepTheirValuesAndDoNotShuffleOrPlayNext() {
        for (mode in listOf(QueueRepeatMode.OFF, QueueRepeatMode.ONE, QueueRepeatMode.ALL)) {
            assertEquals(mode, QueueRepeatMode.fromValue(mode.value))
            assertEquals(mode.value, mode.playerValue)
            assertFalse(mode.shufflesOnWrap)
            assertFalse(mode.playsNextQueue)
        }
    }

    @Test
    fun repeatCycleVisitsEveryModeInPickerOrderAndReturnsToOff() {
        var mode = QueueRepeatMode.OFF
        val visited = mutableListOf<QueueRepeatMode>()
        repeat(QueueRepeatMode.entries.size) {
            visited += mode
            mode = mode.next
        }
        assertEquals(
            listOf(
                QueueRepeatMode.OFF,
                QueueRepeatMode.ONE,
                QueueRepeatMode.ALL,
                QueueRepeatMode.SHUFFLE_ALL,
                QueueRepeatMode.PLAY_NEXT_QUEUE,
            ),
            visited,
        )
        assertEquals(QueueRepeatMode.OFF, mode)
    }

    @Test
    fun unknownValuesFallBackToOff() {
        assertEquals(QueueRepeatMode.OFF, QueueRepeatMode.fromValue(42))
    }
}
