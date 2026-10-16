package com.easyhooon.dari.desktop.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyhooon.dari.desktop.app.Appearance

// Layout rules borrowed from Necto's design notes: colour carries status only, one separator
// per region, weight/colour for hierarchy, UI face for labels and mono face for values.
// Dari's own identity: cool blue-grey neutrals with the Dari blue as the single accent.

@Immutable
data class DariColors(
    val background: Color,
    val surface: Color,
    val hover: Color,
    val selected: Color,
    val border: Color,
    val borderStrong: Color,
    val textTertiary: Color,
    val textSecondary: Color,
    val text: Color,
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    // Code blocks: set apart from the detail pane's surface, which they sit on.
    val codeBackground: Color,
    // JSON syntax: muted so status colours stay the loudest thing on screen.
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxKeyword: Color,
)

val LightColors = DariColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF8FAFC),
    hover = Color(0xFFF2F5F8),
    selected = Color(0xFFE8EEF5),
    border = Color(0xFFE2E7ED),
    borderStrong = Color(0xFFC8D0D9),
    textTertiary = Color(0xFF66707B),
    textSecondary = Color(0xFF46505B),
    text = Color(0xFF111820),
    accent = Color(0xFF2D6AB1),
    success = Color(0xFF1C7A45),
    warning = Color(0xFF8A5A00),
    danger = Color(0xFFB42318),
    codeBackground = Color(0xFFEEF2F6),
    syntaxString = Color(0xFF2F6B4F),
    syntaxNumber = Color(0xFF2D5FA8),
    syntaxKeyword = Color(0xFF8A4B9C),
)

val DarkColors = DariColors(
    background = Color(0xFF16191D),
    surface = Color(0xFF1D2126),
    hover = Color(0xFF23282E),
    selected = Color(0xFF2A3139),
    border = Color(0xFF2F353D),
    borderStrong = Color(0xFF414953),
    textTertiary = Color(0xFF8F98A3),
    textSecondary = Color(0xFFB3BBC5),
    text = Color(0xFFE8ECF1),
    accent = Color(0xFF6EA2E6),
    success = Color(0xFF4CC27A),
    warning = Color(0xFFE3A83A),
    danger = Color(0xFFFF6B60),
    codeBackground = Color(0xFF131619),
    syntaxString = Color(0xFF8CCBA6),
    syntaxNumber = Color(0xFF8FB8F0),
    syntaxKeyword = Color(0xFFC79AD6),
)

@Immutable
data class DariType(
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val code: TextStyle,
    val codeCaption: TextStyle,
)

private val ui = FontFamily.SansSerif
private val mono = FontFamily.Monospace

val DariTypography = DariType(
    title = TextStyle(fontFamily = ui, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    body = TextStyle(fontFamily = ui, fontSize = 13.sp, lineHeight = 19.sp),
    label = TextStyle(fontFamily = ui, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    caption = TextStyle(fontFamily = ui, fontSize = 11.sp, lineHeight = 16.sp),
    code = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 18.sp),
    codeCaption = TextStyle(fontFamily = mono, fontSize = 11.sp, lineHeight = 16.sp),
)

object DariMetrics {
    val rowHeight = 26.dp
    val toolbarHeight = 38.dp
    val controlHeight = 24.dp
    val radius = 4.dp
}

val LocalDariColors = staticCompositionLocalOf { LightColors }

object Dari {
    val colors: DariColors
        @Composable get() = LocalDariColors.current
    val type: DariType get() = DariTypography
}

@Composable
fun isDark(appearance: Appearance): Boolean = when (appearance) {
    Appearance.SYSTEM -> isSystemInDarkTheme()
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

@Composable
fun DariTheme(appearance: Appearance = Appearance.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDark(appearance)
    val colors = if (dark) DarkColors else LightColors
    // Material components (menus, text selection) pick up the same neutrals.
    val material = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            surface = colors.surface,
            background = colors.background,
            onSurface = colors.text,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            surface = colors.surface,
            background = colors.background,
            onSurface = colors.text,
        )
    }
    CompositionLocalProvider(LocalDariColors provides colors) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
