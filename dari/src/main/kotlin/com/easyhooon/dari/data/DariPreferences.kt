package com.easyhooon.dari.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

/**
 * Persists user-toggled Dari settings (e.g., shake-to-open) so changes survive
 * process restarts and override the initial DariConfig defaults.
 */
internal class DariPreferences(
    context: Context,
    private val defaultShakeToOpen: Boolean,
) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_SHAKE_TO_OPEN)) {
            prefs.edit { putBoolean(KEY_SHAKE_TO_OPEN, defaultShakeToOpen) }
        }
    }

    var shakeToOpen: Boolean
        get() = prefs.getBoolean(KEY_SHAKE_TO_OPEN, defaultShakeToOpen)
        set(value) = prefs.edit { putBoolean(KEY_SHAKE_TO_OPEN, value) }

    /** Emits the current value immediately and on every subsequent change. */
    fun shakeToOpenFlow(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SHAKE_TO_OPEN) {
                trySend(shakeToOpen)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.onStart { emit(shakeToOpen) }

    companion object {
        private const val PREFS_NAME = "dari_preferences"
        private const val KEY_SHAKE_TO_OPEN = "shake_to_open"
    }
}
