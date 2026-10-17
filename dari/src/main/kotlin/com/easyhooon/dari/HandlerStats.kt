package com.easyhooon.dari

internal data class HandlerStats(
    val handlerName: String,
    val displayName: String?,
    val direction: MessageDirection,
    val callCount: Int,
    val errorCount: Int,
    val timedCount: Int,
    val avgMs: Long?,
    val minMs: Long?,
    val maxMs: Long?,
) {
    val isSlow: Boolean get() = (avgMs ?: 0) >= SLOW_HANDLER_THRESHOLD_MS
}

internal const val SLOW_HANDLER_THRESHOLD_MS = 500L

/** Groups entries by handler and direction, slowest average first; untimed handlers go last. */
internal fun List<MessageEntry>.handlerStats(): List<HandlerStats> =
    groupBy { it.handlerName to it.direction }
        .map { (key, group) ->
            // Fire-and-forget and in-progress calls have no response time.
            val durations = group.mapNotNull { it.durationMs }
            HandlerStats(
                handlerName = key.first,
                displayName = group.lastOrNull { !it.displayName.isNullOrBlank() }?.displayName,
                direction = key.second,
                callCount = group.size,
                errorCount = group.count { it.status == MessageStatus.ERROR },
                timedCount = durations.size,
                avgMs = durations.takeIf { it.isNotEmpty() }?.average()?.toLong(),
                minMs = durations.minOrNull(),
                maxMs = durations.maxOrNull(),
            )
        }
        .sortedWith(compareByDescending<HandlerStats, Long?>(nullsFirst()) { it.avgMs }.thenBy { it.handlerName })
