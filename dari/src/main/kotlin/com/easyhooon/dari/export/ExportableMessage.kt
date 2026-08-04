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
    val direction: String,
    val tag: String? = null,
    @SerialName("request_data") val requestData: String? = null,
    @SerialName("response_data") val responseData: String? = null,
    @SerialName("request_data_truncated") val requestDataTruncated: Boolean = false,
    @SerialName("response_data_truncated") val responseDataTruncated: Boolean = false,
    @SerialName("request_content_type") val requestContentType: String? = null,
    @SerialName("request_original_size_bytes") val requestOriginalSizeBytes: Int? = null,
    @SerialName("request_decode_status") val requestDecodeStatus: String? = null,
    @SerialName("response_content_type") val responseContentType: String? = null,
    @SerialName("response_original_size_bytes") val responseOriginalSizeBytes: Int? = null,
    @SerialName("response_decode_status") val responseDecodeStatus: String? = null,
    val status: String,
    @SerialName("request_timestamp") val requestTimestamp: Long,
    @SerialName("response_timestamp") val responseTimestamp: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
)

internal fun MessageEntry.toExportable(): ExportableMessage = ExportableMessage(
    id = id,
    requestId = requestId,
    handlerName = handlerName,
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
    responseContentType = responsePayloadMetadata?.contentType?.name,
    responseOriginalSizeBytes = responsePayloadMetadata?.originalSizeBytes,
    responseDecodeStatus = responsePayloadMetadata?.decodeStatus?.name,
    status = when (status) {
        MessageStatus.IN_PROGRESS -> "IN_PROGRESS"
        MessageStatus.SUCCESS -> "SUCCESS"
        MessageStatus.ERROR -> "ERROR"
    },
    requestTimestamp = requestTimestamp,
    responseTimestamp = responseTimestamp,
    durationMs = durationMs,
)
