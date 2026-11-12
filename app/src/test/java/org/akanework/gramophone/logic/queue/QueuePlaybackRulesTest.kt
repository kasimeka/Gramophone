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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueuePlaybackRulesTest {
    private fun state(
        rowCount: Int,
        bookmark: QueueBookmark = QueueBookmark(),
        hasBeenPlayed: Boolean = false,
        isActive: Boolean = false,
        isPlaying: Boolean = false,
    ) = QueuePlaybackState(rowCount, bookmark, hasBeenPlayed, isActive, isPlaying)

    @Test
    fun libraryPlayNextNeedsAnActiveQueue() {
        assertFalse(QueuePlaybackRules.libraryPlayNextAvailable(null))
        assertTrue(
            QueuePlaybackRules.libraryPlayNextAvailable(
                Queue.create("Active", null, rows = listOf("a"), now = 1L)
            )
        )
    }

    @Test
    fun emptyQueuePlayButtonIsDisabled() {
        assertEquals(
            QueuePlaybackAction.Disabled,
            QueuePlaybackRules.queuePlayButton(state(rowCount = 0))
        )
        assertEquals(
            QueuePlaybackAction.Disabled,
            QueuePlaybackRules.playerPlayButton(state(rowCount = 0))
        )
        assertNull(QueuePlaybackRules.libraryPlayStartRow(0))
    }

    @Test
    fun neverPlayedQueueStartsAtTheFirstRow() {
        assertEquals(
            QueuePlaybackAction.Play(row = 0, positionMs = 0L),
            QueuePlaybackRules.queuePlayButton(
                state(rowCount = 5, bookmark = QueueBookmark(row = 3, positionMs = 9_000L))
            )
        )
    }

    @Test
    fun partiallyPlayedQueueResumesFromItsBookmark() {
        assertEquals(
            QueuePlaybackAction.Play(row = 2, positionMs = 42_000L),
            QueuePlaybackRules.queuePlayButton(
                state(
                    rowCount = 5,
                    bookmark = QueueBookmark(row = 2, positionMs = 42_000L),
                    hasBeenPlayed = true,
                )
            )
        )
    }

    @Test
    fun endedQueueRestartsAtTheFirstRow() {
        assertEquals(
            QueuePlaybackAction.Play(row = 0, positionMs = 0L),
            QueuePlaybackRules.queuePlayButton(
                state(
                    rowCount = 5,
                    bookmark = QueueBookmark(row = 4, positionMs = 180_000L, ended = true),
                    hasBeenPlayed = true,
                )
            )
        )
    }

    @Test
    fun activePlayingQueuePauses() {
        assertEquals(
            QueuePlaybackAction.Pause,
            QueuePlaybackRules.queuePlayButton(
                state(
                    rowCount = 5,
                    bookmark = QueueBookmark(row = 2, positionMs = 42_000L, ended = true),
                    hasBeenPlayed = true,
                    isActive = true,
                    isPlaying = true,
                )
            )
        )
    }

    @Test
    fun activePausedQueueResumesFromItsBookmark() {
        assertEquals(
            QueuePlaybackAction.Play(row = 2, positionMs = 42_000L),
            QueuePlaybackRules.queuePlayButton(
                state(
                    rowCount = 5,
                    bookmark = QueueBookmark(row = 2, positionMs = 42_000L),
                    hasBeenPlayed = true,
                    isActive = true,
                )
            )
        )
    }

    @Test
    fun playerButtonPausesWhilePlaying() {
        assertEquals(
            QueuePlaybackAction.Pause,
            QueuePlaybackRules.playerPlayButton(state(rowCount = 3, isPlaying = true))
        )
    }

    @Test
    fun playerButtonResumesAtTheLastTimestamp() {
        assertEquals(
            QueuePlaybackAction.Play(row = 1, positionMs = 12_000L),
            QueuePlaybackRules.playerPlayButton(
                state(
                    rowCount = 3,
                    bookmark = QueueBookmark(row = 1, positionMs = 12_000L),
                    hasBeenPlayed = true,
                )
            )
        )
    }

    @Test
    fun playerButtonRestartsAnEndedQueue() {
        assertEquals(
            QueuePlaybackAction.Play(row = 0, positionMs = 0L),
            QueuePlaybackRules.playerPlayButton(
                state(
                    rowCount = 3,
                    bookmark = QueueBookmark(row = 2, positionMs = 200_000L, ended = true),
                    hasBeenPlayed = true,
                )
            )
        )
    }

    @Test
    fun switchingQueuesSavesTheOutgoingBookmarkAndStartsTheIncomingOne() {
        val outgoing = state(
            rowCount = 3,
            bookmark = QueueBookmark(row = 99, positionMs = 5_000L),
            hasBeenPlayed = true,
            isActive = true,
            isPlaying = true,
        )
        val incoming = state(
            rowCount = 4,
            bookmark = QueueBookmark(row = 1, positionMs = 1_000L),
            hasBeenPlayed = true,
        )

        assertEquals(
            QueueSwitchPlan(
                savedBookmark = QueueBookmark(row = 2, positionMs = 5_000L),
                action = QueuePlaybackAction.Play(row = 1, positionMs = 1_000L),
            ),
            QueuePlaybackRules.switchQueue(outgoing, incoming)
        )
    }

    @Test
    fun switchingWithNothingActiveSavesNoBookmark() {
        val plan = QueuePlaybackRules.switchQueue(
            outgoing = null,
            incoming = state(rowCount = 2),
        )

        assertNull(plan.savedBookmark)
        assertEquals(QueuePlaybackAction.Play(row = 0, positionMs = 0L), plan.action)
    }

    @Test
    fun detailAnchorUsesCurrentRowForActiveAndBookmarkForInactive() {
        assertEquals(
            2,
            QueuePlaybackRules.detailAnchor(
                rowCount = 5,
                bookmark = QueueBookmark(row = 4),
                isActive = true,
                currentRow = 2,
            )
        )
        assertEquals(
            3,
            QueuePlaybackRules.detailAnchor(
                rowCount = 5,
                bookmark = QueueBookmark(row = 3),
                isActive = false,
                currentRow = 2,
            )
        )
        assertNull(
            QueuePlaybackRules.detailAnchor(
                rowCount = 5,
                bookmark = QueueBookmark(row = 3),
                isActive = true,
                currentRow = null,
            )
        )
        assertNull(
            QueuePlaybackRules.detailAnchor(
                rowCount = 0,
                bookmark = QueueBookmark(),
                isActive = false,
                currentRow = null,
            )
        )
    }

    @Test
    fun nowPlayingScrollTargetReturnsIndexOnlyForActiveQueueWithValidRow() {
        assertEquals(
            2,
            QueuePlaybackRules.nowPlayingScrollTarget(
                rowCount = 5,
                isActive = true,
                currentRow = 2,
            )
        )
        assertNull(
            QueuePlaybackRules.nowPlayingScrollTarget(
                rowCount = 5,
                isActive = false,
                currentRow = 2,
            )
        )
        assertNull(
            QueuePlaybackRules.nowPlayingScrollTarget(
                rowCount = 5,
                isActive = true,
                currentRow = 5,
            )
        )
        assertNull(
            QueuePlaybackRules.nowPlayingScrollTarget(
                rowCount = 5,
                isActive = true,
                currentRow = -1,
            )
        )
        assertNull(
            QueuePlaybackRules.nowPlayingScrollTarget(
                rowCount = 0,
                isActive = true,
                currentRow = 0,
            )
        )
        assertNull(
            QueuePlaybackRules.nowPlayingScrollTarget(
                rowCount = 5,
                isActive = true,
                currentRow = null,
            )
        )
    }

    @Test
    fun libraryPlayStartsAtTheFirstOrTappedRowWithoutTruncating() {
        assertEquals(0, QueuePlaybackRules.libraryPlayStartRow(5))
        assertEquals(3, QueuePlaybackRules.libraryPlayStartRow(5, tappedRow = 3))
        assertEquals(4, QueuePlaybackRules.libraryPlayStartRow(5, tappedRow = 99))
        assertEquals(0, QueuePlaybackRules.libraryPlayStartRow(5, tappedRow = -1))
    }

    @Test
    fun libraryShuffleAllUsesAFullPermutationWithNoAnchor() {
        val rows = QueuePlaybackRules.libraryShuffleAllOrder(5, Random(1))

        assertEquals(5, rows.size)
        assertEquals((0..4).toSet(), rows.toSet())
        assertNotEquals((0..4).toList(), rows)
    }

    @Test
    fun libraryShuffleAllCanStartAnywhere() {
        val firstRows = (0..49)
            .map { QueuePlaybackRules.libraryShuffleAllOrder(5, Random(it)).first() }
            .toSet()

        assertEquals((0..4).toSet(), firstRows)
    }

    @Test
    fun libraryShuffleAllOfOneRowIsTheSameRow() {
        assertEquals(listOf(0), QueuePlaybackRules.libraryShuffleAllOrder(1))
        assertEquals(emptyList<Int>(), QueuePlaybackRules.libraryShuffleAllOrder(0))
    }
}
