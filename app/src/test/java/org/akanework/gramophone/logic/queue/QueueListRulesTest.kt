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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class QueueListRulesTest {
    private val firstId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val secondId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val thirdId = UUID.fromString("00000000-0000-0000-0000-000000000003")

    private fun queue(
        id: UUID,
        createdAt: Long,
        lastPlayedAt: Long? = null,
        rows: List<String> = listOf("row-a"),
        bookmark: QueueBookmark = QueueBookmark(),
        title: String = "queue",
        manualOrder: Long = createdAt,
    ) = Queue(
        id = id,
        title = title,
        source = null,
        rows = rows,
        bookmark = bookmark,
        createdAt = createdAt,
        lastPlayedAt = lastPlayedAt,
        manualOrder = manualOrder,
    )

    @Test
    fun emptyCollectionHasNoRows() {
        assertTrue(QueueListRules.ordered(emptyList()).isEmpty())
    }

    @Test
    fun queuesFollowManualOrder() {
        val first = queue(firstId, createdAt = 100L, manualOrder = 3L)
        val second = queue(secondId, createdAt = 200L, manualOrder = 1L)
        val third = queue(thirdId, createdAt = 300L, manualOrder = 2L)

        assertEquals(
            listOf(secondId, thirdId, firstId),
            QueueListRules.ordered(listOf(first, second, third)).map { it.id },
        )
    }

    @Test
    fun playingOrRecentActivityDoesNotChangeManualOrder() {
        val active = queue(firstId, createdAt = 10L, manualOrder = 1L)
        val idle = queue(secondId, createdAt = 20L, manualOrder = 2L)
        val recentlyPlayed = queue(thirdId, createdAt = 30L, lastPlayedAt = 400L, manualOrder = 3L)

        assertEquals(
            listOf(firstId, secondId, thirdId),
            QueueListRules.ordered(listOf(recentlyPlayed, idle, active)).map { it.id },
        )
    }

    @Test
    fun emptyQueueIsANormalEmptyRow() {
        val row = QueueListRules.row(
            queue(firstId, createdAt = 1L, rows = emptyList()),
            isActive = false,
            isPlaying = false,
        )

        assertEquals(QueueRowState.EMPTY, row.state)
        assertEquals(0, row.songCount)
        assertNull(row.bookmarkRow)
        assertNull(row.bookmarkPositionText)
    }

    @Test
    fun activePlayingQueueReportsPlayingAndItsBookmark() {
        val row = QueueListRules.row(
            queue(
                firstId,
                createdAt = 1L,
                rows = listOf("a", "b", "c"),
                lastPlayedAt = 2L,
                bookmark = QueueBookmark(row = 1, positionMs = 83_000L),
            ),
            isActive = true,
            isPlaying = true,
        )

        assertEquals(QueueRowState.PLAYING, row.state)
        assertEquals(3, row.songCount)
        assertEquals(2, row.bookmarkRow)
        assertEquals("1:23", row.bookmarkPositionText)
    }

    @Test
    fun activePausedQueueReportsPausedRatherThanPlaying() {
        val row = QueueListRules.row(
            queue(
                firstId,
                createdAt = 1L,
                lastPlayedAt = 2L,
                rows = listOf("a", "b", "c"),
                bookmark = QueueBookmark(row = 1, positionMs = 83_000L),
            ),
            isActive = true,
            isPlaying = false,
        )

        assertEquals(QueueRowState.PAUSED, row.state)
        assertEquals(2, row.bookmarkRow)
        assertEquals("1:23", row.bookmarkPositionText)
    }

    @Test
    fun inactivePlayedQueueReportsPausedAtItsBookmark() {
        val row = QueueListRules.row(
            queue(
                firstId,
                createdAt = 1L,
                lastPlayedAt = 2L,
                bookmark = QueueBookmark(row = 0, positionMs = 5_000L),
            ),
            isActive = false,
            isPlaying = false,
        )

        assertEquals(QueueRowState.PAUSED, row.state)
        assertEquals("0:05", row.bookmarkPositionText)
    }

    @Test
    fun neverPlayedQueueHasNoBookmarkPosition() {
        val row = QueueListRules.row(
            queue(firstId, createdAt = 1L),
            isActive = false,
            isPlaying = false,
        )

        assertEquals(QueueRowState.NEVER_PLAYED, row.state)
        assertNull(row.bookmarkRow)
        assertNull(row.bookmarkPositionText)
    }

    @Test
    fun endedQueueReportsEndedAndHasNoResumableBookmark() {
        val row = QueueListRules.row(
            queue(
                firstId,
                createdAt = 1L,
                lastPlayedAt = 2L,
                bookmark = QueueBookmark(row = 0, positionMs = 120_000L, ended = true),
            ),
            isActive = false,
            isPlaying = false,
        )

        assertEquals(QueueRowState.ENDED, row.state)
        assertNull(row.bookmarkPositionText)
    }

    @Test
    fun positionFormattingHandlesMinutesAndHours() {
        assertEquals(
            "1:02:03",
            QueueListRules.row(
                queue(
                    firstId,
                    createdAt = 1L,
                    lastPlayedAt = 2L,
                    bookmark = QueueBookmark(row = 0, positionMs = 3_723_000L),
                ),
                isActive = false,
                isPlaying = false,
            ).bookmarkPositionText,
        )
    }
}
