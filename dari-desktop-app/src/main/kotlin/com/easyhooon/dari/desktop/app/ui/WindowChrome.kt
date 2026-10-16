package com.easyhooon.dari.desktop.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.FrameWindowScope
import com.easyhooon.dari.desktop.app.Appearance
import com.easyhooon.dari.desktop.app.WindowsChrome
import com.easyhooon.dari.desktop.app.ui.theme.DarkColors
import com.easyhooon.dari.desktop.app.ui.theme.LightColors
import com.easyhooon.dari.desktop.app.ui.theme.isDark
import java.awt.Color as AwtColor

/** Matches the Windows title bar and menu bar to [appearance]. Call it after the window's MenuBar. */
@Composable
internal fun FrameWindowScope.WindowChrome(appearance: Appearance) {
    val dark = isDark(appearance)
    LaunchedEffect(window, dark) {
        val colors = if (dark) DarkColors else LightColors
        val palette = WindowsChrome.Palette(
            background = colors.background.awt(),
            selected = colors.selected.awt(),
            border = colors.border.awt(),
            text = colors.text.awt(),
            textTertiary = colors.textTertiary.awt(),
        )
        WindowsChrome.apply(window, dark, palette)
    }
}

private fun Color.awt() = AwtColor(toArgb(), true)
