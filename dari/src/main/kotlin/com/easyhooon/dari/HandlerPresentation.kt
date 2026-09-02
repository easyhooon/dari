package com.easyhooon.dari

internal fun handlerLabel(handlerName: String, displayName: String?): String {
    val label = displayName?.takeIf { it.isNotBlank() } ?: return handlerName
    return "$handlerName($label)"
}

internal fun MessageEntry.matchesHandlerQuery(query: String): Boolean =
    handlerName.contains(query, ignoreCase = true) ||
        displayName?.contains(query, ignoreCase = true) == true
