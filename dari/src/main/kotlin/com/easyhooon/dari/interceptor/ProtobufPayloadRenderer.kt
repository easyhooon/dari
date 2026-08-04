package com.easyhooon.dari.interceptor

import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessagePayloadMetadata
import com.easyhooon.dari.PayloadContentType
import com.easyhooon.dari.PayloadDecodeStatus

internal data class RenderedProtobufPayload(
    val data: String,
    val wasTruncated: Boolean,
    val metadata: MessagePayloadMetadata,
)

internal object ProtobufPayloadRenderer {
    fun render(
        payload: ByteArray,
        context: ProtobufDecodeContext,
        decoder: ProtobufPayloadDecoder,
        maxContentLength: Int,
    ): RenderedProtobufPayload {
        val (displayData, decodeStatus) = try {
            val decoded = decoder.decode(payload, context)
            if (decoded == null) {
                "(protobuf decoder unavailable)" to PayloadDecodeStatus.DECODER_UNAVAILABLE
            } else {
                decoded to PayloadDecodeStatus.DECODED
            }
        } catch (_: Exception) {
            "(protobuf decoding failed)" to PayloadDecodeStatus.FAILED
        }
        val (truncatedData, wasTruncated) = MessageEntry.truncateIfNeeded(displayData, maxContentLength)

        return RenderedProtobufPayload(
            data = truncatedData.orEmpty(),
            wasTruncated = wasTruncated,
            metadata = MessagePayloadMetadata(
                contentType = PayloadContentType.PROTOBUF,
                originalSizeBytes = payload.size,
                decodeStatus = decodeStatus,
            ),
        )
    }
}
