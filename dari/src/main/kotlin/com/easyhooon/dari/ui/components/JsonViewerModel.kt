package com.easyhooon.dari.ui.components

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

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
    val key: String? = null,
    val tokenType: JsonTokenType = JsonTokenType.PUNCTUATION,
    val containerPath: String? = null,
    val expanded: Boolean? = null,
    val toggleLabel: String? = null,
)

internal enum class JsonTokenType {
    PUNCTUATION,
    CONTAINER,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
}

internal fun buildJsonTreeRows(
    element: JsonElement,
    collapsedPaths: Set<String>,
    foldingEnabled: Boolean = true,
): List<JsonTreeRow> = buildList {
    appendElement(
        element = element,
        path = ROOT_PATH,
        label = "JSON root",
        key = null,
        depth = 0,
        trailingComma = false,
        collapsedPaths = collapsedPaths,
        foldingEnabled = foldingEnabled,
    )
}

internal fun defaultCollapsedPaths(element: JsonElement): Set<String> = buildSet {
    collectCollapsiblePaths(
        element = element,
        path = ROOT_PATH,
        depth = 0,
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
    foldingEnabled: Boolean,
) {
    when (element) {
        is JsonArray -> if (element.isEmpty()) {
            appendEmptyContainer(path, key, depth, trailingComma, "[]")
        } else {
            appendArray(
                array = element,
                path = path,
                label = label,
                key = key,
                depth = depth,
                trailingComma = trailingComma,
                collapsedPaths = collapsedPaths,
                foldingEnabled = foldingEnabled,
            )
        }

        is JsonObject -> if (element.isEmpty()) {
            appendEmptyContainer(path, key, depth, trailingComma, "{}")
        } else {
            appendObject(
                jsonObject = element,
                path = path,
                label = label,
                key = key,
                depth = depth,
                trailingComma = trailingComma,
                collapsedPaths = collapsedPaths,
                foldingEnabled = foldingEnabled,
            )
        }

        else -> add(
            JsonTreeRow(
                id = "$path:value",
                depth = depth,
                text = keyPrefix(key) + element + comma(trailingComma),
                key = key,
                tokenType = element.tokenType(),
            ),
        )
    }
}

private fun MutableList<JsonTreeRow>.appendEmptyContainer(
    path: String,
    key: String?,
    depth: Int,
    trailingComma: Boolean,
    brackets: String,
) {
    add(
        JsonTreeRow(
            id = "$path:value",
            depth = depth,
            text = keyPrefix(key) + brackets + comma(trailingComma),
            key = key,
            tokenType = JsonTokenType.CONTAINER,
        ),
    )
}

private fun MutableList<JsonTreeRow>.appendArray(
    array: JsonArray,
    path: String,
    label: String,
    key: String?,
    depth: Int,
    trailingComma: Boolean,
    collapsedPaths: Set<String>,
    foldingEnabled: Boolean,
) {
    val collapsible = foldingEnabled && path != ROOT_PATH
    val expanded = !collapsible || path !in collapsedPaths
    appendContainerStart(array, path, label, key, depth, trailingComma, expanded, collapsible, "[")
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
            foldingEnabled = foldingEnabled,
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
    foldingEnabled: Boolean,
) {
    val collapsible = foldingEnabled && path != ROOT_PATH
    val expanded = !collapsible || path !in collapsedPaths
    appendContainerStart(jsonObject, path, label, key, depth, trailingComma, expanded, collapsible, "{")
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
            foldingEnabled = foldingEnabled,
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
    collapsible: Boolean,
    openingBracket: String,
) {
    add(
        JsonTreeRow(
            id = "$path:container",
            depth = depth,
            text = keyPrefix(key) +
                if (expanded) openingBracket else element.collapsedSummary() + comma(trailingComma),
            key = key,
            tokenType = JsonTokenType.CONTAINER,
            containerPath = path.takeIf { collapsible },
            expanded = expanded.takeIf { collapsible },
            toggleLabel = label.takeIf { collapsible },
        ),
    )
}

private fun MutableSet<String>.collectCollapsiblePaths(
    element: JsonElement,
    path: String,
    depth: Int,
) {
    when (element) {
        is JsonArray -> {
            if (element.isEmpty()) return
            if (depth >= DEFAULT_COLLAPSE_DEPTH) add(path)
            element.forEachIndexed { index, child ->
                collectCollapsiblePaths(child, "$path/$index", depth + 1)
            }
        }

        is JsonObject -> {
            if (element.isEmpty()) return
            if (depth >= DEFAULT_COLLAPSE_DEPTH) add(path)
            element.forEach { (childKey, child) ->
                collectCollapsiblePaths(
                    element = child,
                    path = "$path/${childKey.toJsonPointerSegment()}",
                    depth = depth + 1,
                )
            }
        }

        else -> Unit
    }
}

private fun JsonElement.tokenType(): JsonTokenType = when (this) {
    JsonNull -> JsonTokenType.NULL
    is JsonPrimitive -> when {
        isString -> JsonTokenType.STRING
        booleanOrNull != null -> JsonTokenType.BOOLEAN
        else -> JsonTokenType.NUMBER
    }

    else -> JsonTokenType.CONTAINER
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

private const val DEFAULT_COLLAPSE_DEPTH = 2
private const val ROOT_PATH = "$"
