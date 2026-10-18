package com.easyhooon.dari

/**
 * Data model representing the full lifecycle of a single bridge message.
 * A request and its response are grouped into one entry.
 *
 * @property id Auto-generated unique identifier for list key and entry lookup.
 * @property requestId Optional external request ID for matching request-response pairs.
 *                     When null, the entry is treated as a standalone (fire-and-forget) message.
 * @property handlerName Stable bridge handler identifier.
 * @property displayName Optional human-readable handler description.
 * @property pageUrl Page the WebView showed when the request was captured, without query or fragment.
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
    val displayName: String? = null,
    val pageUrl: String? = null,
) {

    /** Preserves the pre-page-URL constructor signature for binary callers. */
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
        requestPayloadMetadata: MessagePayloadMetadata?,
        responsePayloadMetadata: MessagePayloadMetadata?,
        displayName: String?,
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
        requestPayloadMetadata = requestPayloadMetadata,
        responsePayloadMetadata = responsePayloadMetadata,
        displayName = displayName,
        pageUrl = null,
    )

    /** Preserves the pre-display-name constructor signature for binary callers. */
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
        requestPayloadMetadata: MessagePayloadMetadata?,
        responsePayloadMetadata: MessagePayloadMetadata?,
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
        requestPayloadMetadata = requestPayloadMetadata,
        responsePayloadMetadata = responsePayloadMetadata,
        displayName = null,
    )

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

    /** `scheme://host[:port]` of [pageUrl], or null when there is none. */
    val pageOrigin: String?
        get() = pageUrl?.let(::originOf)

    companion object {
        /**
         * Drops user info, query and fragment, which can carry credentials or tokens, so only origin
         * and path are kept. Returns null for blank input.
         */
        fun sanitizePageUrl(url: String?): String? =
            url?.substringBefore('#')?.substringBefore('?')?.let(::withoutUserInfo)?.takeIf { it.isNotBlank() }

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

private fun originOf(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val authorityStart = schemeEnd + 3
    val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) url.length else it }
    // Drop any user info so credentials never reach a chip or an export.
    val host = url.substring(authorityStart, authorityEnd).substringAfterLast('@')
    return url.substring(0, authorityStart).lowercase() + host.lowercase()
}

private fun withoutUserInfo(url: String): String {
    val authorityStart = url.indexOf("://").takeIf { it > 0 }?.plus(3) ?: return url
    val authorityEnd = url.indexOf('/', authorityStart).let { if (it < 0) url.length else it }
    val at = url.lastIndexOf('@', authorityEnd - 1)
    return if (at < authorityStart) url else url.substring(0, authorityStart) + url.substring(at + 1)
}

private fun String?.utf8Size(): Int = this?.encodeToByteArray()?.size ?: 0
