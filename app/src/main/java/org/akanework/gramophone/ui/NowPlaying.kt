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

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.media3.common.Player
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.adapters.BaseAdapter

/**
 * Shared "now playing" presentation contract for song rows.
 *
 * Current-item identity is resolved by each surface: library/adapters use [androidx.media3.common.MediaItem.mediaId]
 * (including duplicate-aware position mapping), while queue detail uses active-queue + row index.
 * Both feed the same ([isCurrent], [isPlaying]) presentation inputs.
 */
data class NowPlayingState(
    val isCurrent: Boolean,
    val isPlaying: Boolean,
)

object NowPlayingPresentation {
    /** Icon size for the inline current-row indicator, matching queue rows. */
    const val ICON_DP = 18
    /** Gap between cover and inline icon, and between inline icon and title, when current. */
    const val GAP_DP = 8
    /** Leading inset from cover to title when not current, matching queue rows. */
    const val TEXT_START_DP = 16

    /** Canonical "effectively playing" rule shared by library and queue surfaces. */
    fun isEffectivelyPlaying(playWhenReady: Boolean, playbackState: Int): Boolean {
        return playWhenReady &&
            playbackState != Player.STATE_ENDED &&
            playbackState != Player.STATE_IDLE
    }

    @DrawableRes
    fun iconRes(isPlaying: Boolean): Int {
        return if (isPlaying) R.drawable.outline_volume_up_24 else R.drawable.ic_play_arrow
    }

    @StringRes
    fun contentDescriptionRes(): Int = R.string.now_playing

    @AttrRes
    fun currentColorAttr(): Int = androidx.appcompat.R.attr.colorPrimary

    @AttrRes
    fun defaultTitleColorAttr(): Int = com.google.android.material.R.attr.colorOnSurface
}

object NowPlayingViewBinder {
    /**
     * Apply the queue-row now-playing treatment to a library/View song row.
     *
     * Current: inline 18dp primary icon between cover and text, primary title, and
     * 8dp(icon gap)+18dp(icon)+8dp(text gap) leading inset before the text block.
     * Not current: no icon, default title color, and [NowPlayingPresentation.TEXT_START_DP] leading inset.
     */
    fun apply(
        holder: BaseAdapter.ViewHolder,
        state: NowPlayingState,
    ) {
        val context = holder.itemView.context
        val iconSizePx = dp(context, NowPlayingPresentation.ICON_DP)
        val gapPx = dp(context, NowPlayingPresentation.GAP_DP)
        val textStartPx = dp(context, NowPlayingPresentation.TEXT_START_DP)
        val primary = resolveAttrColor(context, NowPlayingPresentation.currentColorAttr())
        val onSurface = resolveAttrColor(context, NowPlayingPresentation.defaultTitleColorAttr())
        val onSurfaceVariant = resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)

        val icon = holder.nowPlaying
        val inConstraintRow = holder.title.layoutParams is ConstraintLayout.LayoutParams &&
            (holder.title.layoutParams as ConstraintLayout.LayoutParams).startToEnd != ConstraintLayout.LayoutParams.UNSET
        if (state.isCurrent) {
            icon.setImageDrawable(
                AppCompatResources.getDrawable(context, NowPlayingPresentation.iconRes(state.isPlaying))
            )
            icon.contentDescription = context.getString(NowPlayingPresentation.contentDescriptionRes())
            icon.setColorFilter(primary)
            icon.visibility = View.VISIBLE
            icon.updateLayoutParams<ViewGroup.LayoutParams> {
                width = iconSizePx
                height = iconSizePx
            }
            if (inConstraintRow) {
                setStartMargin(icon, gapPx)
                setStartToEnd(holder.title, icon.id, gapPx)
                setStartToEnd(holder.subTitle, icon.id, gapPx)
            } else {
                // Grid label: icon sits inline with title in a horizontal LinearLayout.
                setStartMargin(icon, 0)
                setEndMargin(icon, dp(context, 4))
            }
            holder.title.setTextColor(primary)
        } else {
            icon.visibility = View.GONE
            icon.setImageDrawable(null)
            icon.contentDescription = null
            icon.clearColorFilter()
            if (inConstraintRow) {
                icon.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = 0
                    height = 0
                }
                setStartMargin(icon, 0)
                setStartToEnd(holder.title, R.id.coverCardView, textStartPx)
                setStartToEnd(holder.subTitle, R.id.coverCardView, textStartPx)
            } else {
                icon.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = 0
                    height = 0
                }
                setStartMargin(icon, 0)
                setEndMargin(icon, 0)
            }
            holder.title.setTextColor(onSurface)
        }
        // Subtitle always keeps onSurfaceVariant (queue-row parity); reset defensively.
        holder.subTitle.setTextColor(onSurfaceVariant)
    }

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        ).toInt()

    private fun setStartMargin(view: View, marginPx: Int) {
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = marginPx
        }
    }

    private fun setEndMargin(view: View, marginPx: Int) {
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginEnd = marginPx
        }
    }

    private fun setStartToEnd(view: View, targetId: Int, marginPx: Int) {
        val lp = view.layoutParams
        if (lp is ConstraintLayout.LayoutParams) {
            lp.startToEnd = targetId
            lp.startToStart = ConstraintLayout.LayoutParams.UNSET
            lp.marginStart = marginPx
            view.layoutParams = lp
        } else if (lp is ViewGroup.MarginLayoutParams) {
            lp.marginStart = marginPx
            view.layoutParams = lp
        }
    }

    private fun resolveAttrColor(context: Context, @AttrRes attr: Int): Int {
        val tv = TypedValue()
        val theme = context.theme
        return if (theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
        } else {
            0
        }
    }
}
