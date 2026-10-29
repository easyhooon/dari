package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.easyhooon.dari.desktop.app.Appearance
import com.easyhooon.dari.desktop.app.ConnectionState
import com.easyhooon.dari.desktop.app.CrashReporting
import com.easyhooon.dari.desktop.app.DesktopSettings
import com.easyhooon.dari.desktop.app.InspectorController
import com.easyhooon.dari.desktop.app.client.InspectorConnection.Companion.MIN_DARI_VERSION
import com.easyhooon.dari.desktop.app.client.InspectorConnection.Companion.PAYLOAD_SEARCH_MIN_DARI_VERSION
import com.easyhooon.dari.desktop.app.isMacOs
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariMetrics
import com.easyhooon.dari.desktop.app.ui.theme.DariTheme
import com.easyhooon.dari.desktop.app.update.Updater
import com.easyhooon.dari.inspector.protocol.AppInfo
import java.awt.Cursor

// Leaves room for the macOS traffic lights inside the transparent title bar.
private val TrafficLightInset = if (isMacOs) 78.dp else 12.dp
private val TitleBarGap = 4.dp

@Composable
fun WindowScope.App(
    controller: InspectorController,
    settings: DesktopSettings,
    updater: Updater,
    installUpdate: () -> Unit,
    openStats: () -> Unit,
) {
    DariTheme(settings.appearance) {
        Column(Modifier.fillMaxSize().background(Dari.colors.background)) {
            TitleBar(controller, updater, installUpdate)
            Divider()
            if (CrashReporting.isAvailable && !settings.crashReportNoticeDismissed) {
                CrashReportNotice(settings)
                Divider()
            }
            if (controller.adbMissing) {
                EmptyState(
                    "adb not found",
                    "Set ANDROID_HOME or add platform-tools to PATH, then reopen Dari.",
                    Modifier.fillMaxSize(),
                )
                return@Column
            }
            FilterBar(controller, openStats)
            if (controller.appLacksPayloadSearch && controller.filter.isNotBlank()) {
                Text(
                    "Payload search needs dari $PAYLOAD_SEARCH_MIN_DARI_VERSION or later in the app. " +
                        "Matching handler, display name, tag and request ID only.",
                    style = Dari.type.caption,
                    color = Dari.colors.textSecondary,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
            }
            Divider()
            Workspace(controller, settings)
        }
    }
}

/** Title bar row: device and app pickers plus the connection state. No sidebar — Dari has one job. */
@OptIn(ExperimentalFoundationApi::class) // TooltipArea
@Composable
private fun WindowScope.TitleBar(controller: InspectorController, updater: Updater, installUpdate: () -> Unit) {
    val state = controller.state
    Row(
        Modifier.fillMaxWidth().height(DariMetrics.toolbarHeight).padding(start = TrafficLightInset, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(TitleBarGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Dari", style = Dari.type.label, color = Dari.colors.text, modifier = Modifier.padding(end = 8.dp))
        val device = controller.devices.firstOrNull { it.serial == controller.selectedSerial }
        Picker(
            label = device?.title ?: "No device",
            secondary = device?.model?.takeIf { device.name != null },
            options = controller.devices,
            isSelected = { it.serial == controller.selectedSerial },
            optionLabel = { listOfNotNull(it.name, it.model, it.serial).joinToString("  ·  ") },
            onPick = { controller.selectDevice(it.serial) },
        )
        Text("/", style = Dari.type.caption, color = Dari.colors.textTertiary)
        TitleBarMiddle(
            modifier = Modifier.weight(1f),
            start = {
                // Pick from the apps found on the device; when none are listed, type the applicationId instead.
                if (controller.discoveredApps.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionDot(state is ConnectionState.Connected)
                        SearchField(
                            value = controller.applicationId,
                            onValueChange = { controller.applicationId = it },
                            placeholder = "applicationId",
                            modifier = Modifier.width(220.dp).padding(start = 4.dp),
                        )
                    }
                } else {
                    Picker(
                        label = controller.applicationId.ifBlank { "Choose app" },
                        options = controller.discoveredApps,
                        isSelected = { it == controller.applicationId },
                        optionLabel = { it },
                        onPick = {
                            controller.applicationId = it
                            controller.connect()
                        },
                        leading = { ConnectionDot(state is ConnectionState.Connected) },
                    )
                }
            },
            end = {
                // Short on screen; hovering shows every field with its label.
                TooltipArea(
                    tooltip = {
                        Text(
                            statusDetails(state),
                            style = Dari.type.caption,
                            color = Dari.colors.text,
                            modifier = Modifier.background(Dari.colors.surface, RoundedCornerShape(DariMetrics.radius))
                                .border(1.dp, Dari.colors.border, RoundedCornerShape(DariMetrics.radius))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    },
                    modifier = Modifier.widthIn(max = 420.dp),
                ) {
                    Text(
                        statusLine(state),
                        style = Dari.type.caption,
                        color = Dari.colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )
        // Connecting is automatic; the button only appears when nothing is connecting yet.
        // Disconnect and Reconnect live in the Session menu.
        if (state == ConnectionState.Idle) {
            QuietButton("Connect", onClick = controller::connect, enabled = controller.applicationId.isNotBlank())
        }
        // Last, after the session controls, as in most apps' title bars.
        UpdateStatus(updater, installUpdate)
    }
}

/**
 * The app picker, the window drag area and the status, in that order. On a narrow window the picker
 * keeps its width before the status, and both ellipsize instead of pushing the buttons after them out.
 * The title bar is transparent on macOS, so only the drag area moves the window; wrapping the whole
 * row would swallow clicks on the pickers and buttons.
 */
@Composable
private fun WindowScope.TitleBarMiddle(
    modifier: Modifier,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box { start() }
            WindowDraggableArea(Modifier.fillMaxHeight()) {}
            Box { end() }
        },
        modifier = modifier.fillMaxHeight(),
    ) { measurables, constraints ->
        val gap = TitleBarGap.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val startPlaceable = measurables[0].measure(Constraints(maxWidth = width, maxHeight = height))
        val endMax = (width - startPlaceable.width - gap).coerceAtLeast(0)
        val endPlaceable = measurables[2].measure(Constraints(maxWidth = endMax, maxHeight = height))
        val dragWidth = (width - startPlaceable.width - endPlaceable.width - 2 * gap).coerceAtLeast(0)
        val dragPlaceable = measurables[1].measure(Constraints.fixed(dragWidth, height))
        layout(width, height) {
            startPlaceable.place(0, (height - startPlaceable.height) / 2)
            dragPlaceable.place(startPlaceable.width + gap, 0)
            endPlaceable.place(width - endPlaceable.width, (height - endPlaceable.height) / 2)
        }
    }
}

/** Tells the user once that crash reports are on by default, with a way to turn them off. */
@Composable
private fun CrashReportNotice(settings: DesktopSettings) {
    Row(
        Modifier.fillMaxWidth().background(Dari.colors.surface).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Dari sends crash reports: the error type, stack trace and OS and Java versions, never messages. " +
                "You can change this in Settings.",
            style = Dari.type.caption,
            color = Dari.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        QuietButton("Turn Off", onClick = {
            settings.updateCrashReports(false)
            settings.dismissCrashReportNotice()
        })
        QuietButton("OK", onClick = settings::dismissCrashReportNotice)
    }
}

/** Shown only when there is something to act on: a newer version, progress, or a failure. */
@Composable
private fun UpdateStatus(updater: Updater, installUpdate: () -> Unit) {
    when (val phase = updater.phase) {
        // The version lives in the tooltip and About; the button only needs to be noticed.
        is Updater.Phase.Available -> Hint("Dari ${phase.version} is available. Dari restarts to install it.") {
            AccentButton("Update", onClick = installUpdate, showDownloadIcon = true)
        }
        is Updater.Phase.Manual -> Hint(
            if (phase.blocker != null) {
                "Dari ${phase.version} is available. Move Dari to Applications to update in place."
            } else {
                "Dari ${phase.version} is available. Opens the download page."
            },
        ) {
            AccentButton("Update", onClick = updater::installManually, showDownloadIcon = true)
        }
        is Updater.Phase.Working -> Text(phase.step, style = Dari.type.caption, color = Dari.colors.textSecondary)
        Updater.Phase.Checking -> Text("Checking for updates…", style = Dari.type.caption, color = Dari.colors.textTertiary)
        Updater.Phase.UpToDate, Updater.Phase.Idle -> Unit
        is Updater.Phase.Failed -> Hint(phase.message) {
            Text("Update failed", style = Dari.type.caption, color = Dari.colors.danger)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class) // TooltipArea
@Composable
private fun Hint(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Text(
                text,
                style = Dari.type.caption,
                color = Dari.colors.text,
                modifier = Modifier.background(Dari.colors.surface, RoundedCornerShape(DariMetrics.radius))
                    .border(1.dp, Dari.colors.border, RoundedCornerShape(DariMetrics.radius))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
        content = content,
    )
}

@Composable
private fun FilterBar(controller: InspectorController, openStats: () -> Unit) {
    val visible = controller.visibleMessages()
    Row(
        Modifier.fillMaxWidth().height(DariMetrics.toolbarHeight).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchField(
            value = controller.filter,
            onValueChange = { controller.filter = it },
            placeholder = "Filter handler, tag, request ID or payload",
            modifier = Modifier.widthIn(max = 360.dp).weight(1f),
        )
        Picker(
            label = controller.directionFilter?.let(::directionLabel) ?: "All directions",
            options = listOf(null, "WEB_TO_APP", "APP_TO_WEB"),
            isSelected = { it == controller.directionFilter },
            optionLabel = { it?.let(::directionLabel) ?: "All directions" },
            onPick = { controller.directionFilter = it },
        )
        Picker(
            label = controller.statusFilter?.lowercase() ?: "All statuses",
            options = listOf(null, "IN_PROGRESS", "SUCCESS", "ERROR"),
            isSelected = { it == controller.statusFilter },
            optionLabel = { it?.lowercase() ?: "All statuses" },
            onPick = { controller.statusFilter = it },
        )
        // Stay visible while a tag is picked, so a filter whose messages were cleared can still be reset.
        val tags = controller.availableTags
        if (tags.isNotEmpty() || controller.tagFilter != null) {
            Picker(
                label = controller.tagFilter ?: "All tags",
                options = listOf(null) + tags,
                isSelected = { it == controller.tagFilter },
                optionLabel = { it ?: "All tags" },
                onPick = { controller.tagFilter = it },
            )
        }
        // A single origin is just noise; the detail pane still shows the page.
        val origins = controller.availableOrigins
        if (origins.size > 1 || controller.originFilter != null) {
            Picker(
                label = controller.originFilter ?: "All origins",
                options = listOf(null) + origins,
                isSelected = { it == controller.originFilter },
                optionLabel = { it ?: "All origins" },
                onPick = { controller.originFilter = it },
            )
        }
        Spacer(Modifier.weight(1f))
        controller.notice?.let { Text(it, style = Dari.type.caption, color = Dari.colors.accent) }
        Text(
            messageCount(visible.size, controller.messages.size),
            style = Dari.type.codeCaption,
            color = Dari.colors.textTertiary,
        )
        QuietButton("Response Time", onClick = openStats)
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
private fun Workspace(controller: InspectorController, settings: DesktopSettings) {
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
                showTags = settings.showTags,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            // Closed until a message is selected, as in other two-pane apps.
            if (controller.selectedId == null) return@Row
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
                detailLoad = controller.detailLoad,
                jsonFoldingEnabled = settings.jsonFoldingEnabled,
                onCopy = controller::copySelected,
                onCopyPayload = controller::copy,
                onSave = { format ->
                    chooseExportFile(null, format)?.let { controller.export(it, format, all = false) }
                },
                onClose = controller::closeDetail,
                onRetry = controller::retryDetail,
                modifier = Modifier.width(with(density) { detailWidth.coerceIn(minDetail, maxDetail).toDp() })
                    .fillMaxHeight(),
                newerCount = controller.newerCount(visible),
                onShowLatest = { visible.firstOrNull()?.let { controller.select(it.id) } },
                searchQuery = controller.filter.trim(),
            )
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
    // The library version stays in the tooltip; the desktop version is in About.
    is ConnectionState.Connected -> with(state.app) {
        listOf("$appName v$appVersion", osLabel).plus(environment?.values.orEmpty()).joinToString(" · ")
    }
}

private fun statusDetails(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> with(state.app) {
        val env = environment?.takeIf { it.isNotEmpty() }
            ?.entries?.joinToString(", ") { "${it.key}=${it.value}" }
            ?.let { "\nEnvironment: $it" }.orEmpty()
        "App: $appName ($applicationId) v$appVersion\nDevice: $deviceModel, $osDetail\nDari library: $dariVersion$env"
    }
    else -> statusLine(state)
}

/** `API 36` or `iOS 18.2`; apps before 3.0 send no platform and are all Android. */
private val AppInfo.osLabel: String get() = if (platform == "ios") "iOS ${osVersion.orEmpty()}".trim() else "API $sdkInt"

private val AppInfo.osDetail: String get() = if (platform == "ios") osLabel else "Android API $sdkInt"

private fun emptyHint(controller: InspectorController): Pair<String, String?> = when {
    controller.devices.isEmpty() && controller.androidUnavailable ->
        "No device" to "Run an iOS app with Dari in a Simulator or on an iPhone over USB. For Android, install adb and reopen Dari."
    controller.devices.isEmpty() && isMacOs ->
        "No device" to "Connect an Android device with USB debugging on, or run an iOS app with Dari in a Simulator or on an iPhone."
    controller.devices.isEmpty() -> "No device" to "Connect an Android device with USB debugging on."
    controller.state == ConnectionState.Idle && controller.discoveredApps.isEmpty() ->
        "No Dari app found" to "Open a debug build using dari $MIN_DARI_VERSION or later. If it isn't listed, type its applicationId."
    controller.state == ConnectionState.Idle -> "Not connected" to "Pick an app that uses Dari, then connect."
    controller.state is ConnectionState.Connecting ->
        "Waiting for the app" to "Open a debug build using dari $MIN_DARI_VERSION or later; Dari reconnects on its own."
    controller.messages.isNotEmpty() -> "No matches" to "Change the filter to see more messages."
    else -> "No messages yet" to "Use a bridge in the connected app and it appears here."
}

private fun messageCount(visible: Int, total: Int): String {
    val noun = if (total == 1) "message" else "messages"
    return if (visible == total) "$total $noun" else "$visible of $total $noun"
}

private const val DETAIL_FRACTION = 0.55f

fun appearanceLabel(appearance: Appearance): String = when (appearance) {
    Appearance.SYSTEM -> "Match system"
    Appearance.LIGHT -> "Light"
    Appearance.DARK -> "Dark"
}
