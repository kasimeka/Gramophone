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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * Inactive queues are edited through the same order editor as the player. These tests exercise
 * that path against the repository: what the target displays is what gets stored.
 */
class StoredQueueOrderTargetTest {
    private val rows = listOf("row-a", "row-b", "row-c", "row-d")

    private fun repositoryWithBookmark(row: Int): Pair<QueueRepository, Queue> = runBlocking {
        val repository = StoreQueueRepository(InMemoryQueueStore(), Dispatchers.Unconfined)
        val created = repository.create(source = null, rows = rows, now = 1L)
        val bookmarked = repository.mutate(created.id, now = 2L) {
            it.copy(bookmark = QueueBookmark(row = row, positionMs = 1_500L))
        }!!
        repository to bookmarked
    }

    @Test
    fun playNextOnAnInactiveQueueUsesTheBookmarkAsTheAnchor() = runBlocking {
        val (repository, queue) = repositoryWithBookmark(row = 1)

        val stored = repository.mutate(queue.id, now = 3L) { current ->
            val target = StoredQueueOrderTarget(current)
            val result = QueueOrderEditor.playNext(
                target.displayedOrder(),
                target.currentPosition(),
                selectedPosition = 3
            ) as QueueEditResult.Applied
            target.applyDisplayedOrder(result.order)
            target.queue
        }

        assertEquals(listOf("row-a", "row-b", "row-d", "row-c"), stored?.rows)
        assertEquals(1, stored?.bookmark?.row)
        assertEquals(1_500L, stored?.bookmark?.positionMs)
    }

    @Test
    fun removingTheBookmarkedRowClampsTheStoredBookmark() = runBlocking {
        val (repository, queue) = repositoryWithBookmark(row = 2)

        val stored = repository.mutate(queue.id, now = 3L) { current ->
            val target = StoredQueueOrderTarget(current)
            QueueOrderEditor.remove(target.displayedOrder(), 2)
            target.removeDisplayedRow(2)
            target.queue
        }

        assertEquals(listOf("row-a", "row-b", "row-d"), stored?.rows)
        assertEquals(2, stored?.bookmark?.row)
        assertEquals(1_500L, stored?.bookmark?.positionMs)
    }

    @Test
    fun shuffleKeepsTheBookmarkRowFirstAndPersistsOneOrder() = runBlocking {
        val (repository, queue) = repositoryWithBookmark(row = 2)

        val stored = repository.mutate(queue.id, now = 3L) { current ->
            val target = StoredQueueOrderTarget(current)
            val result = QueueOrderEditor.shuffleEverything(
                target.displayedOrder(),
                target.currentPosition(),
                Random(4)
            ) as QueueEditResult.Applied
            target.applyDisplayedOrder(result.order)
            target.queue
        }

        assertEquals("row-c", stored?.rows?.first())
        assertEquals(0, stored?.bookmark?.row)
        assertEquals(rows.toSet(), stored?.rows?.toSet())
    }

    @Test
    fun draggedMoveFollowsTheRowIdentity() = runBlocking {
        val (repository, queue) = repositoryWithBookmark(row = 1)

        val stored = repository.mutate(queue.id, now = 3L) { current ->
            val target = StoredQueueOrderTarget(current)
            val result = QueueOrderEditor.move(
                target.displayedOrder(),
                from = 1,
                to = 3
            ) as QueueEditResult.Applied
            target.applyDisplayedOrder(result.order)
            target.queue
        }

        assertEquals(listOf("row-a", "row-c", "row-d", "row-b"), stored?.rows)
        // row-b was the bookmark and keeps its identity, so it moved to the end with its row.
        assertEquals(3, stored?.bookmark?.row)
    }

    @Test
    fun editingOneInactiveQueueLeavesTheOthersAlone() = runBlocking {
        val repository = StoreQueueRepository(InMemoryQueueStore(), Dispatchers.Unconfined)
        val edited = repository.create(source = null, rows = rows, now = 1L)
        val untouched = repository.create(source = null, rows = rows, now = 2L)
        repository.mutate(untouched.id, now = 3L) {
            it.copy(bookmark = QueueBookmark(row = 3, positionMs = 9_000L))
        }

        repository.mutate(edited.id, now = 4L) { current ->
            val target = StoredQueueOrderTarget(current)
            target.removeDisplayedRow(0)
            target.queue
        }

        val other = repository.queue(untouched.id).first()
        assertEquals(rows, other?.rows)
        assertEquals(QueueBookmark(row = 3, positionMs = 9_000L), other?.bookmark)
    }
}
