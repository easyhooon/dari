package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariMetrics
import com.easyhooon.dari.inspector.protocol.WireMessageSummary

private val StatusWidth = 84.dp
private val DirectionWidth = 52.dp
private val TagWidth = 108.dp
private val TimeWidth = 104.dp
private val DurationWidth = 72.dp
private val SizeWidth = 72.dp
private val COMPACT_WIDTH = 640.dp

/** Newest-first table. ↑/↓ moves the selection, Esc closes the detail. */
@Composable
fun MessageTable(
    messages: List<WireMessageSummary>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onMove: (Int) -> Unit,
    onClose: () -> Unit,
    emptyHint: Pair<String, String?>,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    BoxWithConstraints(modifier) {
        // Narrow table: drop Tag and Size so the handler name keeps its room.
        val compact = maxWidth < COMPACT_WIDTH
        Column(
            Modifier.fillMaxSize().focusRequester(focus).focusable().onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> onMove(1).let { true }
                    Key.DirectionUp -> onMove(-1).let { true }
                    Key.Escape -> onClose().let { true }
                    else -> false
                }
            },
        ) {
            HeaderRow(compact)
            if (messages.isEmpty()) {
                EmptyState(emptyHint.first, emptyHint.second, Modifier.fillMaxSize())
                return@Column
            }
            val listState = rememberLazyListState()
            val selectedIndex = messages.indexOfFirst { it.id == selectedId }
            LaunchedEffect(selectedIndex) {
                if (selectedIndex >= 0) {
                    val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
                    if (selectedIndex !in visible) listState.animateScrollToItem(selectedIndex)
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(messages, key = { it.id }) { message ->
                    MessageRow(
                        message = message,
                        compact = compact,
                        selected = message.id == selectedId,
                        onClick = {
                            focus.requestFocus()
                            onSelect(message.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(compact: Boolean) {
    val border = Dari.colors.border
    Row(
        Modifier.fillMaxWidth().height(DariMetrics.rowHeight)
            .drawBehind { drawRect(border, Offset(0f, size.height - 1f), Size(size.width, 1f)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("Status", StatusWidth)
        HeaderCell("Dir", DirectionWidth)
        HeaderCell("Handler", null)
        if (!compact) HeaderCell("Tag", TagWidth)
        HeaderCell("Time", TimeWidth, TextAlign.End)
        HeaderCell("Duration", DurationWidth, TextAlign.End)
        if (!compact) HeaderCell("Size", SizeWidth, TextAlign.End)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, width: Dp?, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        style = Dari.type.caption,
        color = Dari.colors.textTertiary,
        textAlign = align,
        modifier = cell(width),
    )
}

@Composable
private fun MessageRow(message: WireMessageSummary, compact: Boolean, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill = when {
        selected -> Dari.colors.selected
        hovered -> Dari.colors.hover
        else -> Color.Transparent
    }
    val accent = Dari.colors.accent
    val failed = statusKind(message.status) == StatusKind.ERROR
    Row(
        Modifier.fillMaxWidth().height(DariMetrics.rowHeight)
            .background(fill)
            .drawBehind { if (selected) drawRect(accent, Offset.Zero, Size(2.dp.toPx(), size.height)) }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(cell(StatusWidth)) { StatusMark(message.status) }
        Text(
            directionLabel(message.direction),
            style = Dari.type.codeCaption,
            color = Dari.colors.textSecondary,
            modifier = cell(DirectionWidth),
        )
        Row(cell(null), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message.displayName ?: message.handlerName,
                style = Dari.type.label,
                color = if (failed) Dari.colors.danger else Dari.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (message.displayName != null) {
                Text(
                    message.handlerName,
                    style = Dari.type.codeCaption,
                    color = Dari.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (!compact) Box(cell(TagWidth)) { message.tag?.let { Badge(it) } }
        NumericCell(formatTime(message.requestTimestamp), TimeWidth)
        NumericCell(durationLabel(message), DurationWidth)
        if (!compact) NumericCell(formatBytes(message.requestSizeBytes + message.responseSizeBytes), SizeWidth)
    }
}

@Composable
private fun RowScope.NumericCell(text: String, width: Dp) {
    Text(
        text,
        style = Dari.type.codeCaption,
        color = Dari.colors.textTertiary,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = cell(width),
    )
}

private fun RowScope.cell(width: Dp?): Modifier =
    (if (width == null) Modifier.weight(1f) else Modifier.width(width)).padding(horizontal = 12.dp)
