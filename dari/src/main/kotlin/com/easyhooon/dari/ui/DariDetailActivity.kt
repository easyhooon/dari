package com.easyhooon.dari.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.Dari
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessagePayloadMetadata
import com.easyhooon.dari.RawPayloadFormatter
import com.easyhooon.dari.export.DariExporter
import com.easyhooon.dari.export.ExportFormat
import com.easyhooon.dari.ui.components.CodeViewer
import com.easyhooon.dari.ui.components.JsonViewer
import androidx.compose.foundation.isSystemInDarkTheme
import com.easyhooon.dari.ui.theme.ApplyDariSystemBars
import com.easyhooon.dari.ui.theme.DariTheme
import com.easyhooon.dari.ui.theme.DariTopBarColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Activity displaying bridge message details.
 * Chucker-style OVERVIEW / REQUEST / RESPONSE tab layout.
 */
class DariDetailActivity : ComponentActivity() {

    // Entry captured at launch time, consumed in the SAF callback.
    // Two launchers are registered (one per format) so document providers
    // receive the correct MIME type hint — CreateDocument fixes the type at
    // registration, not per-launch.
    private var pendingSaveEntry: MessageEntry? = null

    private val saveTextDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(DariExporter.mimeTypeFor(ExportFormat.TEXT)),
    ) { uri: Uri? -> handleSaveResult(uri, ExportFormat.TEXT) }

    private val saveJsonDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(DariExporter.mimeTypeFor(ExportFormat.JSON)),
    ) { uri: Uri? -> handleSaveResult(uri, ExportFormat.JSON) }

    private fun handleSaveResult(uri: Uri?, format: ExportFormat) {
        val entry = pendingSaveEntry
        pendingSaveEntry = null
        if (uri == null || entry == null) return
        lifecycleScope.launch {
            DariExporter.saveToUri(this@DariDetailActivity, uri, listOf(entry), format)
        }
    }

    private fun launchSave(entry: MessageEntry, format: ExportFormat) {
        pendingSaveEntry = entry
        val launcher = when (format) {
            ExportFormat.TEXT -> saveTextDocumentLauncher
            ExportFormat.JSON -> saveJsonDocumentLauncher
        }
        launcher.launch(DariExporter.suggestedFilename(format))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getLongExtra("id", -1L)
        if (id == -1L) {
            finish()
            return
        }

        setContent {
            val darkMode by Dari.preferences.darkModeFlow().collectAsStateWithLifecycle(
                initialValue = Dari.preferences.darkMode,
            )
            val jsonFoldingEnabled by Dari.preferences.jsonFoldingEnabledFlow()
                .collectAsStateWithLifecycle(
                    initialValue = Dari.preferences.jsonFoldingEnabled,
                )
            val isDark = darkMode ?: isSystemInDarkTheme()
            ApplyDariSystemBars(isDark)
            DariTheme(darkTheme = darkMode) {
                val entries by Dari.repository.entries.collectAsStateWithLifecycle()
                val entry = entries.find { it.id == id }
                var exportMenuExpanded by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(entry?.handlerName ?: "Detail") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                entry?.let { current ->
                                    Box {
                                        IconButton(onClick = { exportMenuExpanded = true }) {
                                            Icon(Icons.Default.IosShare, contentDescription = "Export")
                                        }
                                        DropdownMenu(
                                            expanded = exportMenuExpanded,
                                            onDismissRequest = { exportMenuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Share as TEXT") },
                                                onClick = {
                                                    exportMenuExpanded = false
                                                    DariExporter.shareSingleAsPlainText(
                                                        this@DariDetailActivity,
                                                        current,
                                                    )
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Share as JSON") },
                                                onClick = {
                                                    exportMenuExpanded = false
                                                    lifecycleScope.launch {
                                                        DariExporter.exportAndShareSingle(
                                                            this@DariDetailActivity,
                                                            current,
                                                            ExportFormat.JSON,
                                                        )
                                                    }
                                                },
                                            )
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Save as TEXT") },
                                                onClick = {
                                                    exportMenuExpanded = false
                                                    launchSave(current, ExportFormat.TEXT)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Save as JSON") },
                                                onClick = {
                                                    exportMenuExpanded = false
                                                    launchSave(current, ExportFormat.JSON)
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                            colors = DariTopBarColors.colors(),
                        )
                    },
                ) { padding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        if (entry == null) {
                            Text("Message not found", modifier = Modifier.padding(16.dp))
                        } else {
                            DetailTabs(entry, jsonFoldingEnabled)
                        }
                    }

                }
            }
        }
    }
}

private val TAB_TITLES = listOf("OVERVIEW", "REQUEST", "RESPONSE")

@Composable
private fun DetailTabs(
    entry: MessageEntry,
    jsonFoldingEnabled: Boolean,
) {
    val pagerState = rememberPagerState(pageCount = { TAB_TITLES.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        selectedTabIndex = pagerState.currentPage,
                        matchContentSize = false,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            },
        ) {
            TAB_TITLES.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title,
                            color = if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                            },
                        )
                    },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> OverviewTab(entry)
                1 -> DataTab(entry.requestData, entry.requestPayloadMetadata, jsonFoldingEnabled)
                2 -> DataTab(entry.responseData, entry.responsePayloadMetadata, jsonFoldingEnabled)
            }
        }
    }
}

