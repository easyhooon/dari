package com.easyhooon.dari.desktop.app

import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.inspector.protocol.WirePayloadMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val extension: String) { TEXT("txt"), JSON("json") }

/**
 * Same text and JSON formats as the in-app exporter (`DariExporter` in :dari), so files from the
 * phone and the desktop look alike. Payloads come from the device already masked.
 */
object MessageExport {
    private val prettyJson = Json { prettyPrint = true }
    private const val SEPARATOR_WIDTH = 60

    fun format(details: List<WireMessageDetail>, format: ExportFormat): String = when (format) {
        ExportFormat.TEXT -> details.joinToString("\n${"=".repeat(SEPARATOR_WIDTH)}\n\n") { text(it) }
        ExportFormat.JSON -> prettyJson.encodeToString(JsonElement.serializer(), JsonArray(details.map(::exportable)))
    }

    /** A payload as shown in the detail pane: pretty JSON when it parses, else the raw text. */
    fun payload(data: String): String = prettyOrRaw(data).orEmpty()

    fun suggestedFileName(format: ExportFormat, now: Date = Date()): String =
        "dari_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)}.${format.extension}"

    /** One message as readable text, the format the app shares to chat or email. */
    fun text(detail: WireMessageDetail, dateFormat: SimpleDateFormat = defaultDateFormat()): String {
        val s = detail.summary
        val direction = if (s.direction == "WEB_TO_APP") "Web → App" else "App → Web"
        return buildString {
            appendLine("Handler: ${s.handlerName}")
            s.displayName?.takeIf { it.isNotBlank() }?.let { appendLine("Display name: $it") }
            appendLine("Direction: $direction")
            appendLine("Status: ${s.status}")
            appendLine("Tag: ${s.tag ?: "-"}")
            appendLine("Request ID: ${s.requestId ?: "-"}")
            appendLine()
            appendLine("Request time: ${dateFormat.format(Date(s.requestTimestamp))}")
            s.responseTimestamp?.let {
                appendLine("Response time: ${dateFormat.format(Date(it))}")
                appendLine("Duration: ${it - s.requestTimestamp} ms")
            }
            appendLine()
            appendLine("Request size: ${formatSize(s.requestSizeBytes)}${if (detail.requestDataTruncated) " (truncated)" else ""}")
            appendLine("Response size: ${formatSize(s.responseSizeBytes)}${if (detail.responseDataTruncated) " (truncated)" else ""}")
            appendLine("Total size: ${formatSize(s.requestSizeBytes + s.responseSizeBytes)}")
            detail.requestPayloadMetadata?.let {
                appendLine("Request content type: ${it.contentType}")
                appendLine("Request decode status: ${it.decodeStatus}")
            }
            detail.responsePayloadMetadata?.let {
                appendLine("Response content type: ${it.contentType}")
                appendLine("Response decode status: ${it.decodeStatus}")
            }
            appendLine()
            appendLine("---------- Request (Decoded) ----------")
            appendLine()
            appendLine(prettyOrRaw(detail.requestData) ?: "(empty)")
            detail.requestPayloadMetadata?.let { appendRawPreview("Request", it) }
            appendLine()
            appendLine("---------- Response (Decoded) ----------")
            appendLine()
            appendLine(prettyOrRaw(detail.responseData) ?: "(empty)")
            detail.responsePayloadMetadata?.let { appendRawPreview("Response", it) }
        }
    }

    private fun StringBuilder.appendRawPreview(label: String, metadata: WirePayloadMetadata) {
        val base64 = metadata.rawPreviewBase64 ?: return
        appendLine()
        appendLine("---------- $label (Raw / Base64) ----------")
        appendLine()
        appendLine(base64.ifEmpty { "(empty)" })
    }

    // Field names and order match ExportableMessage in :dari.
    private fun exportable(detail: WireMessageDetail): JsonObject {
        val s = detail.summary
        val request = detail.requestPayloadMetadata
        val response = detail.responsePayloadMetadata
        return JsonObject(
            linkedMapOf(
                "id" to JsonPrimitive(s.id),
                "request_id" to JsonPrimitive(s.requestId),
                "handler_name" to JsonPrimitive(s.handlerName),
                "display_name" to JsonPrimitive(s.displayName),
                "direction" to JsonPrimitive(s.direction),
                "tag" to JsonPrimitive(s.tag),
                "request_data" to JsonPrimitive(detail.requestData),
                "response_data" to JsonPrimitive(detail.responseData),
                "request_data_truncated" to JsonPrimitive(detail.requestDataTruncated),
                "response_data_truncated" to JsonPrimitive(detail.responseDataTruncated),
                "request_content_type" to JsonPrimitive(request?.contentType),
                "request_original_size_bytes" to JsonPrimitive(request?.originalSizeBytes),
                "request_decode_status" to JsonPrimitive(request?.decodeStatus),
                "request_raw_preview_base64" to JsonPrimitive(request?.rawPreviewBase64),
                "request_raw_preview_size_bytes" to JsonPrimitive(request?.rawPreviewSizeBytes),
                "request_raw_preview_truncated" to JsonPrimitive(request?.rawPreviewTruncated),
                "response_content_type" to JsonPrimitive(response?.contentType),
                "response_original_size_bytes" to JsonPrimitive(response?.originalSizeBytes),
                "response_decode_status" to JsonPrimitive(response?.decodeStatus),
                "response_raw_preview_base64" to JsonPrimitive(response?.rawPreviewBase64),
                "response_raw_preview_size_bytes" to JsonPrimitive(response?.rawPreviewSizeBytes),
                "response_raw_preview_truncated" to JsonPrimitive(response?.rawPreviewTruncated),
                "status" to JsonPrimitive(s.status),
                "request_timestamp" to JsonPrimitive(s.requestTimestamp),
                "response_timestamp" to JsonPrimitive(s.responseTimestamp),
                "duration_ms" to (s.responseTimestamp?.let { JsonPrimitive(it - s.requestTimestamp) } ?: JsonNull),
            ),
        )
    }

    private fun prettyOrRaw(text: String?): String? = text?.let {
        runCatching { prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(it)) }.getOrDefault(it)
    }

    private fun formatSize(bytes: Int): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
        else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
    }

    private fun defaultDateFormat() = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault())
}

fun copyText(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}
