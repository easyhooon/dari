package com.easyhooon.dari.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chucker-style list item.
 * | W->A |  handlerName          |
 * |      |  SUCCESS              |
 * |      |  2:51:31 PM  23 ms   |
 */
@Composable
internal fun MessageListItem(
    entry: MessageEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val (directionText, directionColor) = when (entry.direction) {
            MessageDirection.WEB_TO_APP -> "W\u2192A" to Color(0xFF2196F3)
            MessageDirection.APP_TO_WEB -> "A\u2192W" to Color(0xFF4CAF50)
        }
        Text(
            text = directionText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = directionColor,
            modifier = Modifier.width(56.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.handlerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val (statusText, statusColor) = when (entry.status) {
                MessageStatus.IN_PROGRESS -> "IN PROGRESS" to Color(0xFFFFC107)
                MessageStatus.SUCCESS -> "SUCCESS" to Color(0xFF4CAF50)
                MessageStatus.ERROR -> "ERROR" to Color(0xFFF44336)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                fontWeight = FontWeight.Medium,
            )

            Row {
                val time = SimpleDateFormat("a h:mm:ss", Locale.getDefault())
                    .format(Date(entry.requestTimestamp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.durationMs?.let { duration ->
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${duration} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val sizeBytes = entry.totalSizeBytes
                if (sizeBytes > 0) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = formatSize(sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun formatSize(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
    else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
}