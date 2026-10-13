package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.desktop.app.ConnectionState
import com.easyhooon.dari.desktop.app.InspectorController
import com.easyhooon.dari.desktop.app.isMacOs

// Room for the macOS traffic lights when content extends under the transparent title bar.
private val TitleBarInset = if (isMacOs) 28.dp else 0.dp

@Composable
fun App(controller: InspectorController) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(TitleBarInset))
                Toolbar(controller)
                HorizontalDivider()
                if (controller.adbMissing) {
                    CenteredHint("adb not found. Set ANDROID_HOME or add platform-tools to PATH, then restart Dari.")
                } else {
                    Row(Modifier.fillMaxSize()) {
                        MessageList(
                            messages = controller.visibleMessages(),
                            selectedId = controller.selectedId,
                            onSelect = controller::select,
                            modifier = Modifier.width(460.dp).fillMaxHeight(),
                        )
                        VerticalDivider()
                        DetailPane(
                            detail = controller.detail,
                            hasSelection = controller.selectedId != null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Toolbar(controller: InspectorController) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Picker(
            label = controller.devices.firstOrNull { it.serial == controller.selectedSerial }?.model ?: "No device",
            options = controller.devices.map { it.serial to "${it.model} (${it.serial})" },
            onPick = controller::selectDevice,
        )
        OutlinedTextField(
            value = controller.applicationId,
            onValueChange = { controller.applicationId = it },
            label = { Text("Application ID") },
            singleLine = true,
            modifier = Modifier.width(300.dp),
        )
        if (controller.discoveredApps.isNotEmpty()) {
            Picker(
                label = "Found ${controller.discoveredApps.size}",
                options = controller.discoveredApps.map { it to it },
                onPick = { controller.applicationId = it },
            )
        }
        when (controller.state) {
            ConnectionState.Idle -> Button(onClick = controller::connect) { Text("Connect") }
            else -> OutlinedButton(onClick = controller::disconnect) { Text("Disconnect") }
        }
        StatusText(controller.state, Modifier.weight(1f))
        OutlinedTextField(
            value = controller.filter,
            onValueChange = { controller.filter = it },
            label = { Text("Filter") },
            singleLine = true,
            modifier = Modifier.width(220.dp),
        )
    }
}

@Composable
private fun StatusText(state: ConnectionState, modifier: Modifier) {
    val text = when (state) {
        ConnectionState.Idle -> "Not connected"
        is ConnectionState.Connecting -> "Waiting for app" + (state.reason?.let { " · $it" } ?: "")
        is ConnectionState.Connected ->
            "${state.app.appName} ${state.app.appVersion} · ${state.app.deviceModel} · API ${state.app.sdkInt}"
    }
    Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = modifier)
}

@Composable
private fun Picker(label: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onPick(value)
                    },
                )
            }
        }
    }
}

@Composable
internal fun CenteredHint(text: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
