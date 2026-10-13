package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.desktop.app.ExportFormat
import com.easyhooon.dari.desktop.app.MessageExport
import com.easyhooon.dari.desktop.app.isMacOs
import com.easyhooon.dari.desktop.app.ui.json.JsonPayload
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariMetrics
import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.inspector.protocol.WirePayloadMetadata
import kotlinx.coroutines.launch

/**
 * Everything for one message on a single screen: a one-line summary, then the request and
 * response bodies side by side (stacked when the pane is narrow), each scrolling on its own.
 */
@Composable
fun DetailPane(
    detail: WireMessageDetail?,
    jsonFoldingEnabled: Boolean,
    onCopy: () -> Unit,
    onCopyPayload: (text: String, what: String) -> Unit,
    onSave: (ExportFormat) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Dari.colors.surface)) {
        if (detail == null) {
            EmptyState("Select a message", "Its request and response appear here.", Modifier.fillMaxWidth().weight(1f))
            return@Column
        }
        val summary = detail.summary
        SelectionContainer {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusMark(summary.status)
                    Text(directionLabel(summary.direction), style = Dari.type.codeCaption, color = Dari.colors.textSecondary)
                    Text(
                        summary.displayName ?: summary.handlerName,
                        style = Dari.type.title,
                        color = Dari.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ExportMenu(onCopy, onSave)
                    QuietButton("✕", onClick = onClose, contentDescription = "Close details")
                }
                Text(
                    summaryLine(detail),
                    style = Dari.type.codeCaption,
                    color = Dari.colors.textTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Dari.colors.border))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val request: @Composable (Modifier) -> Unit = {
                PayloadColumn(
                    "Request",
                    detail.requestData,
                    summary.requestSizeBytes,
                    detail.requestDataTruncated,
                    detail.requestPayloadMetadata,
                    jsonFoldingEnabled,
                    onCopyPayload,
                    it,
                )
            }
            val response: @Composable (Modifier) -> Unit = {
                PayloadColumn(
                    "Response",
                    detail.responseData,
                    summary.responseSizeBytes,
                    detail.responseDataTruncated,
                    detail.responsePayloadMetadata,
                    jsonFoldingEnabled,
                    onCopyPayload,
                    it,
                )
            }
            if (maxWidth >= SIDE_BY_SIDE_MIN_WIDTH) {
                Row(Modifier.fillMaxSize()) {
                    request(Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Dari.colors.border))
                    response(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    request(Modifier.weight(1f).fillMaxWidth())
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Dari.colors.border))
                    response(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/** The in-app detail screen's share and save actions, desktop style. */
@Composable
private fun ExportMenu(onCopy: () -> Unit, onSave: (ExportFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        QuietButton("Export ▾", onClick = { open = true })
        DariMenu(expanded = open, onDismiss = { open = false }) {
            DariMenuItem("Copy as Text", onClick = {
                open = false
                onCopy()
            })
            DariMenuItem("Save as Text…", onClick = {
                open = false
                onSave(ExportFormat.TEXT)
            })
            DariMenuItem("Save as JSON…", onClick = {
                open = false
                onSave(ExportFormat.JSON)
            })
        }
    }
}

/** Shown instead of the payload when a protobuf body couldn't be decoded, mirroring the app's Raw tab notes. */
@Composable
private fun ProtobufNotice(status: String, originalSizeBytes: Int) {
    val (title, hint) = when (status) {
        "DECODER_UNAVAILABLE" ->
            "No protobuf decoder" to
                "Register a ProtobufPayloadDecoder in the app to see its fields."
        else -> "Protobuf decoding failed" to "The decoder threw for this payload."
    }
    Column(
        Modifier.fillMaxWidth()
            .background(Dari.colors.codeBackground, RoundedCornerShape(DariMetrics.radius))
            .border(1.dp, Dari.colors.border, RoundedCornerShape(DariMetrics.radius))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = Dari.type.label, color = Dari.colors.warning)
        Text(hint, style = Dari.type.caption, color = Dari.colors.textSecondary)
        Text(
            "Raw bytes (${formatBytes(originalSizeBytes)}) stay on the device: open the in-app viewer's Raw tab for hex and Base64.",
            style = Dari.type.caption,
            color = Dari.colors.textTertiary,
        )
    }
}

private const val DECODED = "DECODED"

private fun summaryLine(detail: WireMessageDetail): String {
    val s = detail.summary
    return listOfNotNull(
        s.handlerName.takeIf { s.displayName != null },
        s.tag,
        s.requestId,
        formatTime(s.requestTimestamp),
        durationLabel(s),
    ).joinToString("  ·  ")
}

@OptIn(ExperimentalComposeUiApi::class) // onPointerEvent
@Composable
private fun PayloadColumn(
    title: String,
    data: String?,
    sizeBytes: Int,
    truncated: Boolean,
    metadata: WirePayloadMetadata?,
    jsonFoldingEnabled: Boolean,
    onCopy: (text: String, what: String) -> Unit,
    modifier: Modifier,
) {
    // Compose Desktop's verticalScroll takes mouse drags as scroll gestures, which blocked drag-selecting
    // payload text. Drag is left to selection; the wheel, trackpad and scrollbar scroll instead.
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val wheelStep = with(LocalDensity.current) { (if (isMacOs) 10.dp else 48.dp).toPx() }
    Box(modifier) {
        Column(
            Modifier.fillMaxSize()
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val delta = event.changes.first().scrollDelta.y * wheelStep
                    scope.launch { scroll.scrollBy(delta) }
                }
                .verticalScroll(scroll, enabled = false)
                .padding(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = Dari.type.label, color = Dari.colors.text)
                metadata?.let { Badge(it.contentType) }
                val meta = listOfNotNull(
                    formatBytes(metadata?.originalSizeBytes ?: sizeBytes),
                    metadata?.decodeStatus?.takeIf { it == DECODED }?.lowercase(),
                    "truncated".takeIf { truncated },
                )
                Text(meta.joinToString(" · "), style = Dari.type.caption, color = Dari.colors.textTertiary)
            }
            val undecoded = metadata?.decodeStatus?.takeIf { it != DECODED }
            if (undecoded != null) {
                // The decoder's placeholder text says less than this; raw bytes never leave the device.
                ProtobufNotice(undecoded, metadata.originalSizeBytes)
            } else if (data == null) {
                Text("No payload", style = Dari.type.caption, color = Dari.colors.textTertiary)
            } else {
                SelectionContainer {
                    JsonPayload(data, jsonFoldingEnabled, onCopy = { onCopy(MessageExport.payload(data), title.lowercase()) })
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

private val SIDE_BY_SIDE_MIN_WIDTH = 640.dp
