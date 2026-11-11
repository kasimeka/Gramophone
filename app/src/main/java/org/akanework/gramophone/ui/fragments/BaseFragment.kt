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

package org.akanework.gramophone.ui.fragments

import android.animation.Animator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialSharedAxis
import org.akanework.gramophone.logic.ui.MiniPlayerScreenPolicy
import org.akanework.gramophone.ui.MainActivity
import java.lang.reflect.Field

private val mainHandler = Handler(Looper.getMainLooper())

private val runningAnimatorsField: Field? by lazy(LazyThreadSafetyMode.NONE) {
    runCatching {
        Transition::class.java.getDeclaredField("sRunningAnimators").apply {
            isAccessible = true
        }
    }.getOrNull()
}

private val animationInfoViewField: Field? by lazy(LazyThreadSafetyMode.NONE) {
    runCatching {
        Class.forName("androidx.transition.Transition\$AnimationInfo")
            .getDeclaredField("mView")
            .apply { isAccessible = true }
    }.getOrNull()
}

private val transitionCleanupListener = object : Transition.TransitionListener {
    override fun onTransitionStart(transition: Transition) {}

    override fun onTransitionEnd(transition: Transition) {
        mainHandler.post { pruneDetachedTransitionAnimators() }
    }

    override fun onTransitionCancel(transition: Transition) {
        mainHandler.post { pruneDetachedTransitionAnimators() }
    }

    override fun onTransitionPause(transition: Transition) {}

    override fun onTransitionResume(transition: Transition) {}
}

private fun unwrapActivity(context: Context?): Activity? {
    var current = context
    while (current != null) {
        if (current is Activity) return current
        val next = (current as? ContextWrapper)?.baseContext ?: return null
        if (next === current) return null
        current = next
    }
    return null
}

internal fun pruneDetachedTransitionAnimators(destroyedActivity: Activity? = null) {
    if (Looper.myLooper() != Looper.getMainLooper()) return
    val threadLocal = runCatching { runningAnimatorsField?.get(null) as? ThreadLocal<*> }.getOrNull() ?: return
    val runningAnimators = threadLocal.get() as? MutableMap<*, *> ?: return
    if (runningAnimators.isEmpty()) return
    val viewField = animationInfoViewField ?: return

    val keysToRemove = ArrayList<Any?>()
    for ((animator, info) in runningAnimators) {
        val view = if (info != null) runCatching { viewField.get(info) as? View }.getOrNull() else null
        val stillShown = view != null &&
            view.isAttachedToWindow &&
            (destroyedActivity == null || unwrapActivity(view.context) !== destroyedActivity)
        if (!stillShown) {
            keysToRemove.add(animator)
        }
    }
    for (key in keysToRemove) {
        (key as? Animator)?.cancel()
        runningAnimators.remove(key)
    }
}

/**
 * BaseFragment:
 *   It is a base fragment for all main fragments that
 * can appear in MainActivity's FragmentContainer.
 *   It creates material transitions easily and also make
 * overlapping colors more convenient. It can also manage
 * whether to show up bottom's mini player or not.
 *
 * @author AkaneTan, nift4
 * @see MainActivity
 */
abstract class BaseFragment(
    val miniPlayerPolicy: MiniPlayerScreenPolicy = MiniPlayerScreenPolicy.ALLOWED,
) : Fragment() {

    protected val mainActivity
        get() = requireActivity() as MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable material transitions.
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
            .addListener(transitionCleanupListener)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
            .addListener(transitionCleanupListener)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
            .addListener(transitionCleanupListener)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
            .addListener(transitionCleanupListener)
    }

    // https://github.com/material-components/material-components-android/issues/1984#issuecomment-1089710991
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Overlap colors.
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.colorBackground))
    }

    override fun onDestroyView() {
        // End running transitions here, but only drop the transition objects in onDestroy: they
        // belong to the fragment instance, which survives its views.
        val root = view
        val parent = root?.parent as? ViewGroup
        parent?.let { TransitionManager.endTransitions(it) }
        (root as? ViewGroup)?.let { if (it !== parent) TransitionManager.endTransitions(it) }
        super.onDestroyView()
        mainHandler.post { pruneDetachedTransitionAnimators() }
    }

    override fun onDestroy() {
        (enterTransition as? Transition)?.removeListener(transitionCleanupListener)
        (returnTransition as? Transition)?.removeListener(transitionCleanupListener)
        (exitTransition as? Transition)?.removeListener(transitionCleanupListener)
        (reenterTransition as? Transition)?.removeListener(transitionCleanupListener)
        enterTransition = null
        returnTransition = null
        exitTransition = null
        reenterTransition = null
        super.onDestroy()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) return
        // see registerFragmentLifecycleCallbacks in MainActivity
        (activity as MainActivity?)?.playerBottomSheet?.screenPolicy = miniPlayerPolicy
    }
}