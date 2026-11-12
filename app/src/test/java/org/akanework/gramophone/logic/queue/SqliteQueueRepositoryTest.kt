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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SqliteQueueRepositoryTest : QueueRepositoryContractTest() {
    private var database: QueueDatabase? = null

    override fun createRepository(): QueueRepository {
        val db = QueueDatabase(RuntimeEnvironment.getApplication())
        database = db
        return StoreQueueRepository(QueueDao(db.writableDatabase), Dispatchers.Unconfined)
    }

    override fun closeRepository(repository: QueueRepository) {
        database?.close()
        database = null
    }

    @Test
    fun repositoryStartsEmptyAndIgnoresLegacyQueueAndLastPlayedData() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("LastPlayedManager", 0).edit()
            .putStringSet("last_played_lst", setOf("legacy-row"))
            .putString("last_played_grp", "legacy-row")
            .putInt("last_played_idx", 3)
            .commit()
        val name = "queue-legacy-${UUID.randomUUID()}.db"
        try {
            val database = QueueDatabase(context, name)
            val repository = StoreQueueRepository(
                QueueDao(database.writableDatabase),
                Dispatchers.Unconfined,
            )

            assertTrue(repository.queues.first().isEmpty())
            assertNull(repository.lastActiveQueue.first())
            database.close()
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun queuesSurviveReopeningTheDatabase() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "queue-round-trip-${UUID.randomUUID()}.db"
        try {
            val firstDatabase = QueueDatabase(context, name)
            val first = StoreQueueRepository(
                QueueDao(firstDatabase.writableDatabase),
                Dispatchers.Unconfined,
            )
            val created = first.create(
                QueueSource(QueueSourceKind.ALBUM, "album-9", "Kid A"),
                listOf("row-a", "row-b", "row-c"),
                now = 10L,
            )
            first.mutate(created.id, now = 20L) {
                it.reordered(listOf(2, 0, 1), now = 20L).copy(
                    bookmark = QueueBookmark(row = 1, positionMs = 4_200L),
                    repeatMode = QueueRepeatMode.ALL,
                )
            }
            first.mutate(created.id, now = 25L) { it.copy(lastPlayedAt = 21L) }
            first.setActiveQueue(created.id)
            val numbered = first.create(source = null, now = 5L)
            assertEquals("new queue #1", numbered.title)
            firstDatabase.close()

            val secondDatabase = QueueDatabase(context, name)
            val second = StoreQueueRepository(
                QueueDao(secondDatabase.writableDatabase),
                Dispatchers.Unconfined,
            )
            val restored = second.queue(created.id).first()

            assertEquals(listOf("row-c", "row-a", "row-b"), restored?.rows)
            assertEquals(QueueBookmark(row = 1, positionMs = 4_200L), restored?.bookmark)
            assertEquals(QueueRepeatMode.ALL, restored?.repeatMode)
            assertEquals(created.id, second.lastActiveQueue.first()?.id)
            assertEquals("new queue #2", second.create(source = null, now = 30L).title)
            secondDatabase.close()
        } finally {
            context.deleteDatabase(name)
        }
    }
}
