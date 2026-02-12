package com.easyhooon.dari.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.Dari
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.ui.components.JsonViewer
import com.easyhooon.dari.ui.theme.DariBlue
import com.easyhooon.dari.ui.theme.DariTopBarColors
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity displaying bridge message details.
 * Chucker-style OVERVIEW / REQUEST / RESPONSE tab layout.
 */
class DariDetailActivity : ComponentActivity() {

    @OptIn(ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestId = intent.getStringExtra("requestId") ?: run {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                val entries by Dari.repository.entries.collectAsState()
                val entry = entries.find { it.requestId == requestId }

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
                                entry?.let {
                                    IconButton(onClick = { shareAsText(it) }) {
                                        Icon(Icons.Default.Share, contentDescription = "Share")
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

    private fun shareAsText(entry: MessageEntry) {
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault())
        val direction = when (entry.direction) {
            MessageDirection.WEB_TO_APP -> "Web \u2192 App"
            MessageDirection.APP_TO_WEB -> "App \u2192 Web"
        }
        val requestSize = entry.requestData?.toByteArray(Charsets.UTF_8)?.size ?: 0
        val responseSize = entry.responseData?.toByteArray(Charsets.UTF_8)?.size ?: 0

        val text = buildString {
            appendLine("Handler: ${entry.handlerName}")
            appendLine("Direction: $direction")
            appendLine("Status: ${entry.status}")
            appendLine("Request ID: ${entry.requestId}")
            appendLine()
            appendLine("Request time: ${dateFormat.format(Date(entry.requestTimestamp))}")
            entry.responseTimestamp?.let {
                appendLine("Response time: ${dateFormat.format(Date(it))}")
            }
            entry.durationMs?.let {
                appendLine("Duration: $it ms")
            }
            appendLine()
            appendLine("Request size: ${formatSize(requestSize)}")
            appendLine("Response size: ${formatSize(responseSize)}")
            appendLine("Total size: ${formatSize(requestSize + responseSize)}")
            appendLine()
            appendLine("---------- Request ----------")
            appendLine()
            appendLine(formatJson(entry.requestData) ?: "(empty)")
            appendLine()
            appendLine("---------- Response ----------")
            appendLine()
            appendLine(formatJson(entry.responseData) ?: "(empty)")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share Bridge Message"))
    }

    private fun formatJson(jsonString: String?): String? {
        if (jsonString == null) return null
        return try {
            val element = prettyJson.parseToJsonElement(jsonString)
            prettyJson.encodeToString(JsonElement.serializer(), element)
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
    val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault())
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
        OverviewRow("Request ID", entry.requestId)

        Spacer(modifier = Modifier.height(8.dp))

        OverviewRow("Request time", dateFormat.format(Date(entry.requestTimestamp)))
        entry.responseTimestamp?.let {
            OverviewRow("Response time", dateFormat.format(Date(it)))
        }
        entry.durationMs?.let {
            OverviewRow("Duration", "$it ms")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OverviewRow("Request size", formatSize(requestSize))
        OverviewRow("Response size", formatSize(responseSize))
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

private fun formatSize(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
    else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
}