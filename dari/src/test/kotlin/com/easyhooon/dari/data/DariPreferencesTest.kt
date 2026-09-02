package com.easyhooon.dari.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DariPreferencesTest {

    @Test
    fun `tag chip is shown by default`() {
        val preferences = DariPreferences(
            dataStore = InMemoryDataStore(),
            defaultShakeToOpen = false,
        )

        assertTrue(preferences.showTagChip)
    }

    @Test
    fun `tag chip visibility is persisted`() = runBlocking {
        val dataStore = InMemoryDataStore()
        val preferences = DariPreferences(
            dataStore = dataStore,
            defaultShakeToOpen = false,
        )

        preferences.setShowTagChip(false)
        withTimeout(1_000) {
            preferences.showTagChipFlow().first { !it }
        }

        val restoredPreferences = DariPreferences(
            dataStore = dataStore,
            defaultShakeToOpen = false,
        )
        assertFalse(restoredPreferences.showTagChip)
    }
}

private class InMemoryDataStore(
    initialValue: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initialValue)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences,
    ): Preferences = transform(state.value).also { state.value = it }
}
