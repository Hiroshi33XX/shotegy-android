// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FullScreenDialogTest {
    /** Exercise the installed Compose measurement code with a window smaller than the display. */
    private fun measuredDialogHeight(windowHeight: Int, screenHeightDp: Int, nativeMeasurement: Boolean): Int {
        val app = RuntimeEnvironment.getApplication()
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { this.screenHeightDp = screenHeightDp })
        val window = Dialog(context).window!!
        val type = Class.forName("androidx.compose.ui.window.DialogLayout")
        val layout = type.getDeclaredConstructor(Context::class.java, android.view.Window::class.java).apply { isAccessible = true }.newInstance(context, window) as ViewGroup
        type.getDeclaredMethod("setUsePlatformDefaultWidth", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(layout, nativeMeasurement)
        // A fillMaxSize child: native View avoids needing a renderer or sensor hardware.
        val child = View(context)
        type.superclass.getDeclaredField("creatingComposition").apply { isAccessible = true }.setBoolean(layout, true)
        ViewGroup::class.java.getDeclaredMethod("addViewInLayout", View::class.java, Int::class.javaPrimitiveType, ViewGroup.LayoutParams::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(layout, child, -1, ViewGroup.LayoutParams(-1, -1), true)
        type.superclass.getDeclaredField("creatingComposition").apply { isAccessible = true }.setBoolean(layout, false)
        type.declaredMethods.single { it.name.startsWith("internalOnMeasure") }.apply { isAccessible = true }.invoke(
            layout,
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(windowHeight, View.MeasureSpec.EXACTLY),
        )
        return child.measuredHeight
    }

    @Test fun fullscreenMeasurementRespectsHeightBelowStatusBar() {
        // Same failure shape as the Xiaomi screenshot: content starts below the
        // status bar but the old path still measures it against the whole display.
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val displayHeight = (900 * density).toInt()
        val available = displayHeight - (48 * density).toInt()
        assertTrue(measuredDialogHeight(available, 900, false) > available)
        assertEquals(available, measuredDialogHeight(available, 900, fullScreenDialogProperties(true, true).usePlatformDefaultWidth))
    }

    @Test fun smallerWindowAndLandscapeUseTheirOwnConstraints() {
        assertEquals(420, measuredDialogHeight(420, 900, true))
        assertEquals(300, measuredDialogHeight(300, 900, true))
    }

    @Test fun edgeToEdgeWindowIsActuallyMatchParentAndDoesNotFitInsetsTwice() {
        val window = Dialog(RuntimeEnvironment.getApplication()).window!!
        configureFullScreenWindow(window, true)
        assertEquals(-1, window.attributes.width)
        assertEquals(-1, window.attributes.height)
        assertEquals(0, window.attributes.fitInsetsTypes)
        assertEquals(0, window.attributes.fitInsetsSides)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, window.attributes.softInputMode)
        assertFalse(fullScreenDialogProperties(true, false).dismissOnBackPress)
    }

    @Test fun conventionalEditorWindowStillResizesForKeyboard() {
        val window = Dialog(RuntimeEnvironment.getApplication()).window!!
        configureFullScreenWindow(window, false)
        assertEquals(-1, window.attributes.width)
        assertEquals(-1, window.attributes.height)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, window.attributes.softInputMode)
        assertTrue(fullScreenDialogProperties(false, true).decorFitsSystemWindows)
    }
}
