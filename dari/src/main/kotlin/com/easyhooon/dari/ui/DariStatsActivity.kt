package com.easyhooon.dari.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easyhooon.dari.Dari
import com.easyhooon.dari.HandlerStats
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.SLOW_HANDLER_THRESHOLD_MS
import com.easyhooon.dari.handlerLabel
import com.easyhooon.dari.handlerStats
import com.easyhooon.dari.ui.theme.ApplyDariSystemBars
import com.easyhooon.dari.ui.theme.Blue500
import com.easyhooon.dari.ui.theme.DariTheme
import com.easyhooon.dari.ui.theme.DariTopBarColors
import com.easyhooon.dari.ui.theme.Green500
import com.easyhooon.dari.ui.theme.palette

/** Response time statistics per handler, scoped to the tag and origin selected on the list screen. */
class DariStatsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tag = intent.getStringExtra(EXTRA_TAG)
        val origin = intent.getStringExtra(EXTRA_ORIGIN)

        setContent {
            val darkMode by Dari.preferences.darkModeFlow().collectAsStateWithLifecycle(
                initialValue = Dari.preferences.darkMode,
            )
            ApplyDariSystemBars(darkMode ?: isSystemInDarkTheme())
            DariTheme(darkTheme = darkMode) {
                val entries by Dari.repository.entries.collectAsStateWithLifecycle()
                val stats = remember(entries, tag, origin) {
                    entries.filter { (tag == null || it.tag == tag) && (origin == null || it.pageOrigin == origin) }
                        .handlerStats()
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("Response Time")
                                    listOfNotNull(tag, origin).takeIf { it.isNotEmpty() }?.let {
                                        Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        if (stats.isEmpty()) {
                            Text(
                                text = "No messages captured yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            LazyColumn {
                                item {
                                    Text(
                                        text = "Slowest average first. Averages of ${SLOW_HANDLER_THRESHOLD_MS} ms or more are highlighted.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                    HorizontalDivider()
                                }
                                items(stats, key = { "${it.direction}:${it.handlerName}" }) { stat ->
                                    HandlerStatsRow(stat)
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    internal companion object {
        const val EXTRA_TAG = "tag"
        const val EXTRA_ORIGIN = "origin"
    }
}

@Composable
private fun HandlerStatsRow(stats: HandlerStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val (directionText, directionColor) = when (stats.direction) {
            MessageDirection.WEB_TO_APP -> "W→A" to Blue500
            MessageDirection.APP_TO_WEB -> "A→W" to Green500
        }
        Text(
            text = directionText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = directionColor,
            modifier = Modifier.width(56.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = handlerLabel(stats.handlerName, stats.displayName),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stats.avgMs?.let { "$it ms" } ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (stats.isSlow) {
                        MessageStatus.ERROR.palette.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatText(countLabel(stats.callCount, "call"))
                if (stats.errorCount > 0) {
                    Text(
                        text = countLabel(stats.errorCount, "error"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MessageStatus.ERROR.palette.onSurface,
                    )
                }
                if (stats.minMs != null && stats.maxMs != null) {
                    StatText("min ${stats.minMs} ms")
                    StatText("max ${stats.maxMs} ms")
                } else {
                    StatText("no responses")
                }
            }
        }
    }
}

@Composable
private fun StatText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun countLabel(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"
