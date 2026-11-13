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

import java.util.UUID

/** Lists the app owns. Each kind has one fixed id, so it is found by kind, not by title. */
enum class SystemQueueKind(val id: UUID, val title: String, val mirrorName: String) {
    FAVOURITES(
        id = UUID.fromString("5f6c1f0e-0b7a-4d21-9d0a-0f4e2a7e1a01"),
        title = "Favourites",
        mirrorName = QueueExport.FAVOURITES_FILE_NAME,
    ),
}

/** A system list kept in its own table, outside the user's queues. Mirrored like any other queue. */
data class SystemQueue(
    override val id: UUID,
    override val title: String,
    val kind: SystemQueueKind,
    override val rows: List<String>,
    override val updatedAt: Long,
    override val exportFileName: String?,
) : MirrorableQueue {
    override val fixedMirrorName: String?
        get() = kind.mirrorName
}
