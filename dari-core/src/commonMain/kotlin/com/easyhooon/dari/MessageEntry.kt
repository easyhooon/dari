package com.easyhooon.dari

/**
 * Data model representing the full lifecycle of a single bridge message.
 * A request and its response are grouped into one entry.
 *
 * @property id Auto-generated unique identifier for list key and entry lookup.
 * @property requestId Optional external request ID for matching request-response pairs.
 *                     When null, the entry is treated as a standalone (fire-and-forget) message.
 */
data class MessageEntry(
    val id: Long = 0L,
    val requestId: String? = null,
    val handlerName: String,
    val direction: MessageDirection,
    val tag: String? = null,
    val requestData: String? = null,
    val responseData: String? = null,
    val requestDataTruncated: Boolean = false,
    val responseDataTruncated: Boolean = false,
    val status: MessageStatus = MessageStatus.IN_PROGRESS,
    val requestTimestamp: Long = currentTimeMillis(),
    val responseTimestamp: Long? = null,
    val requestPayloadMetadata: MessagePayloadMetadata? = null,
    val responsePayloadMetadata: MessagePayloadMetadata? = null,
) {

    /** Preserves the pre-protobuf constructor for binary compatibility. */
    constructor(
        id: Long,
        requestId: String?,
        handlerName: String,
        direction: MessageDirection,
        tag: String?,
        requestData: String?,
        responseData: String?,
        requestDataTruncated: Boolean,
        responseDataTruncated: Boolean,
        status: MessageStatus,
        requestTimestamp: Long,
        responseTimestamp: Long?,
    ) : this(
        id = id,
        requestId = requestId,
        handlerName = handlerName,
        direction = direction,
        tag = tag,
        requestData = requestData,
        responseData = responseData,
        requestDataTruncated = requestDataTruncated,
        responseDataTruncated = responseDataTruncated,
        status = status,
        requestTimestamp = requestTimestamp,
        responseTimestamp = responseTimestamp,
        requestPayloadMetadata = null,
        responsePayloadMetadata = null,
    )

    /**
     * Secondary constructor preserving the original parameter order for
     * backward compatibility with external positional callers (e.g., Java).
     */
    constructor(
        id: Long,
        requestId: String?,
        handlerName: String,
        direction: MessageDirection,
        requestData: String?,
        responseData: String?,
        status: MessageStatus,
        requestTimestamp: Long,
        responseTimestamp: Long?,
    ) : this(
        id = id,
        requestId = requestId,
        handlerName = handlerName,
        direction = direction,
        tag = null,
        requestData = requestData,
        responseData = responseData,
        status = status,
        requestTimestamp = requestTimestamp,
        responseTimestamp = responseTimestamp,
    )

    val durationMs: Long?
        get() = responseTimestamp?.let { it - requestTimestamp }

    val requestSizeBytes: Int
        get() = requestPayloadMetadata?.originalSizeBytes ?: requestData.utf8Size()

    val responseSizeBytes: Int
        get() = responsePayloadMetadata?.originalSizeBytes ?: responseData.utf8Size()

    /** Total byte size of the original request and response payloads. */
    val totalSizeBytes: Int
        get() = requestSizeBytes + responseSizeBytes

    companion object {
        /**
         * Truncates [data] to [maxLength] characters if it exceeds the limit.
         * Returns a Pair of (truncated data, wasTruncated).
         */
        fun truncateIfNeeded(data: String?, maxLength: Int): Pair<String?, Boolean> {
            if (data == null || data.length <= maxLength) return data to false
            val truncated = data.take(maxLength) + "\n\n...[truncated, original length: ${data.length} chars]"
            return truncated to true
        }
    }
}

private fun String?.utf8Size(): Int = this?.encodeToByteArray()?.size ?: 0
