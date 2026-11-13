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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ExoPlayer applies playlist changes asynchronously, so reading the playlist right after changing
 * it can return the old order. Each direct change is counted here and cleared by the timeline
 * event it produces; readers wait for the count to drain. A change that never produces an event is
 * released by [timeoutMs] instead of blocking the queue forever.
 */
class PlaylistSettleGate(private val timeoutMs: Long = 1_000L) {
    private var pending = 0
    private var settled: CompletableDeferred<Unit>? = null

    /** Record one direct playlist change made on the player. */
    fun mutated() {
        pending++
        if (settled == null) settled = CompletableDeferred()
    }

    /** Record one playlist-changed timeline event from the player. */
    fun timelineApplied() {
        if (pending == 0) return
        pending--
        if (pending == 0) {
            settled?.complete(Unit)
            settled = null
        }
    }

    /** Suspend until every recorded change has been applied, or the timeout releases us. */
    suspend fun awaitSettled() {
        if (pending == 0) return
        val gate = settled ?: return
        withTimeoutOrNull(timeoutMs) { gate.await() }
        pending = 0
        settled = null
    }
}
