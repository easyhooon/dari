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
)

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
