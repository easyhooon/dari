package com.easyhooon.dari.desktop.app.ui

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import java.awt.event.InputEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.awt.event.KeyEvent as AwtKeyEvent

class WindowShortcutsTest {
    @Test
    fun `command W closes the focused macOS window once`() {
        var closed = 0
        assertTrue(handleCloseWindowShortcut(event(InputEvent.META_DOWN_MASK), macOs = true) { closed++ })
        assertEquals(1, closed)
    }

    @Test
    fun `command comma opens macOS settings`() {
        var opened = false
        assertTrue(handleSettingsWindowShortcut(event(InputEvent.META_DOWN_MASK, key = AwtKeyEvent.VK_COMMA), macOs = true) { opened = true })
        assertTrue(opened)
        assertFalse(handleSettingsWindowShortcut(event(0, key = AwtKeyEvent.VK_COMMA), macOs = true) { error("Unexpected settings") })
        assertFalse(handleSettingsWindowShortcut(event(InputEvent.META_DOWN_MASK), macOs = true) { error("Unexpected settings") })
    }

    @Test
    fun `control W closes a non macOS window`() {
        var closed = false
        assertTrue(handleCloseWindowShortcut(event(InputEvent.CTRL_DOWN_MASK), macOs = false) { closed = true })
        assertTrue(closed)
    }

    @Test
    fun `key release typing and other shortcuts stay unconsumed`() {
        val events = listOf(
            event(0),
            event(InputEvent.CTRL_DOWN_MASK),
            event(InputEvent.META_DOWN_MASK, AwtKeyEvent.KEY_RELEASED),
            event(InputEvent.META_DOWN_MASK, key = AwtKeyEvent.VK_C),
            event(InputEvent.META_DOWN_MASK or InputEvent.ALT_DOWN_MASK),
            event(InputEvent.META_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK),
        )
        events.forEach { event ->
            assertFalse(handleCloseWindowShortcut(event, macOs = true) { error("Unexpected close") })
        }
        assertFalse(handleCloseWindowShortcut(event(InputEvent.META_DOWN_MASK), macOs = false) { error("Unexpected close") })
    }

    @OptIn(InternalComposeUiApi::class)
    private fun event(modifiers: Int, type: Int = AwtKeyEvent.KEY_PRESSED, key: Int = AwtKeyEvent.VK_W): KeyEvent =
        KeyEvent(
            key = when (key) {
                AwtKeyEvent.VK_W -> Key.W
                AwtKeyEvent.VK_COMMA -> Key.Comma
                else -> Key.C
            },
            type = if (type == AwtKeyEvent.KEY_PRESSED) KeyEventType.KeyDown else KeyEventType.KeyUp,
            isAltPressed = modifiers and InputEvent.ALT_DOWN_MASK != 0,
            isCtrlPressed = modifiers and InputEvent.CTRL_DOWN_MASK != 0,
            isMetaPressed = modifiers and InputEvent.META_DOWN_MASK != 0,
            isShiftPressed = modifiers and InputEvent.SHIFT_DOWN_MASK != 0,
        )
}
