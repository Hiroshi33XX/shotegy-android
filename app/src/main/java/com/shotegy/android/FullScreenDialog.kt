// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.os.Build
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

@Suppress("DEPRECATION")
internal fun configureSystemBars(window: Window, background: Color) {
    val lightBackground = background.luminance() > .5f
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = lightBackground
        isAppearanceLightNavigationBars = lightBackground
    }
    window.statusBarColor = background.toArgb()
    window.navigationBarColor = background.toArgb()
}

/** Use the actual window MeasureSpecs, including in split screen and on Android 15+. */
internal fun fullScreenDialogProperties(edgeToEdge: Boolean, dismissOnBackPress: Boolean) =
    DialogProperties(
        // Compose UI 1.7.6's false branch replaces the window MeasureSpecs with
        // Configuration.screenHeightDp, then forces that oversized height onto
        // the window. Keep native measurement and set full width on Window instead.
        usePlatformDefaultWidth = true,
        decorFitsSystemWindows = !edgeToEdge,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = false,
    )

internal fun configureFullScreenWindow(window: Window, edgeToEdge: Boolean) {
    if (edgeToEdge) {
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR)
        val attributes = window.attributes
        if (Build.VERSION.SDK_INT >= 30) {
            attributes.setFitInsetsTypes(0)
            attributes.setFitInsetsSides(0)
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = attributes
    }
    window.setLayout(MATCH_PARENT, MATCH_PARENT)
    window.setSoftInputMode(
        if (edgeToEdge && Build.VERSION.SDK_INT >= 31) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    )
}

@Composable
internal fun FullScreenDialog(
    onDismissRequest: () -> Unit,
    edgeToEdge: Boolean = false,
    dismissOnBackPress: Boolean = true,
    darkBackground: Boolean = false,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest, fullScreenDialogProperties(edgeToEdge, dismissOnBackPress)) {
        val view = LocalView.current
        val window = (view.parent as DialogWindowProvider).window
        val background = if (darkBackground) Color.Black else MaterialTheme.colorScheme.background
        SideEffect { configureFullScreenWindow(window, edgeToEdge); configureSystemBars(window, background) }
        content()
    }
}

internal fun windowLayoutDiagnostics(view: View): String {
    val location = IntArray(2).also(view::getLocationOnScreen)
    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
    val bars = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    val ime = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
    return "窗口内容 ${view.width}×${view.height}px，屏幕起点 ${location[0]},${location[1]}；系统栏 $bars；键盘 $ime"
}
