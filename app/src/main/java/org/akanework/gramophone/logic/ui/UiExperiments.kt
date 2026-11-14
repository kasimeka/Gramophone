package org.akanework.gramophone.logic.ui

import android.content.Context
import androidx.preference.PreferenceManager

/** Switches for the UI experiments, read from the appearance settings. */
object UiExperiments {
    const val TABS_AT_BOTTOM = "ui_exp_tabs_at_bottom"
    const val HUGE_FOREHEAD = "ui_exp_huge_forehead"

    fun tabsAtBottom(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean(TABS_AT_BOTTOM, true)
    }

    fun hugeForehead(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean(HUGE_FOREHEAD, false)
    }

    fun setHugeForehead(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putBoolean(HUGE_FOREHEAD, enabled)
            .apply()
    }
}
