package com.easyhooon.dari.data.local

import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessagePayloadMetadata
import com.easyhooon.dari.PayloadContentType
import com.easyhooon.dari.PayloadDecodeStatus
import com.easyhooon.dari.RawPayloadPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageEntityTest {
    @Test
    fun `protobuf metadata survives entity round trip`() {
        val metadata = MessagePayloadMetadata(
            contentType = PayloadContentType.PROTOBUF,
            originalSizeBytes = 42,
            decodeStatus = PayloadDecodeStatus.DECODED,
            rawPreview = RawPayloadPreview(
                base64 = "AQID",
                previewSizeBytes = 3,
                truncated = true,
            ),
        )
        val entry = MessageEntry(
            requestId = "request-1",
            handlerName = "createOrder",
            direction = MessageDirection.WEB_TO_APP,
            requestData = """{"productId":42}""",
            requestPayloadMetadata = metadata,
        )

        val restored = entry.toEntity().copy(id = 7).toMessageEntry()

        assertEquals(7, restored.id)
        assertEquals(metadata, restored.requestPayloadMetadata)
        assertEquals(42, restored.requestSizeBytes)
    }
}
