package com.easyhooon.dari.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.prefs.Preferences

enum class Appearance { SYSTEM, LIGHT, DARK }

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

    private companion object {
        const val NODE = "io/github/easyhooon/dari/desktop"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_JSON_FOLDING = "jsonFolding"
        const val KEY_SHOW_TAGS = "showTags"
    }
}
