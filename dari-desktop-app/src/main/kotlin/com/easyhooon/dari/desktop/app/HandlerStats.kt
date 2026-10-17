package com.easyhooon.dari.desktop.app

import com.easyhooon.dari.inspector.protocol.WireMessageSummary

data class HandlerStats(
    val handlerName: String,
    val displayName: String?,
    val direction: String,
    val callCount: Int,
    val errorCount: Int,
    val timedCount: Int,
    val avgMs: Long?,
    val minMs: Long?,
    val maxMs: Long?,
) {
    val isSlow: Boolean get() = (avgMs ?: 0) >= SLOW_HANDLER_THRESHOLD_MS
}

// Same threshold as the in-app inspector's Response Time screen.
const val SLOW_HANDLER_THRESHOLD_MS = 500L

/** Groups messages by handler and direction, slowest average first; handlers without responses go last. */
fun List<WireMessageSummary>.handlerStats(): List<HandlerStats> =
    groupBy { it.handlerName to it.direction }
        .map { (key, group) ->
            // Fire-and-forget and in-progress calls have no response time.
            val durations = group.mapNotNull { message -> message.responseTimestamp?.let { it - message.requestTimestamp } }
            HandlerStats(
                handlerName = key.first,
                // Expects oldest-first input, so the latest display name wins.
                displayName = group.lastOrNull { !it.displayName.isNullOrBlank() }?.displayName,
                direction = key.second,
                callCount = group.size,
                errorCount = group.count { it.status == "ERROR" },
                timedCount = durations.size,
                avgMs = durations.takeIf { it.isNotEmpty() }?.average()?.toLong(),
                minMs = durations.minOrNull(),
                maxMs = durations.maxOrNull(),
            )
        }
        .sortedWith(compareByDescending<HandlerStats, Long?>(nullsFirst()) { it.avgMs }.thenBy { it.handlerName })
