package com.easyhooon.dari.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.easyhooon.dari.MessageStatus

// Amber
internal val Amber100 = Color(0xFFFFECB3)
internal val Amber200 = Color(0xFFFFE082)
internal val Amber300 = Color(0xFFFFD54F)
internal val Amber400 = Color(0xFFFFCA28)
internal val Amber500 = Color(0xFFFFC107)
internal val Amber600 = Color(0xFFFFB300)
internal val Amber700 = Color(0xFFFFA000)
internal val Amber800 = Color(0xFFFF8F00)
internal val Amber900 = Color(0xFFFF6F00)

// Green
internal val Green100 = Color(0xFFC8E6C9)
internal val Green200 = Color(0xFFA5D6A7)
internal val Green300 = Color(0xFF81C784)
internal val Green400 = Color(0xFF66BB6A)
internal val Green500 = Color(0xFF4CAF50)
internal val Green600 = Color(0xFF43A047)
internal val Green700 = Color(0xFF388E3C)
internal val Green800 = Color(0xFF2E7D32)
internal val Green900 = Color(0xFF1B5E20)

// Red
internal val Red100 = Color(0xFFFFCDD2)
internal val Red200 = Color(0xFFEF9A9A)
internal val Red300 = Color(0xFFE57373)
internal val Red400 = Color(0xFFEF5350)
internal val Red500 = Color(0xFFF44336)
internal val Red600 = Color(0xFFE53935)
internal val Red700 = Color(0xFFD32F2F)
internal val Red800 = Color(0xFFC62828)
internal val Red900 = Color(0xFFB71C1C)

// Blue
internal val Blue100 = Color(0xFFBBDEFB)
internal val Blue200 = Color(0xFF90CAF9)
internal val Blue300 = Color(0xFF64B5F6)
internal val Blue400 = Color(0xFF42A5F5)
internal val Blue500 = Color(0xFF2196F3)
internal val Blue600 = Color(0xFF1E88E5)
internal val Blue700 = Color(0xFF1976D2)
internal val Blue800 = Color(0xFF1565C0)
internal val Blue900 = Color(0xFF0D47A1)

// Blue Grey
internal val BlueGrey100 = Color(0xFFCFD8DC)
internal val BlueGrey200 = Color(0xFFB0BEC5)
internal val BlueGrey300 = Color(0xFF90A4AE)
internal val BlueGrey400 = Color(0xFF78909C)
internal val BlueGrey500 = Color(0xFF607D8B)
internal val BlueGrey600 = Color(0xFF546E7A)
internal val BlueGrey700 = Color(0xFF455A64)
internal val BlueGrey800 = Color(0xFF37474F)
internal val BlueGrey900 = Color(0xFF263238)

/**
 * Three-role palette for [MessageStatus] visual representation:
 *
 * - [container]   — chip background fill (same in both themes).
 * - [onContainer] — text/icon on [container].
 * - [onSurface]   — status label drawn on the app surface (varies per theme).
 */
internal data class MessageStatusPalette(
    val container: Color,
    val onContainer: Color,
    val onSurface: Color,
)

internal val MessageStatus.palette: MessageStatusPalette
    @Composable
    @ReadOnlyComposable
    get() {
        val isDarkScheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        return when (this) {
            MessageStatus.IN_PROGRESS -> MessageStatusPalette(
                container = Amber500,
                onContainer = Color.Black,
                onSurface = if (isDarkScheme) Amber300 else Amber800,
            )
            MessageStatus.SUCCESS -> MessageStatusPalette(
                container = Green500,
                onContainer = Color.White,
                onSurface = if (isDarkScheme) Green300 else Green800,
            )
            MessageStatus.ERROR -> MessageStatusPalette(
                container = Red500,
                onContainer = Color.White,
                onSurface = if (isDarkScheme) Red300 else Red800,
            )
        }
    }
