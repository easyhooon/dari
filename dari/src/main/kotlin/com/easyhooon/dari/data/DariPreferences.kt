package com.easyhooon.dari.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Persists user-toggled Dari settings so changes
 * survive process restarts and override the initial [com.easyhooon.dari.DariConfig]
 * defaults.
 *
 * Backed by Jetpack DataStore Preferences. [dataStore] is injected rather
 * than built from a `Context` delegate so this class stays Android-free and
 * can be unit-tested on the JVM — and later lifted into `commonMain` for
 * KMP support (see #13). [com.easyhooon.dari.Dari] owns the production
 * instance.
 *
 * Values are exposed as hot [StateFlow]s so both Compose collectors and
 * synchronous Kotlin callers see a consistent latest-known value.
 */
internal class DariPreferences(
    private val dataStore: DataStore<Preferences>,
    private val defaultShakeToOpen: Boolean,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _shakeToOpen = MutableStateFlow(defaultShakeToOpen)

    /** User override for dark mode; `null` means "follow the system theme". */
    private val _darkMode = MutableStateFlow<Boolean?>(null)

    private val _jsonFoldingEnabled = MutableStateFlow(true)

    private val _showTagChip = MutableStateFlow(true)

    init {
        // One-shot blocking read so settings are correct for the first synchronous
        // caller (e.g. Dari.init or Compose initialValue).
        runBlocking {
            val snapshot = dataStore.data.first()
            _shakeToOpen.value = snapshot[KEY_SHAKE_TO_OPEN] ?: defaultShakeToOpen
            _darkMode.value = snapshot[KEY_DARK_MODE]
            _jsonFoldingEnabled.value = snapshot[KEY_JSON_FOLDING_ENABLED] ?: true
            _showTagChip.value = snapshot[KEY_SHOW_TAG_CHIP] ?: true
        }

        // Keep the StateFlows in sync with any subsequent DataStore writes.
        scope.launch {
            dataStore.data.collect { prefs ->
                _shakeToOpen.value = prefs[KEY_SHAKE_TO_OPEN] ?: defaultShakeToOpen
                _darkMode.value = prefs[KEY_DARK_MODE]
                _jsonFoldingEnabled.value = prefs[KEY_JSON_FOLDING_ENABLED] ?: true
                _showTagChip.value = prefs[KEY_SHOW_TAG_CHIP] ?: true
            }
        }
    }

    // region shake-to-open

    val shakeToOpen: Boolean get() = _shakeToOpen.value

    fun shakeToOpenFlow(): Flow<Boolean> = _shakeToOpen.asStateFlow()

    fun setShakeToOpen(value: Boolean) {
        scope.launch {
            dataStore.edit { it[KEY_SHAKE_TO_OPEN] = value }
        }
    }

    // endregion

    // region dark mode

    /**
     * Current dark-mode override, or `null` if the user hasn't chosen and the
     * theme should follow the system setting.
     */
    val darkMode: Boolean? get() = _darkMode.value

    fun darkModeFlow(): StateFlow<Boolean?> = _darkMode.asStateFlow()

    fun setDarkMode(value: Boolean?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (value == null) prefs.remove(KEY_DARK_MODE) else prefs[KEY_DARK_MODE] = value
            }
        }
    }

    // endregion

    // region JSON folding

    val jsonFoldingEnabled: Boolean get() = _jsonFoldingEnabled.value

    fun jsonFoldingEnabledFlow(): Flow<Boolean> = _jsonFoldingEnabled.asStateFlow()

    fun setJsonFoldingEnabled(value: Boolean) {
        scope.launch {
            dataStore.edit { it[KEY_JSON_FOLDING_ENABLED] = value }
        }
    }

    // endregion

    // region tag chip

    val showTagChip: Boolean get() = _showTagChip.value

    fun showTagChipFlow(): Flow<Boolean> = _showTagChip.asStateFlow()

    fun setShowTagChip(value: Boolean) {
        scope.launch {
            dataStore.edit { it[KEY_SHOW_TAG_CHIP] = value }
        }
    }

    // endregion

    companion object {
        private val KEY_SHAKE_TO_OPEN = booleanPreferencesKey("shake_to_open")
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        private val KEY_JSON_FOLDING_ENABLED = booleanPreferencesKey("json_folding_enabled")
        private val KEY_SHOW_TAG_CHIP = booleanPreferencesKey("show_tag_chip")
    }
}
