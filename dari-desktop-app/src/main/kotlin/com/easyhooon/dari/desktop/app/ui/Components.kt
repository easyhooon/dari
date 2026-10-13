package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariMetrics
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

enum class StatusKind { PENDING, SUCCESS, ERROR }

fun statusKind(status: String): StatusKind = when (status) {
    "SUCCESS" -> StatusKind.SUCCESS
    "ERROR" -> StatusKind.ERROR
    else -> StatusKind.PENDING
}

/** Small square mark plus a word; colour never carries the meaning alone. */
@Composable
fun StatusMark(status: String, modifier: Modifier = Modifier) {
    val kind = statusKind(status)
    val color = when (kind) {
        StatusKind.SUCCESS -> Dari.colors.success
        StatusKind.ERROR -> Dari.colors.danger
        StatusKind.PENDING -> Dari.colors.textTertiary
    }
    val shape = RoundedCornerShape(1.dp)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val mark = Modifier.size(5.dp)
        Box(if (kind == StatusKind.PENDING) mark.border(1.dp, color, shape) else mark.background(color, shape))
        Text(
            when (kind) {
                StatusKind.SUCCESS -> "ok"
                StatusKind.ERROR -> "error"
                StatusKind.PENDING -> "pending"
            },
            style = Dari.type.codeCaption,
            color = color,
        )
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Dari.type.codeCaption,
        color = Dari.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.background(Dari.colors.selected, RoundedCornerShape(DariMetrics.radius))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** 6dp dot: filled when connected, a ring when not. */
@Composable
fun ConnectionDot(connected: Boolean) {
    val modifier = Modifier.size(6.dp)
    Box(
        if (connected) {
            modifier.background(Dari.colors.success, CircleShape)
        } else {
            modifier.border(1.dp, Dari.colors.textTertiary, CircleShape)
        },
    )
}

/** Borderless text button; [danger] turns it red on hover, as for destructive actions. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val (fg, bg) = when {
        !enabled -> Dari.colors.textTertiary.copy(alpha = 0.45f) to Color.Transparent
        hovered && danger -> Dari.colors.danger to Dari.colors.danger.copy(alpha = 0.12f)
        hovered -> Dari.colors.text to Dari.colors.hover
        else -> Dari.colors.textSecondary to Color.Transparent
    }
    Box(
        Modifier.height(DariMetrics.controlHeight)
            .background(bg, RoundedCornerShape(DariMetrics.radius))
            .hoverable(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Dari.type.label, color = fg)
    }
}

/** Filled Dari-blue button for the one action worth noticing, such as an available update. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, showDownloadIcon: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // The background colour reads well on the accent in both themes (white on blue, dark on light blue).
    val content = Dari.colors.background
    Row(
        Modifier.height(DariMetrics.controlHeight)
            .background(Dari.colors.accent.copy(alpha = if (hovered) 0.85f else 1f), RoundedCornerShape(DariMetrics.radius))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDownloadIcon) DownloadIcon(content)
        Text(text, style = Dari.type.label.copy(fontWeight = FontWeight.SemiBold), color = content)
    }
}

/** Arrow into a tray, drawn so the app needs no icon font. */
@Composable
private fun DownloadIcon(color: Color) {
    Canvas(Modifier.size(12.dp)) {
        val stroke = 1.6.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w / 2, 0f), Offset(w / 2, h * 0.68f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.22f, h * 0.42f), Offset(w / 2, h * 0.7f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.78f, h * 0.42f), Offset(w / 2, h * 0.7f), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, h * 0.95f), Offset(w, h * 0.95f), stroke, StrokeCap.Round)
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Dari.type.label.copy(color = Dari.colors.text),
        cursorBrush = SolidColor(Dari.colors.accent),
        modifier = modifier.height(DariMetrics.controlHeight),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize()
                    .background(Dari.colors.surface, RoundedCornerShape(DariMetrics.radius))
                    .border(1.dp, Dari.colors.borderStrong, RoundedCornerShape(DariMetrics.radius))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = Dari.type.label, color = Dari.colors.textTertiary)
                inner()
            }
        },
    )
}

/** Compact dropdown that looks like a quiet button with a chevron. */
@Composable
fun <T> Picker(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    isSelected: (T) -> Boolean = { false },
    leading: @Composable (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(modifier) {
        Row(
            Modifier.height(DariMetrics.controlHeight)
                .background(if (hovered) Dari.colors.hover else Color.Transparent, RoundedCornerShape(DariMetrics.radius))
                .hoverable(interaction)
                .clickable(enabled = options.isNotEmpty(), interactionSource = interaction, indication = null) {
                    expanded = true
                }
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(
                label,
                style = Dari.type.label,
                color = Dari.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
            secondary?.let { Text(it, style = Dari.type.caption, color = Dari.colors.textTertiary, maxLines = 1) }
            Text("▾", style = Dari.type.caption, color = Dari.colors.textTertiary)
        }
        DariMenu(expanded = expanded, onDismiss = { expanded = false }) {
            options.forEach { option ->
                DariMenuItem(
                    text = optionLabel(option),
                    checked = isSelected(option),
                    onClick = {
                        expanded = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

/** Compact popup menu in Dari's palette instead of Material's tinted default. */
@Composable
fun DariMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(MENU_RADIUS),
        containerColor = Dari.colors.background,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Dari.colors.border),
        modifier = Modifier.padding(vertical = 4.dp).widthIn(min = 180.dp),
        content = content,
    )
}

/** 28dp row with a check column, so selected and unselected labels line up. */
@Composable
fun DariMenuItem(text: String, onClick: () -> Unit, checked: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(MENU_ROW_HEIGHT).padding(horizontal = 4.dp)
            .background(if (hovered) Dari.colors.hover else Color.Transparent, RoundedCornerShape(DariMetrics.radius))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(18.dp)) {
            if (checked) Text("✓", style = Dari.type.label, color = Dari.colors.text)
        }
        Text(text, style = Dari.type.label, color = Dari.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Code block with a copy button that appears on hover. */
@Composable
fun CodeBlock(text: String, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }
    Box(
        modifier.fillMaxWidth()
            .background(Dari.colors.surface, RoundedCornerShape(DariMetrics.radius))
            .border(1.dp, Dari.colors.border, RoundedCornerShape(DariMetrics.radius))
            .hoverable(interaction),
    ) {
        Text(
            text,
            style = Dari.type.code,
            color = Dari.colors.text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (hovered || copied) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                QuietButton(
                    text = if (copied) "copied" else "copy",
                    onClick = {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                        copied = true
                    },
                )
            }
        }
    }
}

/** Two-column key/value list: keys in the UI face, values in mono. */
@Composable
fun Pairs(pairs: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pairs.forEach { (key, value) ->
            Row {
                Text(
                    key,
                    style = Dari.type.label,
                    color = Dari.colors.textTertiary,
                    modifier = Modifier.widthIn(min = 120.dp).padding(end = 12.dp),
                )
                Text(value, style = Dari.type.code, color = Dari.colors.text)
            }
        }
    }
}

/** At most two lines: what is missing, and what to do next. */
@Composable
fun EmptyState(title: String, hint: String?, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Dari.type.body, color = Dari.colors.textSecondary)
            hint?.let { Text(it, style = Dari.type.caption, color = Dari.colors.textTertiary) }
        }
    }
}

private const val COPIED_FEEDBACK_MS = 1_400L
private val MENU_RADIUS = 8.dp
private val MENU_ROW_HEIGHT = 28.dp
