package com.easyhooon.dari

/**
 * Data model representing the full lifecycle of a single bridge message.
 * A request and its response are grouped into one entry.
 */
data class MessageEntry(
    val requestId: String,
    val handlerName: String,
    val direction: MessageDirection,
    val requestData: String? = null,
    val responseData: String? = null,
    val status: MessageStatus = MessageStatus.IN_PROGRESS,
    val requestTimestamp: Long = System.currentTimeMillis(),
    val responseTimestamp: Long? = null,
) {
    val durationMs: Long?
        get() = responseTimestamp?.let { it - requestTimestamp }

    /** Total byte size of request + response data */
    val totalSizeBytes: Int
        get() {
            val requestSize = requestData?.toByteArray(Charsets.UTF_8)?.size ?: 0
            val responseSize = responseData?.toByteArray(Charsets.UTF_8)?.size ?: 0
            return requestSize + responseSize
        }
}