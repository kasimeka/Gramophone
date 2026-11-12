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

/**
 * Rules for durable queue deduplication.
 * When playing a queue from a library source, an unmodified replica of the same source
 * is reused/resumed rather than creating a duplicate copy.
 */
object QueueDeduplicationRules {

    /**
     * Finds an unmodified replica matching the given [source] among [existingQueues].
     * Matches queues where `!it.isModified && it.source?.kind == source.kind && it.source?.identifier == source.identifier`.
     * If multiple exist, returns the most recently active or created one (sorted by lastPlayedAt ?: createdAt).
     */
    fun findUnmodifiedReplica(existingQueues: List<Queue>, source: QueueSource?): Queue? {
        if (source == null) return null
        return existingQueues
            .filter {
                !it.isModified &&
                    it.source?.kind == source.kind &&
                    it.source.identifier == source.identifier
            }
            .maxByOrNull { it.lastPlayedAt ?: it.createdAt }
    }
}
