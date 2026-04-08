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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.Dari
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.export.DariExporter
import com.easyhooon.dari.export.ExportFormat
import com.easyhooon.dari.ui.components.JsonViewer
import androidx.compose.foundation.isSystemInDarkTheme
import com.easyhooon.dari.ui.theme.ApplyDariSystemBars
import com.easyhooon.dari.ui.theme.DariBlue
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
            val isDark = darkMode ?: isSystemInDarkTheme()
            ApplyDariSystemBars(isDark)
            DariTheme(darkTheme = darkMode) {
                val entries by Dari.repository.entries.collectAsStateWithLifecycle()
                val entry = entries.find { it.id == id }
                var shareMenuExpanded by remember { mutableStateOf(false) }
                var downloadMenuExpanded by remember { mutableStateOf(false) }

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
                                        IconButton(onClick = { shareMenuExpanded = true }) {
                                            Icon(Icons.Default.Share, contentDescription = "Share")
                                        }
                                        DropdownMenu(
                                            expanded = shareMenuExpanded,
                                            onDismissRequest = { shareMenuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Share as TEXT") },
                                                onClick = {
                                                    shareMenuExpanded = false
                                                    DariExporter.shareSingleAsPlainText(
                                                        this@DariDetailActivity,
                                                        current,
                                                    )
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Share as JSON") },
                                                onClick = {
                                                    shareMenuExpanded = false
                                                    lifecycleScope.launch {
                                                        DariExporter.exportAndShareSingle(
                                                            this@DariDetailActivity,
                                                            current,
                                                            ExportFormat.JSON,
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    Box {
                                        IconButton(onClick = { downloadMenuExpanded = true }) {
                                            Icon(Icons.Default.Download, contentDescription = "Save")
                                        }
                                        DropdownMenu(
                                            expanded = downloadMenuExpanded,
                                            onDismissRequest = { downloadMenuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Save as TEXT") },
                                                onClick = {
                                                    downloadMenuExpanded = false
                                                    launchSave(current, ExportFormat.TEXT)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Save as JSON") },
                                                onClick = {
                                                    downloadMenuExpanded = false
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
                            DetailTabs(entry)
                        }
                    }

                }
            }
        }
    }
}

private val TAB_TITLES = listOf("OVERVIEW", "REQUEST", "RESPONSE")

@Composable
private fun DetailTabs(entry: MessageEntry) {
    val pagerState = rememberPagerState(pageCount = { TAB_TITLES.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = DariBlue,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = Color.White,
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
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.6f)
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
                1 -> DataTab(entry.requestData)
                2 -> DataTab(entry.responseData)
            }
        }
    }
}

@Composable
private fun OverviewTab(entry: MessageEntry) {
    val requestSize = entry.requestData?.toByteArray(Charsets.UTF_8)?.size ?: 0
    val responseSize = entry.responseData?.toByteArray(Charsets.UTF_8)?.size ?: 0

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

@Composable
private fun DataTab(data: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (data.isNullOrBlank()) {
            Text(
                text = "(body is empty)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            JsonViewer(jsonString = data)
        }
    }
}

private val DETAIL_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault())

private fun formatTimestamp(epochMillis: Long): String =
    DETAIL_TIME_FORMATTER.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )

private fun formatSize(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
    else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
}
