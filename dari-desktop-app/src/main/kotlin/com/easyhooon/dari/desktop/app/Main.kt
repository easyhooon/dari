package com.easyhooon.dari.desktop.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.easyhooon.dari.desktop.app.adb.Adb
import com.easyhooon.dari.desktop.app.ui.AboutWindow
import com.easyhooon.dari.desktop.app.ui.App
import com.easyhooon.dari.desktop.app.ui.NoticesWindow
import com.easyhooon.dari.desktop.app.ui.SettingsWindow
import com.easyhooon.dari.desktop.app.ui.appearanceLabel
import com.easyhooon.dari.desktop.app.ui.chooseExportFile
import com.easyhooon.dari.desktop.app.update.ReleaseSource
import com.easyhooon.dari.desktop.app.update.Updater
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.JOptionPane

// jpackage sets this in packaged builds; `./gradlew run` has no version.
private val APP_VERSION: String = System.getProperty("jpackage.app-version") ?: "dev"

val isMacOs: Boolean = System.getProperty("os.name").startsWith("Mac")

@OptIn(FlowPreview::class) // debounce
fun main() {
    // Native menu bar and system appearance on macOS; must be set before any window exists.
    System.setProperty("apple.awt.application.name", "Dari")
    System.setProperty("apple.awt.application.appearance", "system")
    System.setProperty("apple.laf.useScreenMenuBar", "true")

    AppLog.installCrashHandler()
    if (!SingleInstance.acquire()) {
        JOptionPane.showMessageDialog(null, "Dari is already running.", "Dari", JOptionPane.INFORMATION_MESSAGE)
        return
    }
    AppLog.info("Dari $APP_VERSION started")
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
        val updater = remember { Updater(scope, APP_VERSION) }
        val quit = {
            controller.disconnect()
            exitApplication()
        }
        val installUpdate = { updater.install(quit) }
        var settingsOpen by remember { mutableStateOf(false) }
        var aboutOpen by remember { mutableStateOf(false) }
        var noticesOpen by remember { mutableStateOf(false) }
        val windowState = remember { mainWindowState(settings.windowBounds) }
        LaunchedEffect(windowState) {
            // Saved as it changes, so ⌘Q and crashes keep the last bounds too.
            snapshotFlow { windowState.position to windowState.size }
                .debounce(500)
                .collect { (position, size) ->
                    if (windowState.placement == WindowPlacement.Floating && position is WindowPosition.Absolute) {
                        settings.saveWindowBounds(
                            WindowBounds(position.x.value.toInt(), position.y.value.toInt(), size.width.value.toInt(), size.height.value.toInt()),
                        )
                    }
                }
        }
        LaunchedEffect(Unit) {
            controller.start()
            updater.startPeriodicChecks()
            // macOS keeps settings under the app menu: Dari → Settings… (⌘,).
            if (isMacOs && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_PREFERENCES)) {
                Desktop.getDesktop().setPreferencesHandler { settingsOpen = true }
            }
            if (isMacOs && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)) {
                Desktop.getDesktop().setAboutHandler { aboutOpen = true }
            }
        }
        val iconPainter = remember { icon?.let { BitmapPainter(it.toComposeImageBitmap()) } }
        if (settingsOpen) SettingsWindow(settings, onClose = { settingsOpen = false })
        if (noticesOpen) NoticesWindow(settings.appearance, onClose = { noticesOpen = false })
        if (aboutOpen) {
            AboutWindow(
                version = APP_VERSION,
                icon = iconPainter,
                appearance = settings.appearance,
                onCopyInfo = { copyToClipboard(IssueReport.environment(APP_VERSION)) },
                onOpenLog = ::showLogFolder,
                onClose = { aboutOpen = false },
            )
        }

        Window(
            onCloseRequest = quit,
            title = "Dari",
            icon = iconPainter,
            state = windowState,
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
                    Item(
                        "Copy Message as Text",
                        shortcut = shortcut(Key.C, shift = true),
                        enabled = controller.canCopy,
                        onClick = controller::copySelected,
                    )
                    ExportFormat.entries.forEach { format ->
                        Item(
                            if (format == ExportFormat.TEXT) "Export Messages as Text…" else "Export Messages as JSON…",
                            enabled = controller.canExport,
                            onClick = { chooseExportFile(window, format)?.let { controller.export(it, format, all = true) } },
                        )
                    }
                    Separator()
                    Item("Clear Messages", shortcut = shortcut(Key.K), onClick = controller::clearMessages)
                }
                Menu("Window") {
                    Item("Minimize", shortcut = shortcut(Key.M), onClick = { windowState.isMinimized = true })
                    Item("Zoom", onClick = {
                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                            WindowPlacement.Floating
                        } else {
                            WindowPlacement.Maximized
                        }
                    })
                }
                Menu("Help") {
                    Item("Check for Updates…", enabled = updater.enabled, onClick = updater::checkNow)
                    val newer = updater.newerVersion
                    Item(
                        when {
                            newer == null -> "Install Update"
                            updater.phase is Updater.Phase.Available -> "Install Dari $newer"
                            else -> "Download Dari $newer…"
                        },
                        enabled = newer != null,
                        onClick = installUpdate,
                    )
                    Separator()
                    Item("Release Notes", onClick = { browse(ReleaseSource.pageUrl) })
                    Item("Report an Issue…", onClick = { browse(IssueReport.newIssueUrl(APP_VERSION)) })
                    Item("Suggest a Feature…", onClick = { browse(IssueReport.featureRequestUrl()) })
                    Item("Show Log File", onClick = ::showLogFolder)
                    Item("Third-Party Notices", onClick = { noticesOpen = true })
                    if (!isMacOs) {
                        Separator()
                        Item("About Dari", onClick = { aboutOpen = true })
                    }
                }
            }
            App(controller, settings, updater, installUpdate)
        }
    }
}

private fun loadAppIcon(): BufferedImage? =
    Thread.currentThread().contextClassLoader.getResourceAsStream("dari-icon.png")?.use(ImageIO::read)

/** Restores the last bounds when they still fall on a connected screen; otherwise centers a default window. */
private fun mainWindowState(saved: WindowBounds?): WindowState {
    val visible = saved?.takeIf { bounds ->
        bounds.width >= MIN_WINDOW_WIDTH && bounds.height >= MIN_WINDOW_HEIGHT &&
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { screen ->
                screen.defaultConfiguration.bounds.intersects(Rectangle(bounds.x, bounds.y, bounds.width, bounds.height))
            }
    } ?: return WindowState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(1280.dp, 820.dp))
    return WindowState(
        position = WindowPosition(visible.x.dp, visible.y.dp),
        size = DpSize(visible.width.dp, visible.height.dp),
    )
}

private fun showLogFolder() {
    AppLog.directory.mkdirs()
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(AppLog.directory)
    }
}

private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

private const val MIN_WINDOW_WIDTH = 640
private const val MIN_WINDOW_HEIGHT = 400

private fun browse(uri: URI) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(uri)
}

private fun shortcut(key: Key, shift: Boolean = false) =
    if (isMacOs) KeyShortcut(key, meta = true, shift = shift) else KeyShortcut(key, ctrl = true, shift = shift)
