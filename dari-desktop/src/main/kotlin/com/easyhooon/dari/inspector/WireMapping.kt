package com.easyhooon.dari.inspector

import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessagePayloadMetadata
import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.inspector.protocol.WireMessageSummary
import com.easyhooon.dari.inspector.protocol.WirePayloadMetadata

internal fun MessageEntry.toWireSummary(wireId: Long): WireMessageSummary = WireMessageSummary(
    id = wireId,
    requestId = requestId,
    handlerName = handlerName,
    displayName = displayName,
    direction = direction.name,
    tag = tag,
    status = status.name,
    requestTimestamp = requestTimestamp,
    responseTimestamp = responseTimestamp,
    requestSizeBytes = requestSizeBytes,
    responseSizeBytes = responseSizeBytes,
)

internal fun MessageEntry.toWireDetail(wireId: Long): WireMessageDetail = WireMessageDetail(
    summary = toWireSummary(wireId),
    requestData = requestData,
    responseData = responseData,
    requestDataTruncated = requestDataTruncated,
    responseDataTruncated = responseDataTruncated,
    requestPayloadMetadata = requestPayloadMetadata?.toWire(),
    responsePayloadMetadata = responsePayloadMetadata?.toWire(),
)

private fun MessagePayloadMetadata.toWire(): WirePayloadMetadata = WirePayloadMetadata(
    contentType = contentType.name,
    originalSizeBytes = originalSizeBytes,
    decodeStatus = decodeStatus.name,
    rawPreviewBase64 = rawPreview?.base64,
    rawPreviewSizeBytes = rawPreview?.previewSizeBytes,
    rawPreviewTruncated = rawPreview?.truncated,
)