@Composable
private fun OverviewTab(entry: MessageEntry) {
    val requestSize = entry.requestSizeBytes
    val responseSize = entry.responseSizeBytes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        val direction = when (entry.direction) {
            MessageDirection.WEB_TO_APP -> "Web \u2192 App"
            MessageDirection.APP_TO_WEB -> "App \u2192 Web"
        }

        OverviewRow("Handler", entry.handlerName)
        OverviewRow("Direction", direction)
        OverviewRow("Status", entry.status.name)
        OverviewRow("Tag", entry.tag ?: "-")
        OverviewRow("Request ID", entry.requestId ?: "-")

        Spacer(modifier = Modifier.height(8.dp))

        OverviewRow("Request time", formatTimestamp(entry.requestTimestamp))
        entry.responseTimestamp?.let {
            OverviewRow("Response time", formatTimestamp(it))
        }
        entry.durationMs?.let {
            OverviewRow("Duration", "$it ms")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OverviewRow("Request size", formatSize(requestSize) + if (entry.requestDataTruncated) " (truncated)" else "")
        OverviewRow("Response size", formatSize(responseSize) + if (entry.responseDataTruncated) " (truncated)" else "")
        OverviewRow("Total size", formatSize(requestSize + responseSize))
        entry.requestPayloadMetadata?.let { metadata ->
            OverviewRow("Request type", metadata.contentType.name)
            OverviewRow("Request decode", metadata.decodeStatus.name)
            metadata.rawPreview?.let { preview ->
                OverviewRow("Request raw", formatRawPreviewSize(preview.previewSizeBytes, metadata.originalSizeBytes))
            }
        }
        entry.responsePayloadMetadata?.let { metadata ->
            OverviewRow("Response type", metadata.contentType.name)
            OverviewRow("Response decode", metadata.decodeStatus.name)
            metadata.rawPreview?.let { preview ->
                OverviewRow("Response raw", formatRawPreviewSize(preview.previewSizeBytes, metadata.originalSizeBytes))
            }
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class PayloadViewMode {
    DECODED,
    RAW,
}

@Composable
private fun DataTab(
    data: String?,
    metadata: MessagePayloadMetadata?,
    jsonFoldingEnabled: Boolean,
) {
    val rawPreview = metadata?.rawPreview
    var viewMode by remember(rawPreview?.base64) { mutableStateOf(PayloadViewMode.DECODED) }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (rawPreview != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FilterChip(
                    selected = viewMode == PayloadViewMode.DECODED,
                    onClick = { viewMode = PayloadViewMode.DECODED },
                    label = { Text("Decoded") },
                )
                FilterChip(
                    selected = viewMode == PayloadViewMode.RAW,
                    onClick = { viewMode = PayloadViewMode.RAW },
                    label = { Text("Raw") },
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (viewMode == PayloadViewMode.RAW && rawPreview != null) {
                RawPayloadView(metadata)
            } else {
                DecodedPayloadView(data, jsonFoldingEnabled)
            }
        }
    }
}

@Composable
private fun DecodedPayloadView(
    data: String?,
    jsonFoldingEnabled: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (data.isNullOrBlank()) {
            Text(
                text = "(body is empty)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            JsonViewer(
                jsonString = data,
                foldingEnabled = jsonFoldingEnabled,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RawPayloadView(metadata: MessagePayloadMetadata) {
    val preview = metadata.rawPreview ?: return
    val hex = remember(preview.base64) { RawPayloadFormatter.formatHex(preview) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Captured ${formatRawPreviewSize(preview.previewSizeBytes, metadata.originalSizeBytes)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (preview.truncated) {
            Text(
                text = "Raw preview is limited to the first ${formatSize(preview.previewSizeBytes)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("HEX", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        CodeViewer(hex)

        Spacer(modifier = Modifier.height(16.dp))
        Text("BASE64", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        CodeViewer(preview.base64.ifEmpty { "(empty)" })
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Base64 is a display encoding of the captured bytes, not part of protobuf itself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// `get()` instead of a cached val so a locale change at runtime is picked up
// on the next format call instead of staying pinned to the locale at class
// load time.
private val detailTimeFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault())

private fun formatTimestamp(epochMillis: Long): String =
    detailTimeFormatter.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )

private fun formatSize(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
    else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
}

private fun formatRawPreviewSize(previewBytes: Int, originalBytes: Int): String =
    if (previewBytes < originalBytes) {
        "${formatSize(previewBytes)} of ${formatSize(originalBytes)} (truncated)"
    } else {
        formatSize(originalBytes)
    }
