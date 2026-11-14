package org.akanework.gramophone.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import org.akanework.gramophone.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import android.os.Looper
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TopAppBarTransparencyTest {

    private val topBarLayouts = listOf(
        R.layout.fragment_viewpager,
        R.layout.fragment_top_settings,
        R.layout.fragment_general_sub,
        R.layout.activity_blacklist_settings,
        R.layout.activity_bug_handler,
    )

    @Test
    fun allXmlTopAppBarsHaveTransparentBackgroundsAndNoScrollLift() {
        val base = RuntimeEnvironment.getApplication()
        for (themeRes in listOf(R.style.Theme_Gramophone, R.style.Theme_Gramophone_PureDark)) {
            val themed = ContextThemeWrapper(base, themeRes)
            val inflater = LayoutInflater.from(themed)
            for (layoutRes in topBarLayouts) {
                val root = inflater.inflate(layoutRes, null)
                val appBars = collectViews(root, AppBarLayout::class.java)
                assertTrue("Expected AppBarLayout in $layoutRes", appBars.isNotEmpty())
                for (appBar in appBars) {
                    assertFalse("liftOnScroll should be false in $layoutRes", appBar.isLiftOnScroll)
                    appBar.setLifted(true)
                    shadowOf(Looper.getMainLooper()).runToEndOfTasks()
                    assertTrue(
                        "AppBarLayout background should be transparent in $layoutRes but was ${appBar.background}",
                        isTransparent(appBar.background),
                    )
                }
                for (ctl in collectViews(root, CollapsingToolbarLayout::class.java)) {
                    assertTrue(
                        "CollapsingToolbarLayout background should be transparent in $layoutRes but was ${ctl.background}",
                        isTransparent(ctl.background),
                    )
                    assertTrue(
                        "contentScrim should be transparent in $layoutRes but was ${ctl.contentScrim}",
                        isTransparent(ctl.contentScrim),
                    )
                    assertTrue(
                        "statusBarScrim should be transparent in $layoutRes but was ${ctl.statusBarScrim}",
                        isTransparent(ctl.statusBarScrim),
                    )
                }
                for (toolbar in collectViews(root, MaterialToolbar::class.java)) {
                    assertTrue(
                        "MaterialToolbar background should be transparent in $layoutRes but was ${toolbar.background}",
                        isTransparent(toolbar.background),
                    )
                }
            }
        }
    }

    private fun isTransparent(drawable: Drawable?): Boolean {
        if (drawable == null) return true
        if (drawable is ColorDrawable) return Color.alpha(drawable.color) == 0
        if (drawable is MaterialShapeDrawable) {
            val fill = drawable.fillColor?.defaultColor ?: Color.TRANSPARENT
            val tint = drawable.tintList?.defaultColor ?: Color.TRANSPARENT
            return Color.alpha(fill) == 0 && Color.alpha(tint) == 0
        }
        return false
    }

    private fun <T : View> collectViews(root: View, clazz: Class<T>): List<T> {
        val result = mutableListOf<T>()
        fun visit(v: View) {
            if (clazz.isInstance(v)) result.add(clazz.cast(v)!!)
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) visit(v.getChildAt(i))
            }
        }
        visit(root)
        return result
    }

}
