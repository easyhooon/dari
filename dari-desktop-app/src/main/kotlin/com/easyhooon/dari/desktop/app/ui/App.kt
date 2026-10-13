package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.easyhooon.dari.desktop.app.ConnectionState
import com.easyhooon.dari.desktop.app.InspectorController
import com.easyhooon.dari.desktop.app.isMacOs
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariMetrics
import com.easyhooon.dari.desktop.app.ui.theme.DariTheme
import java.awt.Cursor

// Leaves room for the macOS traffic lights inside the transparent title bar.
private val TrafficLightInset = if (isMacOs) 78.dp else 12.dp

@Composable
fun WindowScope.App(controller: InspectorController) {
    DariTheme {
        Column(Modifier.fillMaxSize().background(Dari.colors.background)) {
            // The title bar is transparent on macOS, so this row is what moves the window.
            WindowDraggableArea { TitleBar(controller) }
            Divider()
            if (controller.adbMissing) {
                EmptyState(
                    "adb not found",
                    "Set ANDROID_HOME or add platform-tools to PATH, then reopen Dari.",
                    Modifier.fillMaxSize(),
                )
                return@Column
            }
            FilterBar(controller)
            Divider()
            Workspace(controller)
        }
    }
}

/** Title bar row: device and app pickers plus the connection state. No sidebar — Dari has one job. */
@Composable
private fun TitleBar(controller: InspectorController) {
    val state = controller.state
    Row(
        Modifier.fillMaxWidth().height(DariMetrics.toolbarHeight).padding(start = TrafficLightInset, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Dari", style = Dari.type.label, color = Dari.colors.text, modifier = Modifier.padding(end = 8.dp))
        Picker(
            label = controller.devices.firstOrNull { it.serial == controller.selectedSerial }?.model ?: "No device",
            options = controller.devices,
            optionLabel = { "${it.model}  ·  ${it.serial}" },
            onPick = { controller.selectDevice(it.serial) },
        )
        Text("/", style = Dari.type.caption, color = Dari.colors.textTertiary)
        Picker(
            label = controller.applicationId.ifBlank { "Choose app" },
            options = controller.discoveredApps,
            optionLabel = { it },
            onPick = {
                controller.applicationId = it
                controller.connect()
            },
            leading = { ConnectionDot(state is ConnectionState.Connected) },
        )
        if (controller.discoveredApps.isEmpty()) {
            SearchField(
                value = controller.applicationId,
                onValueChange = { controller.applicationId = it },
                placeholder = "applicationId",
                modifier = Modifier.width(220.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            statusLine(state),
            style = Dari.type.caption,
            color = Dari.colors.textTertiary,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (state == ConnectionState.Idle) {
            QuietButton("Connect", onClick = controller::connect, enabled = controller.applicationId.isNotBlank())
        } else {
            QuietButton("Disconnect", onClick = controller::disconnect)
        }
    }
}

@Composable
private fun FilterBar(controller: InspectorController) {
    val visible = controller.visibleMessages()
    Row(
        Modifier.fillMaxWidth().height(DariMetrics.toolbarHeight).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchField(
            value = controller.filter,
            onValueChange = { controller.filter = it },
            placeholder = "Filter handler, tag or request ID",
            modifier = Modifier.widthIn(max = 360.dp).weight(1f),
        )
        Picker(
            label = controller.directionFilter?.let(::directionLabel) ?: "All directions",
            options = listOf(null, "WEB_TO_APP", "APP_TO_WEB"),
            optionLabel = { it?.let(::directionLabel) ?: "All directions" },
            onPick = { controller.directionFilter = it },
        )
        Picker(
            label = controller.statusFilter?.lowercase() ?: "All statuses",
            options = listOf(null, "IN_PROGRESS", "SUCCESS", "ERROR"),
            optionLabel = { it?.lowercase() ?: "All statuses" },
            onPick = { controller.statusFilter = it },
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (visible.size == controller.messages.size) "${visible.size}" else "${visible.size} of ${controller.messages.size}",
            style = Dari.type.codeCaption,
            color = Dari.colors.textTertiary,
        )
        QuietButton(
            "Clear",
            onClick = controller::clearMessages,
            enabled = controller.state is ConnectionState.Connected,
            danger = true,
        )
    }
}

/** Table with a resizable detail pane docked on the right. */
@Composable
private fun Workspace(controller: InspectorController) {
    val visible = controller.visibleMessages()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val total = with(density) { maxWidth.toPx() }
        var detailWidth by remember { mutableFloatStateOf(total * DETAIL_FRACTION) }
        val minDetail = with(density) { 320.dp.toPx() }
        val maxDetail = (total - with(density) { 360.dp.toPx() }).coerceAtLeast(minDetail)
        Row(Modifier.fillMaxSize()) {
            MessageTable(
                messages = visible,
                selectedId = controller.selectedId,
                onSelect = controller::select,
                onMove = { controller.moveSelection(visible, it) },
                onClose = controller::closeDetail,
                emptyHint = emptyHint(controller),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            if (controller.selectedId != null) {
                Box(
                    Modifier.width(5.dp).fillMaxHeight()
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                detailWidth = (detailWidth - delta).coerceIn(minDetail, maxDetail)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Dari.colors.border))
                }
                DetailPane(
                    detail = controller.detail,
                    onClose = controller::closeDetail,
                    modifier = Modifier.width(with(density) { detailWidth.coerceIn(minDetail, maxDetail).toDp() })
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Dari.colors.border))
}

private fun statusLine(state: ConnectionState): String = when (state) {
    ConnectionState.Idle -> "Not connected"
    is ConnectionState.Connecting -> state.reason?.let { "Waiting for app · $it" } ?: "Connecting…"
    is ConnectionState.Connected -> with(state.app) { "$appName $appVersion · $deviceModel · API $sdkInt · Dari $dariVersion" }
}

private fun emptyHint(controller: InspectorController): Pair<String, String?> = when {
    controller.devices.isEmpty() -> "No device" to "Connect an Android device with USB debugging on."
    controller.state == ConnectionState.Idle -> "Not connected" to "Pick an app that uses dari-desktop, then connect."
    controller.state is ConnectionState.Connecting -> "Waiting for the app" to "Open the app on the device; Dari reconnects on its own."
    controller.messages.isNotEmpty() -> "No matches" to "Change the filter to see more messages."
    else -> "No messages yet" to "Use a bridge in the connected app and it appears here."
}

private const val DETAIL_FRACTION = 0.45f
