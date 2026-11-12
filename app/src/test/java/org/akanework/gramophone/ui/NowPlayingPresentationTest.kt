/*
 *     Copyright (C) 2026 Akane Foundation
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

package org.akanework.gramophone.ui

import androidx.media3.common.Player
import org.akanework.gramophone.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingPresentationTest {

    @Test
    fun effectivelyPlayingWhenPlayWhenReadyAndPlayingOrBuffering() {
        assertTrue(
            NowPlayingPresentation.isEffectivelyPlaying(
                playWhenReady = true,
                playbackState = Player.STATE_READY,
            )
        )
        assertTrue(
            NowPlayingPresentation.isEffectivelyPlaying(
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
            )
        )
    }

    @Test
    fun notEffectivelyPlayingWhenPausedOrEndedOrIdle() {
        assertFalse(
            NowPlayingPresentation.isEffectivelyPlaying(
                playWhenReady = false,
                playbackState = Player.STATE_READY,
            )
        )
        assertFalse(
            NowPlayingPresentation.isEffectivelyPlaying(
                playWhenReady = true,
                playbackState = Player.STATE_ENDED,
            )
        )
        assertFalse(
            NowPlayingPresentation.isEffectivelyPlaying(
                playWhenReady = true,
                playbackState = Player.STATE_IDLE,
            )
        )
    }

    @Test
    fun iconResSelectsVolumeWhenPlayingAndPlayArrowWhenPaused() {
        assertEquals(
            R.drawable.outline_volume_up_24,
            NowPlayingPresentation.iconRes(isPlaying = true),
        )
        assertEquals(
            R.drawable.ic_play_arrow,
            NowPlayingPresentation.iconRes(isPlaying = false),
        )
    }

    @Test
    fun presentationConstantsAndResources() {
        assertEquals(18, NowPlayingPresentation.ICON_DP)
        assertEquals(8, NowPlayingPresentation.GAP_DP)
        assertEquals(16, NowPlayingPresentation.TEXT_START_DP)
        assertEquals(R.string.now_playing, NowPlayingPresentation.contentDescriptionRes())
        assertEquals(androidx.appcompat.R.attr.colorPrimary, NowPlayingPresentation.currentColorAttr())
        assertEquals(com.google.android.material.R.attr.colorOnSurface, NowPlayingPresentation.defaultTitleColorAttr())
    }
}
