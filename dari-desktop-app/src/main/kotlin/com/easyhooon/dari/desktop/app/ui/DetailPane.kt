package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.inspector.protocol.WirePayloadMetadata
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Composable
fun DetailPane(detail: WireMessageDetail?, hasSelection: Boolean, modifier: Modifier = Modifier) {
    if (detail == null) {
        CenteredHint(if (hasSelection) "Loading…" else "Select a message", modifier)
        return
    }
    val summary = detail.summary
    SelectionContainer(modifier) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(summary.displayName ?: summary.handlerName, style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Field("Handler", summary.handlerName)
                Field("Direction", directionLabel(summary.direction))
                Field("Status", summary.status)
                summary.tag?.let { Field("Tag", it) }
                summary.requestId?.let { Field("Request ID", it) }
                Field("Requested", formatTime(summary.requestTimestamp))
                Field("Duration", durationLabel(summary))
            }
            Payload("Request", detail.requestData, detail.requestDataTruncated, detail.requestPayloadMetadata)
            Payload("Response", detail.responseData, detail.responseDataTruncated, detail.responsePayloadMetadata)
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Payload(title: String, data: String?, truncated: Boolean, metadata: WirePayloadMetadata?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val notes = listOfNotNull(
            metadata?.let { "${it.contentType.lowercase()} · ${it.decodeStatus.lowercase()} · ${it.originalSizeBytes} B" },
            "truncated".takeIf { truncated },
        )
        Text(
            (listOf(title) + notes).joinToString("  ·  "),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            data?.let(::prettyJsonOrRaw) ?: "(empty)",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(12.dp),
        )
    }
}

private val prettyJson = Json { prettyPrint = true }

internal fun prettyJsonOrRaw(text: String): String = try {
    prettyJson.encodeToString(JsonElement.serializer(), prettyJson.parseToJsonElement(text))
} catch (_: SerializationException) {
    text
}
