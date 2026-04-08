package com.easyhooon.dari.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Chucker-style blue TopBar color, used as the brand primary in both themes. */
val DariBlue = Color(0xFF2D6AB1)

/** Slightly dimmer blue for the dark theme so it doesn't glow on AMOLED. */
internal val DariBlueDark = Color(0xFF1F4F87)

// Soft neutral grays for dark mode — avoids the pure-black Material3 default
// so the UI reads as "dimmed" rather than "OLED black".
internal val DariDarkBackground = Color(0xFF242428)
private val DariDarkSurface = Color(0xFF323236)
private val DariDarkSurfaceVariant = Color(0xFF42424A)
private val DariDarkOnSurface = Color(0xFFE8E8EC)
private val DariDarkOnSurfaceVariant = Color(0xFFB5B5BA)
private val DariDarkOutline = Color(0xFF505056)
private val DariDarkOutlineVariant = Color(0xFF42424A)

// Off-white foreground for dark mode over the blue top bar / tabs — pure
// white feels harsh next to the dimmed primary color in dark mode.
private val DariDarkOnPrimary = Color(0xFFC5C5CA)

private val DariLightColorScheme = lightColorScheme(
    primary = DariBlue,
    onPrimary = Color.White,
    primaryContainer = DariBlue,
    onPrimaryContainer = Color.White,
)

private val DariDarkColorScheme = darkColorScheme(
    primary = DariBlueDark,
    onPrimary = DariDarkOnPrimary,
    primaryContainer = DariBlueDark,
    onPrimaryContainer = DariDarkOnPrimary,
    background = DariDarkBackground,
    onBackground = DariDarkOnSurface,
    surface = DariDarkSurface,
    onSurface = DariDarkOnSurface,
    surfaceVariant = DariDarkSurfaceVariant,
    onSurfaceVariant = DariDarkOnSurfaceVariant,
    surfaceContainer = DariDarkSurface,
    surfaceContainerHigh = DariDarkSurfaceVariant,
    outline = DariDarkOutline,
    outlineVariant = DariDarkOutlineVariant,
)

/**
 * Material3 theme wrapper for Dari's internal screens.
 *
 * [darkTheme] accepts a nullable [Boolean] so callers can pass the persisted
 * user override directly: `null` means "follow the system", `true` / `false`
 * mean the user explicitly chose dark / light.
 */
@Composable
internal fun DariTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val isDark = darkTheme ?: isSystemInDarkTheme()
    val colorScheme = if (isDark) DariDarkColorScheme else DariLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/**
 * Applies edge-to-edge with system bar styles that follow Dari's theme:
 *
 * - **Status bar** matches the top bar — [DariBlue] in light mode,
 *   [DariBlueDark] in dark mode — so the two visually merge into a single
 *   colored band instead of leaving an off-color strip on top.
 * - **Navigation bar** matches the app background — white in light mode,
 *   [DariDarkBackground] in dark mode — so it blends into the list area.
 *
 * Reacts to [isDark] changes so toggling the theme at runtime updates the
 * system bars immediately.
 */
@Composable
internal fun ApplyDariSystemBars(isDark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(isDark) {
        val statusBarColor = if (isDark) DariBlueDark else DariBlue
        val statusBarStyle = SystemBarStyle.dark(statusBarColor.toArgb())
        val navBarStyle = if (isDark) {
            SystemBarStyle.dark(DariDarkBackground.toArgb())
        } else {
            SystemBarStyle.light(
                scrim = Color.White.toArgb(),
                darkScrim = Color.White.toArgb(),
            )
        }
        activity.enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navBarStyle,
        )
        onDispose { }
    }
}

object DariTopBarColors {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun colors(): TopAppBarColors {
        val container = MaterialTheme.colorScheme.primary
        val onContainer = MaterialTheme.colorScheme.onPrimary
        return TopAppBarColors(
            containerColor = container,
            scrolledContainerColor = container,
            navigationIconContentColor = onContainer,
            titleContentColor = onContainer,
            actionIconContentColor = onContainer,
        )
    }
}
