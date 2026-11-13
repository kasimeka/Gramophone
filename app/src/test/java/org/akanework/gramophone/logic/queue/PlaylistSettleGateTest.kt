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

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaylistSettleGateTest {
    @Test
    fun withNoChangesAwaitReturnsImmediately() = runBlocking {
        val gate = PlaylistSettleGate(timeoutMs = 10_000L)

        withTimeout(1_000L) { gate.awaitSettled() }
    }

    @Test
    fun awaitWaitsUntilTheTimelineEventArrives() = runBlocking {
        val gate = PlaylistSettleGate(timeoutMs = 10_000L)
        gate.mutated()
        val waiter = async { gate.awaitSettled() }

        delay(20L)
        assertFalse(waiter.isCompleted)
        gate.timelineApplied()

        withTimeout(1_000L) { waiter.await() }
    }

    @Test
    fun twoChangesNeedTwoTimelineEvents() = runBlocking {
        val gate = PlaylistSettleGate(timeoutMs = 10_000L)
        gate.mutated()
        gate.mutated()
        val waiter = async { gate.awaitSettled() }

        gate.timelineApplied()
        delay(20L)
        assertFalse(waiter.isCompleted)
        gate.timelineApplied()

        withTimeout(1_000L) { waiter.await() }
    }

    @Test
    fun aChangeWithoutAnEventIsReleasedByTheTimeout() = runBlocking {
        val gate = PlaylistSettleGate(timeoutMs = 50L)
        gate.mutated()

        withTimeout(1_000L) { gate.awaitSettled() }
        withTimeout(1_000L) { gate.awaitSettled() }
    }
}
