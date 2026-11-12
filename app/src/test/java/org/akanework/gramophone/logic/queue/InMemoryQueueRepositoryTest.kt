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

class InMemoryQueueRepositoryTest : QueueRepositoryContractTest() {
    override fun createRepository(): QueueRepository =
        StoreQueueRepository(InMemoryQueueStore(), Dispatchers.Unconfined)

    @Test
    fun repositoriesStartEmptyAndDoNotShareState() = runBlocking {
        val first = createRepository()
        val second = createRepository()

        first.create(source = null, now = 1L)

        assertEquals(1, first.queues.first().size)
        assertEquals(0, second.queues.first().size)
    }
}
