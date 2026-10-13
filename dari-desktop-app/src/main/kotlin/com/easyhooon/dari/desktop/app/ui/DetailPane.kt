package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariMetrics
import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.inspector.protocol.WirePayloadMetadata

private val Tabs = listOf("Summary", "Request", "Response")

@Composable
fun DetailPane(detail: WireMessageDetail?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.background(Dari.colors.surface)) {
        if (detail == null) {
            EmptyState("Loading…", null, Modifier.fillMaxWidth().weight(1f))
            return@Column
        }
        val summary = detail.summary
        Row(
            Modifier.fillMaxWidth().heightIn(min = DariMetrics.toolbarHeight).padding(horizontal = 12.dp),
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
        Tabs(Tabs, tab) { tab = it }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Dari.colors.border))
        SelectionContainer(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                when (tab) {
                    0 -> Pairs(summaryPairs(detail))
                    1 -> PayloadSection(detail.requestData, detail.requestDataTruncated, detail.requestPayloadMetadata)
                    else -> PayloadSection(detail.responseData, detail.responseDataTruncated, detail.responsePayloadMetadata)
                }
            }
        }
    }
}

private fun summaryPairs(detail: WireMessageDetail): List<Pair<String, String>> {
    val s = detail.summary
    return listOfNotNull(
        "Handler" to s.handlerName,
        s.displayName?.let { "Display name" to it },
        "Direction" to directionLabel(s.direction),
        "Status" to s.status.lowercase(),
        s.tag?.let { "Tag" to it },
        s.requestId?.let { "Request ID" to it },
        "Requested" to formatTime(s.requestTimestamp),
        s.responseTimestamp?.let { "Responded" to formatTime(it) },
        "Duration" to durationLabel(s),
        "Request size" to formatBytes(s.requestSizeBytes),
        "Response size" to formatBytes(s.responseSizeBytes),
    )
}

@Composable
private fun PayloadSection(data: String?, truncated: Boolean, metadata: WirePayloadMetadata?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val meta = listOfNotNull(
            metadata?.let { formatBytes(it.originalSizeBytes) },
            metadata?.contentType?.lowercase() ?: data?.let { "text" },
            metadata?.decodeStatus?.lowercase()?.replace('_', ' '),
            "truncated".takeIf { truncated },
        )
        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = Dari.type.caption, color = Dari.colors.textTertiary)
        if (data == null) {
            Text("No payload", style = Dari.type.body, color = Dari.colors.textTertiary)
        } else {
            CodeBlock(prettyJsonOrRaw(data))
        }
    }
}
