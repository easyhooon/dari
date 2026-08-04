package com.easyhooon.dari.interceptor

import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.PayloadContentType
import com.easyhooon.dari.PayloadDecodeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtobufPayloadRendererTest {
    private val context = ProtobufDecodeContext(
        handlerName = "createOrder",
        direction = MessageDirection.WEB_TO_APP,
        part = PayloadPart.REQUEST,
    )

    @Test
    fun `render uses decoder output and original binary size`() {
        var receivedContext: ProtobufDecodeContext? = null
        val result = ProtobufPayloadRenderer.render(
            payload = byteArrayOf(1, 2, 3),
            context = context,
            decoder = ProtobufPayloadDecoder { _, decodeContext ->
                receivedContext = decodeContext
                """{"productId":42}"""
            },
            maxContentLength = 100,
        )

        assertEquals("""{"productId":42}""", result.data)
        assertFalse(result.wasTruncated)
        assertEquals(context, receivedContext)
        assertEquals(PayloadContentType.PROTOBUF, result.metadata.contentType)
        assertEquals(3, result.metadata.originalSizeBytes)
        assertEquals(PayloadDecodeStatus.DECODED, result.metadata.decodeStatus)
    }

    @Test
    fun `render records unavailable decoder without throwing`() {
        val result = ProtobufPayloadRenderer.render(
            payload = byteArrayOf(1),
            context = context,
            decoder = ProtobufPayloadDecoder { _, _ -> null },
            maxContentLength = 100,
        )

        assertEquals("(protobuf decoder unavailable)", result.data)
        assertEquals(PayloadDecodeStatus.DECODER_UNAVAILABLE, result.metadata.decodeStatus)
    }

    @Test
    fun `render records decoder failure without exposing the exception`() {
        val result = ProtobufPayloadRenderer.render(
            payload = byteArrayOf(1),
            context = context,
            decoder = ProtobufPayloadDecoder { _, _ -> error("sensitive details") },
            maxContentLength = 100,
        )

        assertEquals("(protobuf decoding failed)", result.data)
        assertEquals(PayloadDecodeStatus.FAILED, result.metadata.decodeStatus)
    }

    @Test
    fun `render truncates decoded display text`() {
        val result = ProtobufPayloadRenderer.render(
            payload = byteArrayOf(1, 2),
            context = context,
            decoder = ProtobufPayloadDecoder { _, _ -> "abcdefghij" },
            maxContentLength = 5,
        )

        assertTrue(result.wasTruncated)
        assertTrue(result.data.startsWith("abcde"))
        assertEquals(2, result.metadata.originalSizeBytes)
    }
}
