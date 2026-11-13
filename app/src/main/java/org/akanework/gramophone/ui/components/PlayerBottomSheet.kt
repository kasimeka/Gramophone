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
import com.google.android.material.tabs.TabLayout
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.ui.BottomTabBarLayout
import androidx.compose.runtime.mutableIntStateOf
import org.akanework.gramophone.logic.ui.UiExperiments
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
    private val bottomTabBar: TabLayout by lazy { activity.findViewById(R.id.tab_layout)!! }
    private var bottomTabBarAvailable = false
    private var bottomTabBarReserved = false
    /** How far the sheet is open, from 0 (collapsed) to 1 (expanded). The tab bar's fade follows it. */
    private var sheetSlide = 0f
    private var lastTabBarHeight = 0
    private var lastBottomPadding: Int? = null
    private var navInsets = Insets.NONE
    private val bottomTabBarLayoutListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
        if (v.height != lastTabBarHeight) {
            lastTabBarHeight = v.height
            refreshBottomTabBar()
        }
    }
    private val bottomTabsEnabled: Boolean by lazy { UiExperiments.tabsAtBottom(context) }
    /** The content bottom padding as observable state, so Compose content recomposes when it changes. */
    val bottomPadding = mutableIntStateOf(0)

    /**
     * The current screen's policy for the mini player. Presence is derived from this policy and
     * the active queue, never from whatever sheet state was left behind.
     */
    var screenPolicy: MiniPlayerScreenPolicy = MiniPlayerScreenPolicy.ALLOWED
        set(value) {
            if (field != value) {
                field = value
                updatePresence()
            }
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
        // Presence is visibility, not a sheet state: a hidden state animates back in on appear.
        if (!show && behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        visibility = if (show) VISIBLE else GONE
    }

    /**
     * Called by ViewPagerFragment. [available]: the tab bar should be shown now. [reserved]: the
     * main pager has enough sections for the bar. Hiding for a sub-page only makes the bar
     * invisible, so the peek height, and the collapsed mini player with it, stays where it is.
     */
    fun setBottomTabBarAvailable(available: Boolean, reserved: Boolean = available) {
        bottomTabBarAvailable = available
        bottomTabBarReserved = reserved
        updateTabBar()
        if (standardBottomSheetBehavior != null) refreshBottomTabBar()
    }

    /** The tab bar's only writer: its alpha and visibility both follow from [sheetSlide]. */
    private fun updateTabBar() {
        if (!bottomTabsEnabled) return
        bottomTabBar.alpha = BottomTabBarLayout.alphaForSlide(sheetSlide)
        bottomTabBar.visibility = when {
            !bottomTabBarReserved -> GONE
            BottomTabBarLayout.isVisible(bottomTabBarAvailable, sheetSlide) -> VISIBLE
            else -> View.INVISIBLE
        }
    }

    private fun refreshBottomTabBar() {
        applyNavInsets()
        updatePeekHeight()
        dispatchBottomSheetInsets()
    }

    val actuallyVisible: Boolean
        get() = visibility == VISIBLE
    val visibleAndExpanded: Boolean
        get() = standardBottomSheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED

    init {
        inflate(context, R.layout.bottom_sheet, this)
        previewPlayer = findViewById(R.id.preview_player)!!
        fullPlayer = findViewById(R.id.full_player)!!
        // The text in the mini player can change its height after the sheet was measured.
        // Every layout, not only height changes: an update skipped while this layout was pending
        // (for example a tab bar change) is caught up here even when the height stays the same.
        previewPlayer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updatePeekHeight(previewLaidOut = true)
            dispatchBottomSheetInsets()
        }

        setOnClickListener { open() }

        activity.controllerViewModel.addRecreationalPlayerListener(activity.lifecycle, this) {
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
            // The controller can arrive before the sheet is attached; check presence once it has.
            post { updatePresence() }
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
            sheetSlide = when (newState) {
                BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_HALF_EXPANDED -> 1f
                BottomSheetBehavior.STATE_COLLAPSED, BottomSheetBehavior.STATE_HIDDEN -> 0f
                else -> sheetSlide
            }
            updateTabBar()
            dispatchBottomSheetInsets()
            bottomPadding.intValue = getBottomPadding()
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
            sheetSlide = slideOffset
            updateTabBar()
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
            // Layout can still reset the sheet state after attach, so presence is checked again after it.
            post { updatePresence() }
            lifecycleOwner.lifecycle.addObserver(this)
            bottomTabBar.addOnLayoutChangeListener(bottomTabBarLayoutListener)
            updatePeekHeight()
            dispatchBottomSheetInsets()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lastBottomPadding = null
        lastTabBarHeight = 0
        bottomTabBar.removeOnLayoutChangeListener(bottomTabBarLayoutListener)
        fullPlayer.minimize = null
        lifecycleOwner.lifecycle.removeObserver(this)
        standardBottomSheetBehavior?.removeBottomSheetCallback(bottomSheetCallback)
        bottomSheetBackCallback?.remove()
        standardBottomSheetBehavior = null
        onStop(lifecycleOwner)
    }

    /**
     * The mini player's height only comes from a finished layout. Measuring it from here would
     * leave its constraint layout positioning the title and artist for the wrong height, so a
     * pending layout reports the new height through the mini player's layout listener instead.
     */
    private fun updatePeekHeight(previewLaidOut: Boolean = false) {
        if (width <= 0) return
        val miniHeight = previewPlayer.height
        if (miniHeight > 0 && (previewLaidOut || !previewPlayer.isLayoutRequested)) {
            standardBottomSheetBehavior?.setPeekHeight(
                BottomTabBarLayout.peekHeight(miniHeight, bottomTabBarHeight()), false
            )
        }
        bottomPadding.intValue = getBottomPadding()
    }

    /** The tab bar height as it is drawn, so the resting position matches the content exactly. */
    private fun bottomTabBarHeight(): Int {
        if (!bottomTabsEnabled || bottomTabBar.visibility == GONE) return 0
        if (bottomTabBar.height > 0) return bottomTabBar.height
        val w = width
        if (w > 0) {
            bottomTabBar.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.UNSPECIFIED
            )
        }
        return bottomTabBar.measuredHeight
    }

    /**
     * The tab bar sits on the navigation bar, so while it is shown the mini player does not pad
     * for the navigation bar itself.
     */
    private fun applyNavInsets() {
        previewPlayer.setPadding(
            navInsets.left, 0, navInsets.right,
            if (bottomTabsEnabled && bottomTabBar.visibility != GONE) 0 else navInsets.bottom
        )
        if (bottomTabsEnabled) bottomTabBar.setPadding(navInsets.left, 0, navInsets.right, navInsets.bottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
            updatePeekHeight()
            dispatchBottomSheetInsets()
            updatePresence()
        }
    }

    /** Bottom padding for content: the mini player while the sheet is on screen, plus the tab bar. */
    fun getBottomPadding() = BottomTabBarLayout.contentBottomPadding(
        sheetVisible = standardBottomSheetBehavior != null && actuallyVisible,
        miniPlayerHeight = previewPlayer.height,
        tabBarHeight = bottomTabBarHeight(),
    )

    private fun dispatchBottomSheetInsets() {
        val bottomPadding = getBottomPadding()
        if (lastBottomPadding == bottomPadding) return
        if (BuildConfig.DEBUG) Log.i(TAG, "dispatching bottom sheet insets")
        lastBottomPadding = bottomPadding
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
        navInsets = myInsets
        applyNavInsets()
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
