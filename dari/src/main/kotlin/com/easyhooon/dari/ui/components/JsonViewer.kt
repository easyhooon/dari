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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonElement

/**
 * Composable that displays valid JSON as an expandable tree.
 */
@Composable
internal fun JsonViewer(
    jsonString: String,
    modifier: Modifier = Modifier,
) {
    val content = remember(jsonString) { parseJsonViewerContent(jsonString) }
    when (content) {
        is JsonViewerContent.Structured -> JsonTreeViewer(
            element = content.element,
            stateKey = jsonString,
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
    modifier: Modifier = Modifier,
) {
    val collapsedPaths = remember(stateKey) { mutableStateMapOf<String, Unit>() }
    val rows = buildJsonTreeRows(element, collapsedPaths.keys)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(interactionModifier)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Spacer(modifier = Modifier.width((row.depth * 16).dp))
        if (expanded != null) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Spacer(modifier = Modifier.size(16.dp))
        }
        Text(
            text = row.text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            softWrap = false,
        )
    }
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
