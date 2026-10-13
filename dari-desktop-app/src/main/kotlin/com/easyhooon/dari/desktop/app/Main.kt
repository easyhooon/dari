package com.easyhooon.dari.desktop.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.easyhooon.dari.desktop.app.adb.Adb
import com.easyhooon.dari.desktop.app.ui.App

private const val APP_VERSION = "0.1.0"

val isMacOs: Boolean = System.getProperty("os.name").startsWith("Mac")

fun main() {
    // Native menu bar and system appearance on macOS; must be set before any window exists.
    System.setProperty("apple.awt.application.name", "Dari")
    System.setProperty("apple.awt.application.appearance", "system")

    application {
        val scope = rememberCoroutineScope()
        val controller = remember { InspectorController(scope, Adb.locate()?.let(::Adb), APP_VERSION) }
        LaunchedEffect(Unit) { controller.start() }

        Window(
            onCloseRequest = {
                controller.disconnect()
                exitApplication()
            },
            title = "Dari",
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            if (isMacOs) {
                // Draw content under a transparent title bar, like native macOS apps.
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
            MenuBar {
                Menu("Session") {
                    Item("Reconnect", shortcut = shortcut(Key.R), onClick = controller::connect)
                    Item("Disconnect", shortcut = shortcut(Key.D), onClick = controller::disconnect)
                    Separator()
                    Item("Clear Messages", shortcut = shortcut(Key.K), onClick = controller::clearMessages)
                }
            }
            App(controller)
        }
    }
}

private fun shortcut(key: Key) = if (isMacOs) KeyShortcut(key, meta = true) else KeyShortcut(key, ctrl = true)
