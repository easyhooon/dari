package com.easyhooon.dari.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessagePayloadMetadata
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.PayloadContentType
import com.easyhooon.dari.PayloadDecodeStatus

@Entity(tableName = "messages")
internal data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val requestId: String?,
    val handlerName: String,
    val direction: MessageDirection,
    val tag: String? = null,
    val requestData: String? = null,
    val responseData: String? = null,
    val requestDataTruncated: Boolean = false,
    val responseDataTruncated: Boolean = false,
    val requestContentType: PayloadContentType? = null,
    val requestOriginalSizeBytes: Int? = null,
    val requestDecodeStatus: PayloadDecodeStatus? = null,
    val responseContentType: PayloadContentType? = null,
    val responseOriginalSizeBytes: Int? = null,
    val responseDecodeStatus: PayloadDecodeStatus? = null,
    val status: MessageStatus = MessageStatus.IN_PROGRESS,
    val requestTimestamp: Long = System.currentTimeMillis(),
    val responseTimestamp: Long? = null,
)

internal fun MessageEntry.toEntity(): MessageEntity = MessageEntity(
    requestId = requestId,
    handlerName = handlerName,
    direction = direction,
    tag = tag,
    requestData = requestData,
    responseData = responseData,
    requestDataTruncated = requestDataTruncated,
    responseDataTruncated = responseDataTruncated,
    requestContentType = requestPayloadMetadata?.contentType,
    requestOriginalSizeBytes = requestPayloadMetadata?.originalSizeBytes,
    requestDecodeStatus = requestPayloadMetadata?.decodeStatus,
    responseContentType = responsePayloadMetadata?.contentType,
    responseOriginalSizeBytes = responsePayloadMetadata?.originalSizeBytes,
    responseDecodeStatus = responsePayloadMetadata?.decodeStatus,
    status = status,
    requestTimestamp = requestTimestamp,
    responseTimestamp = responseTimestamp,
)

internal fun MessageEntity.toMessageEntry(): MessageEntry = MessageEntry(
    id = id,
    requestId = requestId,
    handlerName = handlerName,
    direction = direction,
    tag = tag,
    requestData = requestData,
    responseData = responseData,
    requestDataTruncated = requestDataTruncated,
    responseDataTruncated = responseDataTruncated,
    requestPayloadMetadata = payloadMetadata(
        contentType = requestContentType,
        originalSizeBytes = requestOriginalSizeBytes,
        decodeStatus = requestDecodeStatus,
    ),
    responsePayloadMetadata = payloadMetadata(
        contentType = responseContentType,
        originalSizeBytes = responseOriginalSizeBytes,
        decodeStatus = responseDecodeStatus,
    ),
    status = status,
    requestTimestamp = requestTimestamp,
    responseTimestamp = responseTimestamp,
)

private fun payloadMetadata(
    contentType: PayloadContentType?,
    originalSizeBytes: Int?,
    decodeStatus: PayloadDecodeStatus?,
): MessagePayloadMetadata? {
    if (contentType == null || originalSizeBytes == null || decodeStatus == null) return null
    return MessagePayloadMetadata(contentType, originalSizeBytes, decodeStatus)
}
