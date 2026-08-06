package com.easyhooon.dari.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Composable that displays valid JSON as an expandable tree.
 */
@Composable
internal fun JsonViewer(
    jsonString: String,
    foldingEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val content = remember(jsonString) { parseJsonViewerContent(jsonString) }
    when (content) {
        is JsonViewerContent.Structured -> JsonTreeViewer(
            element = content.element,
            stateKey = jsonString,
            foldingEnabled = foldingEnabled,
            modifier = modifier,
        )

        is JsonViewerContent.PlainText -> Box(
            modifier = modifier.verticalScroll(rememberScrollState()),
        ) {
            CodeViewer(content.text)
        }
    }
}

@Composable
private fun JsonTreeViewer(
    element: JsonElement,
    stateKey: String,
    foldingEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val collapsedPaths = remember(stateKey, foldingEnabled) {
        mutableStateMapOf<String, Unit>().apply {
            if (foldingEnabled) {
                defaultCollapsedPaths(element).forEach { path -> this[path] = Unit }
            }
        }
    }
    val rows = buildJsonTreeRows(
        element = element,
        collapsedPaths = collapsedPaths.keys,
        foldingEnabled = foldingEnabled,
    )
    val horizontalScrollState = rememberScrollState()
    val shape = RoundedCornerShape(8.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val viewportWidth = maxWidth
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = viewportWidth)
                    .padding(vertical = 8.dp),
            ) {
                items(
                    items = rows,
                    key = JsonTreeRow::id,
                    contentType = { if (it.containerPath != null) "container" else "value" },
                ) { row ->
                    JsonTreeLine(
                        row = row,
                        showDisclosureGutter = foldingEnabled,
                        onToggle = { path, expanded ->
                            if (expanded) {
                                collapsedPaths[path] = Unit
                            } else {
                                collapsedPaths.remove(path)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonTreeLine(
    row: JsonTreeRow,
    showDisclosureGutter: Boolean,
    onToggle: (path: String, expanded: Boolean) -> Unit,
) {
    val containerPath = row.containerPath
    val expanded = row.expanded
    val toggleDescription = if (expanded == true) {
        "Collapse ${row.toggleLabel}"
    } else {
        "Expand ${row.toggleLabel}"
    }
    val interactionModifier = if (containerPath != null && expanded != null) {
        Modifier
            .clickable(role = Role.Button) { onToggle(containerPath, expanded) }
            .semantics { contentDescription = toggleDescription }
    } else {
        Modifier
    }
    val syntaxColors = jsonSyntaxColors()
    val highlightedText = remember(row, syntaxColors) {
        row.toAnnotatedString(syntaxColors)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(interactionModifier)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Spacer(modifier = Modifier.width((row.depth * JSON_INDENT_DP).dp))
        if (expanded != null) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(JSON_DISCLOSURE_ICON_SIZE),
            )
        } else if (showDisclosureGutter && row.depth > 0) {
            Spacer(modifier = Modifier.size(JSON_DISCLOSURE_ICON_SIZE))
        }
        Text(
            text = highlightedText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            softWrap = false,
        )
    }
}

private data class JsonSyntaxColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val boolean: Color,
    val nullValue: Color,
    val container: Color,
    val punctuation: Color,
)

@Composable
private fun jsonSyntaxColors(): JsonSyntaxColors {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f
    return JsonSyntaxColors(
        key = if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0),
        string = if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32),
        number = if (isDark) Color(0xFFCE93D8) else Color(0xFF7B1FA2),
        boolean = if (isDark) Color(0xFFFFCC80) else Color(0xFFEF6C00),
        nullValue = if (isDark) Color(0xFFBDBDBD) else Color(0xFF757575),
        container = colorScheme.onSurface,
        punctuation = colorScheme.onSurfaceVariant,
    )
}

private fun JsonTreeRow.toAnnotatedString(colors: JsonSyntaxColors): AnnotatedString = buildAnnotatedString {
    val keyText = key?.let { JsonPrimitive(it).toString() }
    val prefix = keyText?.let { "$it: " }.orEmpty()
    if (keyText != null) {
        withStyle(SpanStyle(color = colors.key)) { append(keyText) }
        withStyle(SpanStyle(color = colors.punctuation)) { append(": ") }
    }

    val valueWithComma = text.removePrefix(prefix)
    val hasTrailingComma = valueWithComma.endsWith(',')
    val value = if (hasTrailingComma) valueWithComma.dropLast(1) else valueWithComma
    withStyle(SpanStyle(color = colors.colorFor(tokenType))) { append(value) }
    if (hasTrailingComma) {
        withStyle(SpanStyle(color = colors.punctuation)) { append(',') }
    }
}

private fun JsonSyntaxColors.colorFor(tokenType: JsonTokenType): Color = when (tokenType) {
    JsonTokenType.PUNCTUATION -> punctuation
    JsonTokenType.CONTAINER -> container
    JsonTokenType.STRING -> string
    JsonTokenType.NUMBER -> number
    JsonTokenType.BOOLEAN -> boolean
    JsonTokenType.NULL -> nullValue
}

@Composable
internal fun CodeViewer(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp)
            .horizontalScroll(rememberScrollState()),
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        ),
    )
}

private val JSON_DISCLOSURE_ICON_SIZE = 14.dp
private const val JSON_INDENT_DP = 14
