package com.easyhooon.dari.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object DariExporter {

    @OptIn(ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    private const val EXPORT_DIR = "dari_export"
    private const val AUTHORITY_SUFFIX = ".dari.fileprovider"

    suspend fun exportAndShare(context: Context, entries: List<MessageEntry>, format: ExportFormat) {
        val file = withContext(Dispatchers.IO) {
            writeExportFile(context, entries, format)
        }
        shareFile(context, file, format)
    }

    suspend fun exportAndShareSingle(context: Context, entry: MessageEntry, format: ExportFormat) {
        exportAndShare(context, listOf(entry), format)
    }

    private fun writeExportFile(
        context: Context,
        entries: List<MessageEntry>,
        format: ExportFormat,
    ): File {
        val exportDir = File(context.cacheDir, EXPORT_DIR).apply {
            if (!exists()) mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val extension = when (format) {
            ExportFormat.TEXT -> "txt"
            ExportFormat.JSON -> "json"
        }
        val file = File(exportDir, "dari_export_$timestamp.$extension")

        val content = when (format) {
            ExportFormat.TEXT -> formatAsText(entries)
            ExportFormat.JSON -> formatAsJson(entries)
        }
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    private fun shareFile(context: Context, file: File, format: ExportFormat) {
        val authority = "${context.packageName}$AUTHORITY_SUFFIX"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val mimeType = when (format) {
            ExportFormat.TEXT -> "text/plain"
            ExportFormat.JSON -> "application/json"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Bridge Messages"))
    }

    private fun formatAsJson(entries: List<MessageEntry>): String {
        val exportable = entries.map { it.toExportable() }
        return prettyJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ExportableMessage.serializer()),
            exportable,
        )
    }

    private fun formatAsText(entries: List<MessageEntry>): String {
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault())
        return entries.joinToString("\n${"=".repeat(60)}\n\n") { entry ->
            formatSingleEntry(entry, dateFormat)
        }
    }

    internal fun formatSingleEntry(
        entry: MessageEntry,
        dateFormat: SimpleDateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault()),
    ): String {
        val direction = when (entry.direction) {
            MessageDirection.WEB_TO_APP -> "Web \u2192 App"
            MessageDirection.APP_TO_WEB -> "App \u2192 Web"
        }
        val requestSize = entry.requestData?.toByteArray(Charsets.UTF_8)?.size ?: 0
        val responseSize = entry.responseData?.toByteArray(Charsets.UTF_8)?.size ?: 0

        return buildString {
            appendLine("Handler: ${entry.handlerName}")
            appendLine("Direction: $direction")
            appendLine("Status: ${entry.status}")
            appendLine("Tag: ${entry.tag ?: "-"}")
            appendLine("Request ID: ${entry.requestId ?: "-"}")
            appendLine()
            appendLine("Request time: ${dateFormat.format(Date(entry.requestTimestamp))}")
            entry.responseTimestamp?.let {
                appendLine("Response time: ${dateFormat.format(Date(it))}")
            }
            entry.durationMs?.let {
                appendLine("Duration: $it ms")
            }
            appendLine()
            appendLine("Request size: ${formatSize(requestSize)}${if (entry.requestDataTruncated) " (truncated)" else ""}")
            appendLine("Response size: ${formatSize(responseSize)}${if (entry.responseDataTruncated) " (truncated)" else ""}")
            appendLine("Total size: ${formatSize(requestSize + responseSize)}")
            appendLine()
            appendLine("---------- Request ----------")
            appendLine()
            appendLine(formatJson(entry.requestData) ?: "(empty)")
            appendLine()
            appendLine("---------- Response ----------")
            appendLine()
            append(formatJson(entry.responseData) ?: "(empty)")
        }
    }

    private fun formatJson(jsonString: String?): String? {
        if (jsonString == null) return null
        return try {
            val element = prettyJson.parseToJsonElement(jsonString)
            prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
        } catch (_: Exception) {
            jsonString
        }
    }

    private fun formatSize(bytes: Int): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
        else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
    }
}

enum class ExportFormat {
    TEXT,
    JSON,
}
