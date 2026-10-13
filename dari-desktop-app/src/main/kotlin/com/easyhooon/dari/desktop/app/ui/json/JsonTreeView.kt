package com.easyhooon.dari.desktop.app.ui.json

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.desktop.app.ui.CodeBlock
import com.easyhooon.dari.desktop.app.ui.QuietButton
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariColors
import com.easyhooon.dari.desktop.app.ui.theme.DariMetrics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** JSON shows as a foldable tree; anything else falls back to a plain code block. */
@Composable
fun JsonPayload(text: String, foldingEnabled: Boolean, modifier: Modifier = Modifier) {
    when (val content = remember(text) { parseJsonViewerContent(text) }) {
        is JsonViewerContent.Structured -> JsonTree(content.element, foldingEnabled, modifier)
        is JsonViewerContent.PlainText -> CodeBlock(content.text, modifier)
    }
}

@Composable
private fun JsonTree(element: JsonElement, foldingEnabled: Boolean, modifier: Modifier) {
    // Nodes deeper than two levels start folded, as in the in-app viewer.
    var collapsed by remember(element) { mutableStateOf(defaultCollapsedPaths(element)) }
    val rows = remember(element, collapsed, foldingEnabled) { buildJsonTreeRows(element, collapsed, foldingEnabled) }
    val allPaths = remember(element) { allCollapsiblePaths(element) }
    val pretty = remember(element) { prettyJson.encodeToString(JsonElement.serializer(), element) }
    Column(
        modifier.fillMaxWidth()
            .background(Dari.colors.surface, RoundedCornerShape(DariMetrics.radius))
            .border(1.dp, Dari.colors.border, RoundedCornerShape(DariMetrics.radius)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (foldingEnabled && allPaths.isNotEmpty()) {
                QuietButton("Expand all", onClick = { collapsed = emptySet() }, enabled = collapsed.isNotEmpty())
                QuietButton("Collapse all", onClick = { collapsed = allPaths }, enabled = collapsed != allPaths)
            }
            QuietButton("Copy", onClick = { copyToClipboard(pretty) })
        }
        Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
            rows.forEach { row ->
                JsonRow(row) { path ->
                    collapsed = if (path in collapsed) collapsed - path else collapsed + path
                }
            }
        }
    }
}

@Composable
private fun JsonRow(row: JsonTreeRow, onToggle: (String) -> Unit) {
    val path = row.containerPath
    val toggle = if (path != null) {
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            onToggle(path)
        }
    } else {
        Modifier
    }
    Row(Modifier.padding(start = (row.depth * INDENT_DP).dp).then(toggle), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(14.dp)) {
            if (row.expanded != null) {
                Text(
                    if (row.expanded) "▾" else "▸",
                    style = Dari.type.code,
                    color = Dari.colors.textTertiary,
                )
            }
        }
        Text(highlight(row, Dari.colors), style = Dari.type.code)
    }
}

private fun highlight(row: JsonTreeRow, colors: DariColors) = buildAnnotatedString {
    val keyLength = row.key?.let { JsonPrimitive(it).toString().length } ?: 0
    if (keyLength > 0) {
        withStyle(SpanStyle(color = colors.text)) { append(row.text.take(keyLength)) }
        withStyle(SpanStyle(color = colors.textTertiary)) { append(": ") }
    }
    val rest = row.text.drop(if (keyLength > 0) keyLength + 2 else 0)
    val value = rest.removeSuffix(",")
    withStyle(SpanStyle(color = row.tokenType.color(colors, collapsed = row.expanded == false))) { append(value) }
    if (rest.endsWith(",")) withStyle(SpanStyle(color = colors.textTertiary)) { append(",") }
}

private fun JsonTokenType.color(colors: DariColors, collapsed: Boolean): Color = when (this) {
    JsonTokenType.STRING -> colors.syntaxString
    JsonTokenType.NUMBER -> colors.syntaxNumber
    JsonTokenType.BOOLEAN, JsonTokenType.NULL -> colors.syntaxKeyword
    JsonTokenType.CONTAINER -> if (collapsed) colors.textSecondary else colors.textTertiary
    JsonTokenType.PUNCTUATION -> colors.textTertiary
}

/** Every foldable path, for "Collapse all": the container rows of the fully expanded tree. */
internal fun allCollapsiblePaths(element: JsonElement): Set<String> =
    buildJsonTreeRows(element, emptySet()).mapNotNullTo(mutableSetOf()) { it.containerPath }

private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

private val prettyJson = Json { prettyPrint = true }

private const val INDENT_DP = 16
