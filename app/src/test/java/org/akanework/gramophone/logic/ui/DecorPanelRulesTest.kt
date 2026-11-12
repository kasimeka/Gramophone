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

package org.akanework.gramophone.logic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorPanelRulesTest {

    @Test
    fun mediaActionsEnabledOnlyWhenSupportedAndNotSongPicker() {
        assertTrue(
            DecorPanelRules.canPerformMediaActions(
                isSupportedByProvider = true,
                isSongPicker = false,
            )
        )
        assertFalse(
            DecorPanelRules.canPerformMediaActions(
                isSupportedByProvider = true,
                isSongPicker = true,
            )
        )
        assertFalse(
            DecorPanelRules.canPerformMediaActions(
                isSupportedByProvider = false,
                isSongPicker = false,
            )
        )
        assertFalse(
            DecorPanelRules.canPerformMediaActions(
                isSupportedByProvider = false,
                isSongPicker = true,
            )
        )
    }

    @Test
    fun titleFormattingForPlayback() {
        val originalTitle = "Abbey Road"

        // Regular play leaves title unchanged
        assertEquals(
            originalTitle,
            DecorPanelRules.formatQueueTitle(
                title = originalTitle,
                shuffle = false,
                applyShuffledTemplate = false,
                shuffledTemplate = { "$it (shuffled)" },
            )
        )
        assertEquals(
            originalTitle,
            DecorPanelRules.formatQueueTitle(
                title = originalTitle,
                shuffle = false,
                applyShuffledTemplate = true,
                shuffledTemplate = { "$it (shuffled)" },
            )
        )

        // Shuffle play with template formats title
        assertEquals(
            "Abbey Road (shuffled)",
            DecorPanelRules.formatQueueTitle(
                title = originalTitle,
                shuffle = true,
                applyShuffledTemplate = true,
                shuffledTemplate = { "$it (shuffled)" },
            )
        )

        // Shuffle play without template keeps original title
        assertEquals(
            originalTitle,
            DecorPanelRules.formatQueueTitle(
                title = originalTitle,
                shuffle = true,
                applyShuffledTemplate = false,
                shuffledTemplate = { "$it (shuffled)" },
            )
        )
    }

    @Test
    fun addToQueueRequiresNonEmptySongList() {
        assertTrue(DecorPanelRules.canAddToQueue(songCount = 1))
        assertTrue(DecorPanelRules.canAddToQueue(songCount = 42))
        assertFalse(DecorPanelRules.canAddToQueue(songCount = 0))
        assertFalse(DecorPanelRules.canAddToQueue(songCount = -1))
    }
}
