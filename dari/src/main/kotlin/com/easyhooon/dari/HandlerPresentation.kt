package com.easyhooon.dari

internal fun handlerLabel(handlerName: String, displayName: String?): String {
    val label = displayName?.takeIf { it.isNotBlank() } ?: return handlerName
    return "$handlerName($label)"
}

/** Handler, display name, tag, request ID and payloads; the desktop runs the same match via `messages.search`. */
internal fun MessageEntry.matchesQuery(query: String): Boolean =
    listOfNotNull(handlerName, displayName, tag, requestId, requestData, responseData)
        .any { it.contains(query, ignoreCase = true) }

internal fun MessageEntry.matchesFilters(
    query: String,
    tag: String?,
    status: MessageStatus?,
    direction: MessageDirection?,
): Boolean =
    (query.isBlank() || matchesQuery(query.trim())) &&
        (tag == null || this.tag == tag) &&
        (status == null || this.status == status) &&
        (direction == null || this.direction == direction)
