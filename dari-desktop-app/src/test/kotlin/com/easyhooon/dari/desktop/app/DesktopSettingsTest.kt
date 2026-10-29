package com.easyhooon.dari.desktop.app

import java.awt.Rectangle
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        assertTrue(settings.crashReportsEnabled)
        assertFalse(settings.crashReportNoticeDismissed)
    }

    @Test
    fun `changes persist across instances`() {
        DesktopSettings(node).apply {
            updateAppearance(Appearance.DARK)
            updateJsonFolding(false)
            updateShowTags(false)
            updateCrashReports(false)
            dismissCrashReportNotice()
        }

        val reloaded = DesktopSettings(node)
        assertEquals(Appearance.DARK, reloaded.appearance)
        assertFalse(reloaded.jsonFoldingEnabled)
        assertFalse(reloaded.showTags)
        assertFalse(reloaded.crashReportsEnabled)
        assertTrue(reloaded.crashReportNoticeDismissed)
    }

    @Test
    fun `window bounds round-trip and start empty`() {
        assertNull(DesktopSettings(node).windowBounds)
        DesktopSettings(node).saveWindowBounds(WindowBounds(10, 20, 900, 700))
        assertEquals(WindowBounds(10, 20, 900, 700), DesktopSettings(node).windowBounds)
    }

    @Test
    fun `bounds on a secondary screen are restorable`() {
        val screens = listOf(Rectangle(0, 0, 1440, 900), Rectangle(1440, 0, 2560, 1440))
        val external = WindowBounds(1700, 100, 1300, 800)
        assertEquals(external, external.takeIfRestorable(screens))
        assertNull(external.takeIfRestorable(screens.take(1)))
        assertNull(WindowBounds(0, 30, 80, 28).takeIfRestorable(screens))
    }
}
