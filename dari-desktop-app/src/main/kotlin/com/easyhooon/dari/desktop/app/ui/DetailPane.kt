package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.inspector.protocol.WirePayloadMetadata

/**
 * Everything for one message on a single screen: a one-line summary, then the request and
 * response bodies side by side (stacked when the pane is narrow), each scrolling on its own.
 */
@Composable
fun DetailPane(detail: WireMessageDetail?, onClose: () -> Unit, modifier: Modifier = Modifier) {
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
                    QuietButton("✕", onClick = onClose)
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

@Composable
private fun PayloadColumn(
    title: String,
    data: String?,
    sizeBytes: Int,
    truncated: Boolean,
    metadata: WirePayloadMetadata?,
    modifier: Modifier,
) {
    SelectionContainer(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = Dari.type.label, color = Dari.colors.text)
                val meta = listOfNotNull(
                    formatBytes(metadata?.originalSizeBytes ?: sizeBytes),
                    metadata?.contentType?.lowercase(),
                    metadata?.decodeStatus?.lowercase()?.replace('_', ' '),
                    "truncated".takeIf { truncated },
                )
                Text(meta.joinToString(" · "), style = Dari.type.caption, color = Dari.colors.textTertiary)
            }
            if (data == null) {
                Text("No payload", style = Dari.type.caption, color = Dari.colors.textTertiary)
            } else {
                CodeBlock(prettyJsonOrRaw(data))
            }
        }
    }
}

private val SIDE_BY_SIDE_MIN_WIDTH = 640.dp
