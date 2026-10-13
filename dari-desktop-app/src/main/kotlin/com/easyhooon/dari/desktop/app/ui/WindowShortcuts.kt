package com.easyhooon.dari.desktop.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.easyhooon.dari.desktop.app.isMacOs

/** Handle a close shortcut when it reaches the Compose scene. */
internal fun handleCloseWindowShortcut(event: KeyEvent, macOs: Boolean = isMacOs, onClose: () -> Unit): Boolean {
    return handlePrimaryWindowShortcut(event, Key.W, macOs, onClose)
}

internal fun handleSettingsWindowShortcut(event: KeyEvent, macOs: Boolean = isMacOs, onOpen: () -> Unit): Boolean =
    handlePrimaryWindowShortcut(event, Key.Comma, macOs, onOpen)

private fun handlePrimaryWindowShortcut(event: KeyEvent, key: Key, macOs: Boolean, action: () -> Unit): Boolean {
    val primaryModifier = if (macOs) event.isMetaPressed && !event.isCtrlPressed else event.isCtrlPressed && !event.isMetaPressed
    if (event.type != KeyEventType.KeyDown || event.key != key ||
        !primaryModifier || event.isAltPressed || event.isShiftPressed
    ) {
        return false
    }
    action()
    return true
}

/** Native menu accelerators also work before any Compose child has keyboard focus. */
@Composable
internal fun FrameWindowScope.CloseWindowMenu(onClose: () -> Unit) {
    MenuBar {
        Menu("Window") {
            Item("Close Window", shortcut = KeyShortcut(Key.W, meta = isMacOs, ctrl = !isMacOs), onClick = onClose)
        }
    }
}
