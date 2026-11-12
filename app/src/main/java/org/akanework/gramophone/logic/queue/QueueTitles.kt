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
 * Generated queue titles. Source titles derive from the source and increment on collision;
 * numbered grab-bags derive from a persisted monotonic counter and never reuse a number.
 */
object QueueTitles {
    /** Counter value for the first numbered queue; the store persists this and advances it. */
    const val FIRST_NUMBERED_COUNTER = 1L

    private const val NUMBERED_PREFIX = "new queue #"

    /** Title for a source-derived queue, using the smallest free integer on collision. */
    fun sourceTitle(source: QueueSource, existingTitles: Collection<String>): String {
        require(!source.kind.isNumbered) {
            "Source ${source.kind} is a numbered grab-bag, not a source-derived title"
        }
        val base = requireNotNull(source.baseTitle) {
            "Source ${source.kind} has no display name"
        }
        return uniqueTitle(base, existingTitles)
    }

    /**
     * Return [base] when it is free, otherwise append the smallest integer that is free:
     * `Abbey Road`, `Abbey Road 2`, `Abbey Road 3`. The counter is computed from existing titles,
     * so renaming a queue frees its old base title.
     */
    fun uniqueTitle(base: String, existingTitles: Collection<String>): String {
        if (base !in existingTitles) return base
        var suffix = 2
        while ("$base $suffix" in existingTitles) suffix++
        return "$base $suffix"
    }

    /**
     * Resolve a listener-entered title. Blank titles are rejected; collisions follow the same
     * smallest-free-suffix rule as generated titles. [existingTitles] must not include the queue
     * being renamed, so a rename can keep or reuse its own name.
     */
    fun renameTitle(proposed: String, existingTitles: Collection<String>): String? {
        val title = proposed.trim()
        if (title.isEmpty()) return null
        return uniqueTitle(title, existingTitles)
    }

    /**
     * Take the next numbered title and return the counter to persist afterwards. Because the
     * counter only moves forward, deleting a numbered queue leaves a gap instead of reusing a
     * number.
     */
    fun nextNumberedTitle(counter: Long): NumberedQueueTitle {
        require(counter >= 1) { "numbered queue counter must be positive, was $counter" }
        return NumberedQueueTitle(
            title = "$NUMBERED_PREFIX$counter",
            nextCounter = counter + 1,
        )
    }
}

data class NumberedQueueTitle(
    val title: String,
    val nextCounter: Long,
)
