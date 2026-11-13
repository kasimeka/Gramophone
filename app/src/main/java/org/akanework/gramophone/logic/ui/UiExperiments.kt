package org.akanework.gramophone.logic.ui

import android.content.Context
import androidx.preference.PreferenceManager

/** Switches for the UI experiments, read from the appearance settings. */
object UiExperiments {
    const val HUGE_FOREHEAD = "ui_exp_huge_forehead"

    fun hugeForehead(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean(HUGE_FOREHEAD, false)
    }
}
