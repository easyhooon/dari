package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.easyhooon.dari.desktop.app.Appearance
import com.easyhooon.dari.desktop.app.HandlerStats
import com.easyhooon.dari.desktop.app.SLOW_HANDLER_THRESHOLD_MS
import com.easyhooon.dari.desktop.app.handlerStats
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariMetrics
import com.easyhooon.dari.desktop.app.ui.theme.DariTheme
import com.easyhooon.dari.inspector.protocol.WireMessageSummary

private val DirectionWidth = 72.dp
private val CountWidth = 64.dp
private val MillisWidth = 80.dp

/** View → Response Time: per-handler timing of the messages the main window's filters show. */
@Composable
fun StatsWindow(messages: List<WireMessageSummary>, totalCount: Int, appearance: Appearance, onClose: () -> Unit) {
    // visibleMessages() is newest first; handlerStats() wants oldest first.
    val stats = remember(messages) { messages.asReversed().handlerStats() }
    Window(
        onCloseRequest = onClose,
        onPreviewKeyEvent = { handleCloseWindowShortcut(it, onClose = onClose) },
        title = "Response Time",
        state = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center), width = 720.dp, height = 480.dp),
    ) {
        CloseWindowMenu(onClose)
        WindowChrome(appearance)
        DariTheme(appearance) {
            Column(Modifier.fillMaxSize().background(Dari.colors.background)) {
                Row(
                    Modifier.fillMaxWidth().height(DariMetrics.toolbarHeight).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Slowest average first. Averages of $SLOW_HANDLER_THRESHOLD_MS ms or more are highlighted.",
                        style = Dari.type.caption,
                        color = Dari.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(scopeLabel(messages.size, totalCount), style = Dari.type.codeCaption, color = Dari.colors.textTertiary)
                }
                StatsHeaderRow()
                if (stats.isEmpty()) {
                    val (title, hint) = if (totalCount == 0) {
                        "No messages yet" to "Use a bridge in the connected app and its timing appears here."
                    } else {
                        "No matches" to "Change the filter in the main window."
                    }
                    EmptyState(title, hint, Modifier.fillMaxSize())
                    return@Column
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(stats, key = { "${it.direction}:${it.handlerName}" }) { StatsRow(it) }
                }
            }
        }
    }
}

@Composable
private fun StatsHeaderRow() {
    val border = Dari.colors.border
    Row(
        Modifier.fillMaxWidth().height(DariMetrics.rowHeight)
            .drawBehind {
                drawRect(border, Offset.Zero, Size(size.width, 1f))
                drawRect(border, Offset(0f, size.height - 1f), Size(size.width, 1f))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("Direction", DirectionWidth)
        HeaderCell("Handler", null)
        HeaderCell("Calls", CountWidth, TextAlign.End)
        HeaderCell("Errors", CountWidth, TextAlign.End)
        HeaderCell("Avg", MillisWidth, TextAlign.End)
        HeaderCell("Min", MillisWidth, TextAlign.End)
        HeaderCell("Max", MillisWidth, TextAlign.End)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, width: Dp?, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        style = Dari.type.caption,
        color = Dari.colors.textTertiary,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = cell(width),
    )
}

@Composable
private fun StatsRow(stats: HandlerStats) {
    Row(Modifier.fillMaxWidth().height(DariMetrics.rowHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(
            directionLabel(stats.direction),
            style = Dari.type.codeCaption,
            color = Dari.colors.textSecondary,
            modifier = cell(DirectionWidth),
        )
        Row(cell(null), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stats.displayName ?: stats.handlerName,
                style = Dari.type.label,
                color = Dari.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (stats.displayName != null) {
                Text(
                    stats.handlerName,
                    style = Dari.type.codeCaption,
                    color = Dari.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        NumberCell(stats.callCount.toString(), CountWidth)
        NumberCell(
            stats.errorCount.toString(),
            CountWidth,
            if (stats.errorCount > 0) Dari.colors.danger else Dari.colors.textTertiary,
        )
        // "—" means no responses yet: fire-and-forget or still in progress.
        NumberCell(
            stats.avgMs?.let(::formatMillis) ?: "—",
            MillisWidth,
            if (stats.isSlow) Dari.colors.danger else Dari.colors.text,
        )
        NumberCell(stats.minMs?.let(::formatMillis) ?: "—", MillisWidth)
        NumberCell(stats.maxMs?.let(::formatMillis) ?: "—", MillisWidth)
    }
}

@Composable
private fun RowScope.NumberCell(text: String, width: Dp, color: Color = Dari.colors.textTertiary) {
    Text(
        text,
        style = Dari.type.codeCaption,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = cell(width),
    )
}

private fun RowScope.cell(width: Dp?): Modifier =
    (if (width == null) Modifier.weight(1f) else Modifier.width(width)).padding(horizontal = 12.dp)

private fun scopeLabel(shown: Int, total: Int): String {
    val noun = if (total == 1) "message" else "messages"
    return if (shown == total) "$total $noun" else "$shown of $total $noun"
}
