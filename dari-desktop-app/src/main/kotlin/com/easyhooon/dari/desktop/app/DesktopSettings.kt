package com.easyhooon.dari.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.Rectangle
import java.util.prefs.Preferences

enum class Appearance { SYSTEM, LIGHT, DARK }

/** Main window bounds in screen points, restored on the next launch. */
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    val rectangle: Rectangle get() = Rectangle(x, y, width, height)
}

const val MIN_WINDOW_WIDTH = 640
const val MIN_WINDOW_HEIGHT = 400

/** These bounds when they are at least the minimum size and still overlap one of [screens]. */
fun WindowBounds.takeIfRestorable(screens: List<Rectangle>): WindowBounds? =
    takeIf { width >= MIN_WINDOW_WIDTH && height >= MIN_WINDOW_HEIGHT && screens.any { it.intersects(rectangle) } }

/** Desktop counterparts of the in-app Dari settings (shake-to-open is mobile-only). */
class DesktopSettings(private val prefs: Preferences = Preferences.userRoot().node(NODE)) {

    var appearance by mutableStateOf(
        runCatching { Appearance.valueOf(prefs.get(KEY_APPEARANCE, Appearance.SYSTEM.name)) }
            .getOrDefault(Appearance.SYSTEM),
    )
        private set

    var jsonFoldingEnabled by mutableStateOf(prefs.getBoolean(KEY_JSON_FOLDING, true))
        private set

    var showTags by mutableStateOf(prefs.getBoolean(KEY_SHOW_TAGS, true))
        private set

    /** Asks the connected app to mask secrets before they leave the device; separate from its in-app setting. */
    var maskingEnabled by mutableStateOf(prefs.getBoolean(KEY_MASKING, false))
        private set

    /** Sends uncaught exceptions (type, stack trace, OS/Java versions) when the build has a DSN. On by default. */
    var crashReportsEnabled by mutableStateOf(prefs.getBoolean(KEY_CRASH_REPORTS, true))
        private set

    /** Whether the one-time notice that crash reports are on has been dismissed. */
    var crashReportNoticeDismissed by mutableStateOf(prefs.getBoolean(KEY_CRASH_REPORT_NOTICE, false))
        private set

    val windowBounds: WindowBounds?
        get() {
            val values = listOf(KEY_WINDOW_X, KEY_WINDOW_Y, KEY_WINDOW_WIDTH, KEY_WINDOW_HEIGHT)
                .map { prefs.get(it, null)?.toIntOrNull() ?: return null }
            return WindowBounds(values[0], values[1], values[2], values[3])
        }

    fun saveWindowBounds(bounds: WindowBounds) {
        prefs.putInt(KEY_WINDOW_X, bounds.x)
        prefs.putInt(KEY_WINDOW_Y, bounds.y)
        prefs.putInt(KEY_WINDOW_WIDTH, bounds.width)
        prefs.putInt(KEY_WINDOW_HEIGHT, bounds.height)
    }

    /** Whether the main window was zoomed or full screen; [windowBounds] keeps its floating size. */
    val windowMaximized: Boolean
        get() = prefs.getBoolean(KEY_WINDOW_MAXIMIZED, false)

    fun saveWindowMaximized(maximized: Boolean) {
        prefs.putBoolean(KEY_WINDOW_MAXIMIZED, maximized)
    }

    fun updateAppearance(value: Appearance) {
        appearance = value
        prefs.put(KEY_APPEARANCE, value.name)
    }

    fun updateJsonFolding(enabled: Boolean) {
        jsonFoldingEnabled = enabled
        prefs.putBoolean(KEY_JSON_FOLDING, enabled)
    }

    fun updateShowTags(show: Boolean) {
        showTags = show
        prefs.putBoolean(KEY_SHOW_TAGS, show)
    }

    fun updateMasking(enabled: Boolean) {
        maskingEnabled = enabled
        prefs.putBoolean(KEY_MASKING, enabled)
    }

    fun updateCrashReports(enabled: Boolean) {
        crashReportsEnabled = enabled
        prefs.putBoolean(KEY_CRASH_REPORTS, enabled)
    }

    fun dismissCrashReportNotice() {
        crashReportNoticeDismissed = true
        prefs.putBoolean(KEY_CRASH_REPORT_NOTICE, true)
    }

    private companion object {
        const val NODE = "io/github/easyhooon/dari/desktop"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_JSON_FOLDING = "jsonFolding"
        const val KEY_SHOW_TAGS = "showTags"
        const val KEY_MASKING = "masking"
        const val KEY_CRASH_REPORTS = "crashReports"
        const val KEY_CRASH_REPORT_NOTICE = "crashReportNotice"
        const val KEY_WINDOW_X = "windowX"
        const val KEY_WINDOW_Y = "windowY"
        const val KEY_WINDOW_WIDTH = "windowWidth"
        const val KEY_WINDOW_HEIGHT = "windowHeight"
        const val KEY_WINDOW_MAXIMIZED = "windowMaximized"
    }
}
