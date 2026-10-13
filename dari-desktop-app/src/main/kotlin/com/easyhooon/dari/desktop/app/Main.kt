package com.easyhooon.dari.desktop.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.easyhooon.dari.desktop.app.adb.Adb
import com.easyhooon.dari.desktop.app.ui.App
import com.easyhooon.dari.desktop.app.ui.SettingsWindow
import com.easyhooon.dari.desktop.app.ui.appearanceLabel
import java.awt.Desktop
import java.awt.Taskbar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

// jpackage sets this in packaged builds; `./gradlew run` has no version.
private val APP_VERSION: String = System.getProperty("jpackage.app-version") ?: "dev"

val isMacOs: Boolean = System.getProperty("os.name").startsWith("Mac")

fun main() {
    // Native menu bar and system appearance on macOS; must be set before any window exists.
    System.setProperty("apple.awt.application.name", "Dari")
    System.setProperty("apple.awt.application.appearance", "system")
    System.setProperty("apple.laf.useScreenMenuBar", "true")

    val icon = loadAppIcon()
    // Dock icon while running unpackaged (`./gradlew run`); packaged apps use the bundle icon.
    icon?.let { image ->
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
            Taskbar.getTaskbar().iconImage = image
        }
    }

    application {
        val scope = rememberCoroutineScope()
        val controller = remember {
            // ANDROID_SERIAL picks the device first, as it does for adb itself.
            InspectorController(scope, Adb.locate()?.let(::Adb), APP_VERSION, System.getenv("ANDROID_SERIAL"))
        }
        val settings = remember { DesktopSettings() }
        var settingsOpen by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            controller.start()
            // macOS keeps settings under the app menu: Dari → Settings… (⌘,).
            if (isMacOs && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_PREFERENCES)) {
                Desktop.getDesktop().setPreferencesHandler { settingsOpen = true }
            }
        }
        if (settingsOpen) SettingsWindow(settings, onClose = { settingsOpen = false })

        Window(
            onCloseRequest = {
                controller.disconnect()
                exitApplication()
            },
            title = "Dari",
            icon = icon?.let { BitmapPainter(it.toComposeImageBitmap()) },
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            if (isMacOs) {
                // Draw content under a transparent title bar, like native macOS apps.
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
            MenuBar {
                Menu("View") {
                    Appearance.entries.forEach { option ->
                        RadioButtonItem(
                            appearanceLabel(option),
                            selected = settings.appearance == option,
                            onClick = { settings.updateAppearance(option) },
                        )
                    }
                    Separator()
                    CheckboxItem(
                        "Fold JSON",
                        checked = settings.jsonFoldingEnabled,
                        onCheckedChange = settings::updateJsonFolding,
                    )
                    CheckboxItem("Show Tags", checked = settings.showTags, onCheckedChange = settings::updateShowTags)
                    if (!isMacOs) {
                        Separator()
                        Item("Settings…", shortcut = shortcut(Key.Comma), onClick = { settingsOpen = true })
                    }
                }
                Menu("Session") {
                    Item("Reconnect", shortcut = shortcut(Key.R), onClick = controller::connect)
                    Item("Disconnect", shortcut = shortcut(Key.D), onClick = controller::disconnectByUser)
                    Separator()
                    Item("Clear Messages", shortcut = shortcut(Key.K), onClick = controller::clearMessages)
                }
            }
            App(controller, settings)
        }
    }
}

private fun loadAppIcon(): BufferedImage? =
    Thread.currentThread().contextClassLoader.getResourceAsStream("dari-icon.png")?.use(ImageIO::read)

private fun shortcut(key: Key) = if (isMacOs) KeyShortcut(key, meta = true) else KeyShortcut(key, ctrl = true)
