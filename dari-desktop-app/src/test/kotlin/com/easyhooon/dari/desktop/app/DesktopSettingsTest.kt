package com.easyhooon.dari.desktop.app

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSettingsTest {
    private val node = Preferences.userRoot().node("dari-test-${UUID.randomUUID()}")

    @AfterTest
    fun tearDown() {
        node.removeNode()
    }

    @Test
    fun `defaults match the in-app settings`() {
        val settings = DesktopSettings(node)
        assertEquals(Appearance.SYSTEM, settings.appearance)
        assertTrue(settings.jsonFoldingEnabled)
        assertTrue(settings.showTags)
    }

    @Test
    fun `changes persist across instances`() {
        DesktopSettings(node).apply {
            updateAppearance(Appearance.DARK)
            updateJsonFolding(false)
            updateShowTags(false)
        }

        val reloaded = DesktopSettings(node)
        assertEquals(Appearance.DARK, reloaded.appearance)
        assertFalse(reloaded.jsonFoldingEnabled)
        assertFalse(reloaded.showTags)
    }
}
