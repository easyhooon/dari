package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.easyhooon.dari.desktop.app.Appearance
import com.easyhooon.dari.desktop.app.ConnectionState
import com.easyhooon.dari.desktop.app.DesktopSettings
import com.easyhooon.dari.desktop.app.InspectorController
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariTheme

/** Opened from View → Settings… or the macOS application menu. */
@Composable
fun SettingsWindow(settings: DesktopSettings, controller: InspectorController, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        onPreviewKeyEvent = { handleCloseWindowShortcut(it, onClose = onClose) },
        title = "Dari Settings",
        resizable = false,
        state = rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            width = 380.dp,
            height = 330.dp,
        ),
    ) {
        CloseWindowMenu(onClose)
        DariTheme(settings.appearance) {
            Column(
                Modifier.fillMaxSize().background(Dari.colors.background).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionTitle("Appearance")
                Appearance.entries.forEach { option ->
                    OptionRow(
                        label = appearanceLabel(option),
                        selected = settings.appearance == option,
                        checkbox = false,
                        onClick = { settings.updateAppearance(option) },
                    )
                }
                SectionTitle("Messages", Modifier.padding(top = 12.dp))
                OptionRow(
                    label = "Fold nested JSON",
                    selected = settings.jsonFoldingEnabled,
                    checkbox = true,
                    onClick = { settings.updateJsonFolding(!settings.jsonFoldingEnabled) },
                )
                OptionRow(
                    label = "Show tags in the list",
                    selected = settings.showTags,
                    checkbox = true,
                    onClick = { settings.updateShowTags(!settings.showTags) },
                )
                OptionRow(
                    label = "Mask sensitive values",
                    selected = settings.maskingEnabled,
                    checkbox = true,
                    onClick = {
                        settings.updateMasking(!settings.maskingEnabled)
                        controller.onMaskingChanged()
                    },
                )
                val note = if (controller.state is ConnectionState.Connected && controller.appAlwaysMasks) {
                    "The connected app uses dari older than 2.2.0, which always masks."
                } else {
                    "Hides tokens, passwords and cookies before they leave the device."
                }
                Text(note, style = Dari.type.caption, color = Dari.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Dari.type.caption, color = Dari.colors.textTertiary, modifier = modifier)
}

/** A radio or checkbox row; the whole row is the hit target. */
@Composable
private fun OptionRow(label: String, selected: Boolean, checkbox: Boolean, onClick: () -> Unit) {
    val shape: Shape = if (checkbox) RoundedCornerShape(3.dp) else CircleShape
    Row(
        Modifier.fillMaxWidth().height(24.dp).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(14.dp)
                .background(if (selected) Dari.colors.accent else Dari.colors.background, shape)
                .border(1.dp, if (selected) Dari.colors.accent else Dari.colors.borderStrong, shape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !selected -> Unit
                checkbox -> Text("✓", style = Dari.type.caption, color = Dari.colors.background)
                else -> Box(Modifier.size(6.dp).background(Dari.colors.background, shape))
            }
        }
        Text(label, style = Dari.type.body, color = Dari.colors.text)
    }
}
