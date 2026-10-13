package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.inspector.protocol.WireMessageSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageList(
    messages: List<WireMessageSummary>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) {
        CenteredHint("No messages yet", modifier)
        return
    }
    val listState = rememberLazyListState()
    // Follow new messages while the user is already at the bottom.
    LaunchedEffect(messages.size) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisible >= messages.size - 2) listState.scrollToItem(messages.lastIndex)
    }
    LazyColumn(modifier, state = listState) {
        items(messages, key = { it.id }) { message ->
            MessageRow(message, selected = message.id == selectedId, onClick = { onSelect(message.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun MessageRow(message: WireMessageSummary, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth().background(background).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(statusColor(message.status), CircleShape))
        Text(directionLabel(message.direction), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
        Column(Modifier.weight(1f)) {
            Text(
                message.displayName ?: message.handlerName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (message.displayName != null) {
                Text(
                    message.handlerName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        message.tag?.let { TagChip(it) }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(message.requestTimestamp), style = MaterialTheme.typography.labelSmall)
            Text(
                durationLabel(message),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TagChip(tag: String) {
    Text(
        tag,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

internal fun directionLabel(direction: String): String = when (direction) {
    "WEB_TO_APP" -> "W→A"
    "APP_TO_WEB" -> "A→W"
    else -> direction
}

internal fun statusColor(status: String): Color = when (status) {
    "SUCCESS" -> Color(0xFF2E9E5B)
    "ERROR" -> Color(0xFFD64545)
    else -> Color(0xFFE0A030)
}

internal fun durationLabel(message: WireMessageSummary): String =
    message.responseTimestamp?.let { "${it - message.requestTimestamp} ms" } ?: message.status.lowercase()

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

internal fun formatTime(epochMillis: Long): String = synchronized(timeFormat) { timeFormat.format(Date(epochMillis)) }
