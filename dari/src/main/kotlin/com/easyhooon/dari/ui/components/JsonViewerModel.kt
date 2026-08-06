package com.easyhooon.dari.ui.components

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal sealed interface JsonViewerContent {
    data class Structured(val element: JsonElement) : JsonViewerContent

    data class PlainText(val text: String) : JsonViewerContent
}

internal fun parseJsonViewerContent(text: String): JsonViewerContent =
    try {
        JsonViewerContent.Structured(Json.parseToJsonElement(text))
    } catch (_: Exception) {
        JsonViewerContent.PlainText(text)
    }

internal fun JsonElement.collapsedSummary(): String = when (this) {
    is JsonArray -> "[${size} ${if (size == 1) "item" else "items"}]"
    is JsonObject -> "{${size} ${if (size == 1) "field" else "fields"}}"
    else -> toString()
}

internal data class JsonTreeRow(
    val id: String,
    val depth: Int,
    val text: String,
    val containerPath: String? = null,
    val expanded: Boolean? = null,
    val toggleLabel: String? = null,
)

internal fun buildJsonTreeRows(
    element: JsonElement,
    collapsedPaths: Set<String>,
): List<JsonTreeRow> = buildList {
    appendElement(
        element = element,
        path = ROOT_PATH,
        label = "JSON root",
        key = null,
        depth = 0,
        trailingComma = false,
        collapsedPaths = collapsedPaths,
    )
}

private fun MutableList<JsonTreeRow>.appendElement(
    element: JsonElement,
    path: String,
    label: String,
    key: String?,
    depth: Int,
    trailingComma: Boolean,
    collapsedPaths: Set<String>,
) {
    when (element) {
        is JsonArray -> appendArray(
            array = element,
            path = path,
            label = label,
            key = key,
            depth = depth,
            trailingComma = trailingComma,
            collapsedPaths = collapsedPaths,
        )

        is JsonObject -> appendObject(
            jsonObject = element,
            path = path,
            label = label,
            key = key,
            depth = depth,
            trailingComma = trailingComma,
            collapsedPaths = collapsedPaths,
        )

        else -> add(
            JsonTreeRow(
                id = "$path:value",
                depth = depth,
                text = keyPrefix(key) + element + comma(trailingComma),
            ),
        )
    }
}

private fun MutableList<JsonTreeRow>.appendArray(
    array: JsonArray,
    path: String,
    label: String,
    key: String?,
    depth: Int,
    trailingComma: Boolean,
    collapsedPaths: Set<String>,
) {
    val expanded = path !in collapsedPaths
    appendContainerStart(array, path, label, key, depth, trailingComma, expanded, "[")
    if (!expanded) return

    array.forEachIndexed { index, child ->
        appendElement(
            element = child,
            path = "$path/$index",
            label = "item ${index + 1}",
            key = null,
            depth = depth + 1,
            trailingComma = index < array.lastIndex,
            collapsedPaths = collapsedPaths,
        )
    }
    appendContainerEnd(path, depth, trailingComma, "]")
}

private fun MutableList<JsonTreeRow>.appendObject(
    jsonObject: JsonObject,
    path: String,
    label: String,
    key: String?,
    depth: Int,
    trailingComma: Boolean,
    collapsedPaths: Set<String>,
) {
    val expanded = path !in collapsedPaths
    appendContainerStart(jsonObject, path, label, key, depth, trailingComma, expanded, "{")
    if (!expanded) return

    jsonObject.entries.forEachIndexed { index, (childKey, child) ->
        appendElement(
            element = child,
            path = "$path/${childKey.toJsonPointerSegment()}",
            label = childKey,
            key = childKey,
            depth = depth + 1,
            trailingComma = index < jsonObject.size - 1,
            collapsedPaths = collapsedPaths,
        )
    }
    appendContainerEnd(path, depth, trailingComma, "}")
}

private fun MutableList<JsonTreeRow>.appendContainerStart(
    element: JsonElement,
    path: String,
    label: String,
    key: String?,
    depth: Int,
    trailingComma: Boolean,
    expanded: Boolean,
    openingBracket: String,
) {
    add(
        JsonTreeRow(
            id = "$path:container",
            depth = depth,
            text = keyPrefix(key) +
                if (expanded) openingBracket else element.collapsedSummary() + comma(trailingComma),
            containerPath = path,
            expanded = expanded,
            toggleLabel = label,
        ),
    )
}

private fun MutableList<JsonTreeRow>.appendContainerEnd(
    path: String,
    depth: Int,
    trailingComma: Boolean,
    closingBracket: String,
) {
    add(
        JsonTreeRow(
            id = "$path:end",
            depth = depth,
            text = closingBracket + comma(trailingComma),
        ),
    )
}

private fun keyPrefix(key: String?): String = key?.let { "${JsonPrimitive(it)}: " }.orEmpty()

private fun comma(trailingComma: Boolean): String = if (trailingComma) "," else ""

private fun String.toJsonPointerSegment(): String = replace("~", "~0").replace("/", "~1")

private const val ROOT_PATH = "$"
