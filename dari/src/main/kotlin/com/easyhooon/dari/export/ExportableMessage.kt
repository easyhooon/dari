package com.easyhooon.dari.export

import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ExportableMessage(
    val id: Long,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("handler_name") val handlerName: String,
    @SerialName("display_name") val displayName: String? = null,
    val direction: String,
    val tag: String? = null,
    @SerialName("request_data") val requestData: String? = null,
    @SerialName("response_data") val responseData: String? = null,
    @SerialName("request_data_truncated") val requestDataTruncated: Boolean = false,
    @SerialName("response_data_truncated") val responseDataTruncated: Boolean = false,
    @SerialName("request_content_type") val requestContentType: String? = null,
    @SerialName("request_original_size_bytes") val requestOriginalSizeBytes: Int? = null,
    @SerialName("request_decode_status") val requestDecodeStatus: String? = null,
    @SerialName("request_raw_preview_base64") val requestRawPreviewBase64: String? = null,
    @SerialName("request_raw_preview_size_bytes") val requestRawPreviewSizeBytes: Int? = null,
    @SerialName("request_raw_preview_truncated") val requestRawPreviewTruncated: Boolean? = null,
    @SerialName("response_content_type") val responseContentType: String? = null,
    @SerialName("response_original_size_bytes") val responseOriginalSizeBytes: Int? = null,
    @SerialName("response_decode_status") val responseDecodeStatus: String? = null,
    @SerialName("response_raw_preview_base64") val responseRawPreviewBase64: String? = null,
    @SerialName("response_raw_preview_size_bytes") val responseRawPreviewSizeBytes: Int? = null,
    @SerialName("response_raw_preview_truncated") val responseRawPreviewTruncated: Boolean? = null,
    val status: String,
    @SerialName("request_timestamp") val requestTimestamp: Long,
    @SerialName("response_timestamp") val responseTimestamp: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
)

internal fun MessageEntry.toExportable(): ExportableMessage = ExportableMessage(
    id = id,
    requestId = requestId,
    handlerName = handlerName,
    displayName = displayName,
    direction = when (direction) {
        MessageDirection.WEB_TO_APP -> "WEB_TO_APP"
        MessageDirection.APP_TO_WEB -> "APP_TO_WEB"
    },
    tag = tag,
    requestData = requestData,
    responseData = responseData,
    requestDataTruncated = requestDataTruncated,
    responseDataTruncated = responseDataTruncated,
    requestContentType = requestPayloadMetadata?.contentType?.name,
    requestOriginalSizeBytes = requestPayloadMetadata?.originalSizeBytes,
    requestDecodeStatus = requestPayloadMetadata?.decodeStatus?.name,
    requestRawPreviewBase64 = requestPayloadMetadata?.rawPreview?.base64,
    requestRawPreviewSizeBytes = requestPayloadMetadata?.rawPreview?.previewSizeBytes,
    requestRawPreviewTruncated = requestPayloadMetadata?.rawPreview?.truncated,
    responseContentType = responsePayloadMetadata?.contentType?.name,
    responseOriginalSizeBytes = responsePayloadMetadata?.originalSizeBytes,
    responseDecodeStatus = responsePayloadMetadata?.decodeStatus?.name,
    responseRawPreviewBase64 = responsePayloadMetadata?.rawPreview?.base64,
    responseRawPreviewSizeBytes = responsePayloadMetadata?.rawPreview?.previewSizeBytes,
    responseRawPreviewTruncated = responsePayloadMetadata?.rawPreview?.truncated,
    status = when (status) {
        MessageStatus.IN_PROGRESS -> "IN_PROGRESS"
        MessageStatus.SUCCESS -> "SUCCESS"
        MessageStatus.ERROR -> "ERROR"
    },
    requestTimestamp = requestTimestamp,
    responseTimestamp = responseTimestamp,
    durationMs = durationMs,
)
