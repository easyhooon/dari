package com.easyhooon.dari.inspector.protocol

import kotlinx.serialization.Serializable

// Enum-like fields are plain strings so an older desktop can still show values it doesn't know.

/** List row: everything except payload bodies. [id] is stable for the life of the app process. */
@Serializable
data class WireMessageSummary(
    val id: Long,
    val requestId: String? = null,
    val handlerName: String,
    val displayName: String? = null,
    val direction: String,
    val tag: String? = null,
    val status: String,
    val requestTimestamp: Long,
    val responseTimestamp: Long? = null,
    val requestSizeBytes: Int,
    val responseSizeBytes: Int,
    /** Page URL without query or fragment; null from older apps or bridges that don't report it. */
    val pageUrl: String? = null,
) {
    /** `scheme://host[:port]` of [pageUrl]. Mirrors `MessageEntry.pageOrigin`, which this module can't see. */
    val pageOrigin: String?
        get() = pageUrl?.let(::originOf)
}

private fun originOf(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val authorityStart = schemeEnd + 3
    val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) url.length else it }
    val host = url.substring(authorityStart, authorityEnd).substringAfterLast('@')
    return url.substring(0, authorityStart).lowercase() + host.lowercase()
}

@Serializable
data class WireMessageDetail(
    val summary: WireMessageSummary,
    val requestData: String? = null,
    val responseData: String? = null,
    val requestDataTruncated: Boolean = false,
    val responseDataTruncated: Boolean = false,
    val requestPayloadMetadata: WirePayloadMetadata? = null,
    val responsePayloadMetadata: WirePayloadMetadata? = null,
)

@Serializable
data class WirePayloadMetadata(
    val contentType: String,
    val originalSizeBytes: Int,
    val decodeStatus: String,
    val rawPreviewBase64: String? = null,
    val rawPreviewSizeBytes: Int? = null,
    val rawPreviewTruncated: Boolean? = null,
)
