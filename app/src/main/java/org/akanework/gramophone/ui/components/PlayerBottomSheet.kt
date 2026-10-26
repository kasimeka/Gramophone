/*
 *     Copyright (C) 2024 Akane Foundation
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

package org.akanework.gramophone.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.Log
import androidx.media3.session.MediaController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.motion.MaterialBottomContainerBackHelper
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.ui.MiniPlayerPresence
import org.akanework.gramophone.logic.ui.MiniPlayerScreenPolicy
import org.akanework.gramophone.logic.ui.MyBottomSheetBehavior
import org.akanework.gramophone.ui.MainActivity


class PlayerBottomSheet private constructor(
    context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
) : FrameLayout(context, attributeSet, defStyleAttr, defStyleRes),
    Player.Listener, DefaultLifecycleObserver {
    constructor(context: Context, attributeSet: AttributeSet?)
            : this(context, attributeSet, 0, 0)

    companion object {
        private const val TAG = "PlayerBottomSheet"
    }

    private var standardBottomSheetBehavior: MyBottomSheetBehavior<FrameLayout>? = null

    @SuppressLint("RestrictedApi")
    private var lyricsBackHelper: MaterialBottomContainerBackHelper? = null
    private var bottomSheetBackCallback: OnBackPressedCallback? = null
    val fullPlayer: FullBottomSheet
    private val previewPlayer: View

    private val activity
        get() = context as MainActivity
    private val lifecycleOwner: LifecycleOwner
        get() = activity
    private val handler = Handler(Looper.getMainLooper())
    private val instance: MediaController?
        get() = activity.getPlayer()
    private val fragmentContainer: View by lazy { activity.findViewById(R.id.container)!! }
    private val lyricsFrame: View by lazy { activity.findViewById(R.id.lyric_framelayout)!! }
    private var lastActuallyVisible: Boolean? = null
    private var lastMeasuredHeight: Int? = null
    private var lastTabBarHeight: Int? = null
    private var lastInsets: Insets? = null

    /**
     * The current screen's policy for the mini player. Presence is derived from this policy and
     * the active queue, never from whatever sheet state was left behind.
     */
    var screenPolicy: MiniPlayerScreenPolicy = MiniPlayerScreenPolicy.ALLOWED
        set(value) {
            if (field != value) {
                field = value
                updatePresence()
                activity.updateTabBarPresence()
            }
        }

    /**
     * The full player occupies the whole dock, so the tab bar must not float on top of it. The
     * sheet is either a docked mini player (collapsed or hidden) or a playback surface.
     */
    val obscuresBottomBar: Boolean
        get() = when (standardBottomSheetBehavior?.state) {
            BottomSheetBehavior.STATE_EXPANDED,
            BottomSheetBehavior.STATE_HALF_EXPANDED,
            BottomSheetBehavior.STATE_DRAGGING,
            BottomSheetBehavior.STATE_SETTLING -> true

            else -> false
        }

    private val hasActiveQueue: Boolean
        get() = (instance?.mediaItemCount ?: 0) > 0

    /**
     * Recompute whether the mini player should be present. The sheet is only ever shown as
     * collapsed here; an expanded or user-dragged sheet is left alone.
     */
    private fun updatePresence() {
        val behavior = standardBottomSheetBehavior ?: return
        val show = MiniPlayerPresence.shouldShow(hasActiveQueue, screenPolicy)
        if (show) {
            if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        } else if (behavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    val actuallyVisible: Boolean
        get() = standardBottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN
    val visibleAndExpanded: Boolean
        get() = standardBottomSheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED

    init {
        inflate(context, R.layout.bottom_sheet, this)
        previewPlayer = findViewById(R.id.preview_player)!!
        fullPlayer = findViewById(R.id.full_player)!!

        setOnClickListener { open() }

        activity.controllerViewModel.addRecreationalPlayerListener(activity.lifecycle, this) {
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
        }
    }

    fun open() {
        if (standardBottomSheetBehavior!!.state == BottomSheetBehavior.STATE_COLLAPSED) {
            standardBottomSheetBehavior!!.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private val bottomSheetCallback = object : BottomSheetCallback() {
        override fun onStateChanged(
            bottomSheet: View,
            newState: Int,
        ) {
            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    fullPlayer.visibilityDueToBottomSheet = GONE
                    previewPlayer.visibility = VISIBLE
                    fragmentContainer.visibility = VISIBLE
                    lyricsFrame.visibility = GONE
                    previewPlayer.alpha = 1f
                    fullPlayer.alpha = 0f
                    lyricsFrame.alpha = 0f
                    bottomSheetBackCallback!!.isEnabled = false
                }

                BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                    fullPlayer.visibilityDueToBottomSheet = VISIBLE
                    previewPlayer.visibility = VISIBLE
                    fragmentContainer.visibility = VISIBLE
                    lyricsFrame.visibility = VISIBLE
                }

                BottomSheetBehavior.STATE_EXPANDED -> {
                    previewPlayer.visibility = GONE
                    fullPlayer.visibilityDueToBottomSheet = VISIBLE
                    // TODO: uncomment fragmentContainer.visibility = INVISIBLE
                    lyricsFrame.visibility = VISIBLE
                    previewPlayer.alpha = 0f
                    fullPlayer.alpha = 1f
                    lyricsFrame.alpha = 1f
                    bottomSheetBackCallback!!.isEnabled = true
                }

                 BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                    previewPlayer.visibility = GONE
                    fullPlayer.visibilityDueToBottomSheet = VISIBLE
                    fragmentContainer.visibility = VISIBLE
                    lyricsFrame.visibility = VISIBLE
                    previewPlayer.alpha = 0f
                    fullPlayer.alpha = 1f
                    lyricsFrame.alpha = 1f
                    bottomSheetBackCallback!!.isEnabled = true
                }

                BottomSheetBehavior.STATE_HIDDEN -> {
                    previewPlayer.visibility = GONE
                    fullPlayer.visibilityDueToBottomSheet = GONE
                    fragmentContainer.visibility = VISIBLE
                    lyricsFrame.visibility = GONE
                    previewPlayer.alpha = 0f
                    fullPlayer.alpha = 0f
                    lyricsFrame.alpha = 0f
                    bottomSheetBackCallback!!.isEnabled = false
                }
            }
            activity.updateTabBarPresence()
            dispatchBottomSheetInsets()
        }

        override fun onSlide(
            bottomSheet: View,
            slideOffset: Float,
        ) {
            if (slideOffset < 0) {
                // hidden state
                previewPlayer.alpha = 1 - (-1 * slideOffset)
                fullPlayer.alpha = 0f
                lyricsFrame.alpha = 0f
                return
            }
            previewPlayer.alpha = 1 - (slideOffset)
            fullPlayer.alpha = slideOffset
            lyricsFrame.alpha = slideOffset
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        doOnLayout { // wait for CoordinatorLayout to finish to allow getting behaviour
            standardBottomSheetBehavior = MyBottomSheetBehavior.from(this)
            fullPlayer.minimize = {
                standardBottomSheetBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            @SuppressLint("RestrictedApi")
            lyricsBackHelper =
                MaterialBottomContainerBackHelper(fullPlayer.bottomSheetFullLyricView)
            bottomSheetBackCallback = object : OnBackPressedCallback(enabled = false) {
                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    if (fullPlayer.bottomSheetFullLyricView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.startBackProgress(backEvent)
                    } else {
                        standardBottomSheetBehavior!!.startBackProgress(backEvent)
                    }
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (fullPlayer.bottomSheetFullLyricView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.updateBackProgress(backEvent)
                    } else {
                        standardBottomSheetBehavior!!.updateBackProgress(backEvent)
                    }
                }

                override fun handleOnBackPressed() {
                    if (fullPlayer.bottomSheetFullLyricView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        val backEvent = lyricsBackHelper!!.onHandleBackInvoked()
                        if (backEvent == null || backEvent.progress == 0f
                            || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                        ) {
                            fullPlayer.bottomSheetFullLyricView.fadOutAnimation(FullBottomSheet.LYRIC_FADE_TRANSITION_SEC)
                            return
                        }
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.finishBackProgressPersistent(
                            backEvent,
                            object : AnimatorListenerAdapter() {
                                override fun onAnimationStart(animation: Animator) {
                                    fullPlayer.bottomSheetFullLyricView.fadOutAnimation(FullBottomSheet.LYRIC_FADE_TRANSITION_SEC)
                                }
                            })
                    } else {
                        standardBottomSheetBehavior!!.handleBackInvoked()
                    }
                }

                override fun handleOnBackCancelled() {
                    if (fullPlayer.bottomSheetFullLyricView.isVisible) {
                        @SuppressLint("RestrictedApi")
                        lyricsBackHelper!!.cancelBackProgress()
                    } else {
                        standardBottomSheetBehavior!!.cancelBackProgress()
                    }
                }
            }
            /*
            lyricSheetBackCallback = object : OnBackPressedCallback(enabled = false) {
                override fun handleOnBackPressed() {
                    bottomSheetFullLyricRecyclerView.fadOutAnimation(LYRIC_FADE_TRANSITION_SEC)
                    bottomSheetFullLyricGradientViewUp.fadOutAnimation(LYRIC_FADE_TRANSITION_SEC)
                    bottomSheetFullLyricGradientViewDown.fadOutAnimation(LYRIC_FADE_TRANSITION_SEC)
                    bottomSheetLyricButton.isChecked = false
                    activity.onBackPressedDispatcher.addCallback(activity, bottomSheetBackCallback!!)
                    bottomSheetBackCallback!!.isEnabled = true
                }
            }

             */
            activity.onBackPressedDispatcher.addCallback(activity, bottomSheetBackCallback!!)
            standardBottomSheetBehavior!!.addBottomSheetCallback(bottomSheetCallback)
            // this is required after onRestoreSavedInstanceState() in BottomSheetBehaviour
            bottomSheetCallback.onStateChanged(this, standardBottomSheetBehavior!!.state)
            // Close the cold-launch ordering gap: the screen may have reported its policy before
            // the behaviour existed, and the resume state may have arrived before we attached.
            updatePresence()
            lifecycleOwner.lifecycle.addObserver(this)
            updatePeekHeight()
            dispatchBottomSheetInsets()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lastActuallyVisible = null
        lastMeasuredHeight = null
        lastTabBarHeight = null
        lastInsets = null
        fullPlayer.minimize = null
        lifecycleOwner.lifecycle.removeObserver(this)
        standardBottomSheetBehavior?.removeBottomSheetCallback(bottomSheetCallback)
        bottomSheetBackCallback?.remove()
        standardBottomSheetBehavior = null
        onStop(lifecycleOwner)
    }

    private fun updatePeekHeight() {
        previewPlayer.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.UNSPECIFIED
        )
        standardBottomSheetBehavior?.setPeekHeight(
            previewPlayer.measuredHeight + activity.bottomTabBarDockedHeight,
            false
        )
    }

    fun getBottomPadding() = if (lastActuallyVisible == true) lastMeasuredHeight ?: 0 else 0

    /**
     * The docked group changed shape (tab bar shown/hidden or measured). Recompute the sheet peek
     * and re-dispatch insets so every list padding follows the new band.
     */
    fun onDockedGroupChanged() {
        if (!isAttachedToWindow) return
        // Update the preview padding before measuring so the cached collapsed height is exact.
        applyPreviewPadding()
        updatePeekHeight()
        dispatchBottomSheetInsets()
    }

    private fun applyPreviewPadding() {
        val i = lastInsets ?: return
        previewPlayer.setPadding(
            i.left, 0, i.right,
            if (activity.isBottomTabBarVisible) 0 else i.bottom
        )
    }

    private fun dispatchBottomSheetInsets() {
        val tabBarHeight = activity.bottomTabBarDockedHeight
        val collapsedHeight = previewPlayer.measuredHeight + tabBarHeight
        if (lastMeasuredHeight == collapsedHeight &&
            lastActuallyVisible == actuallyVisible &&
            lastTabBarHeight == tabBarHeight
        ) return
        if (BuildConfig.DEBUG) Log.i(TAG, "dispatching bottom sheet insets")
        lastMeasuredHeight = collapsedHeight
        lastTabBarHeight = tabBarHeight
        lastActuallyVisible = actuallyVisible
        // This dispatches the last known insets again to force regeneration of
        // FragmentContainerView's insets which will in turn call generateBottomSheetInsets().
        val i = ViewCompat.getRootWindowInsets(activity.window.decorView)
        if (i != null) {
            ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, i.clone())
        } else Log.e(TAG, "getRootWindowInsets returned null, this should NEVER happen")
    }

    override fun dispatchApplyWindowInsets(platformInsets: WindowInsets): WindowInsets {
        val insets = WindowInsetsCompat.toWindowInsetsCompat(platformInsets)
        val myInsets = insets.getInsets(
            WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
        )
        // We here have to set up inset padding manually as the bottom sheet won't know what
        // View is behind the status bar, paddingTopSystemWindowInsets just allows it to go
        // behind it, which differs from the other padding*SystemWindowInsets. We can't use the
        // other padding*SystemWindowInsets to apply systemBars() because previewPlayer and
        // fullPlayer should extend into system bars AND display cutout. previewPlayer can't use
        // fitsSystemWindows because it doesn't want top padding from status bar.
        // We have to do it manually, duh.
        // When the tab bar is docked below the sheet, it carries the system bar inset instead, so
        // the preview must not reserve the same inset twice.
        lastInsets = myInsets
        applyPreviewPadding()
        // Let fullPlayer handle insets itself (and discard result as it's irrelevant to hierarchy)
        ViewCompat.dispatchApplyWindowInsets(fullPlayer, insets.clone())
        // Same for lyrics view
        ViewCompat.dispatchApplyWindowInsets(fullPlayer.bottomSheetFullLyricView, insets.clone())
        // Now make sure BottomSheetBehaviour has the correct View height set.
        if (isLaidOut && !isLayoutRequested) {
            updatePeekHeight()
        } else {
            doOnNextLayout {
                updatePeekHeight()
                dispatchBottomSheetInsets()
            }
        }
        val i = insets.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
        )
        return WindowInsetsCompat.Builder(insets)
            .setInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.of(0, myInsets.top, 0, 0)
            )
            .setInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.of(0, i.top, 0, 0)
            )
            .build()
            .toWindowInsets()!!
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        updatePresence()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        updatePresence()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        fullPlayer.onStop()
    }
}