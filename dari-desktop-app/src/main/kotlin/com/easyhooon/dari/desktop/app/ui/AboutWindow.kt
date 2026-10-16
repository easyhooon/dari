package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.easyhooon.dari.desktop.app.Appearance
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariTheme

/** Dari → About Dari on macOS, Help → About Dari elsewhere. The version is what bug reports need. */
@Composable
fun AboutWindow(
    version: String,
    icon: Painter?,
    appearance: Appearance,
    onCopyInfo: () -> Unit,
    onOpenLog: () -> Unit,
    onClose: () -> Unit,
) {
    Window(
        onCloseRequest = onClose,
        onPreviewKeyEvent = { handleCloseWindowShortcut(it, onClose = onClose) },
        title = "About Dari",
        resizable = false,
        state = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center), width = 320.dp, height = 300.dp),
    ) {
        CloseWindowMenu(onClose)
        WindowChrome(appearance)
        DariTheme(appearance) {
            Column(
                Modifier.fillMaxSize().background(Dari.colors.background).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                icon?.let { Image(it, contentDescription = "Dari icon", modifier = Modifier.size(64.dp)) }
                Text("Dari", style = Dari.type.title, color = Dari.colors.text, modifier = Modifier.padding(top = 8.dp))
                Text("Version $version", style = Dari.type.label, color = Dari.colors.textSecondary)
                Text(
                    "Desktop inspector for Dari WebView bridge messages",
                    style = Dari.type.caption,
                    color = Dari.colors.textTertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text("© 2026 easyhooon · Apache License 2.0", style = Dari.type.caption, color = Dari.colors.textTertiary)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    QuietButton("Copy Version Info", onClick = onCopyInfo)
                    QuietButton("Show Log", onClick = onOpenLog)
                }
            }
        }
    }
}
