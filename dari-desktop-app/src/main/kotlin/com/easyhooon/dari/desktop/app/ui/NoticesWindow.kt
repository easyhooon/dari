package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.easyhooon.dari.desktop.app.Appearance
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariTheme

/** Help → Third-Party Notices: the open-source software bundled in the app. */
@Composable
fun NoticesWindow(appearance: Appearance, onClose: () -> Unit) {
    val text = remember {
        NoticesWindowResource.read() ?: "Third-party notices are missing from this build."
    }
    Window(
        onCloseRequest = onClose,
        title = "Third-Party Notices",
        state = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center), width = 560.dp, height = 480.dp),
    ) {
        DariTheme(appearance) {
            SelectionContainer {
                Text(
                    text,
                    style = Dari.type.code,
                    color = Dari.colors.text,
                    modifier = Modifier.fillMaxSize().background(Dari.colors.background)
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                )
            }
        }
    }
}

private object NoticesWindowResource {
    fun read(): String? =
        javaClass.getResourceAsStream("/third-party-notices.txt")?.bufferedReader()?.use { it.readText() }
}
