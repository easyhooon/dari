package com.easyhooon.dari.inspector

import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessagePayloadMetadata
import com.easyhooon.dari.PayloadContentType
import com.easyhooon.dari.PayloadDecodeStatus
import com.easyhooon.dari.RawPayloadPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPayloadMaskerTest {

    @Test
    fun `masks sensitive JSON keys at any depth and keeps the rest`() {
        val masked = DesktopPayloadMasker.maskText(
            """{"user":{"name":"kim","accessToken":"abc","Password":"pw"},"items":[{"api_key":"k","id":1}]}""",
        )
        assertEquals(
            """{"user":{"name":"kim","accessToken":"***","Password":"***"},"items":[{"api_key":"***","id":1}]}""",
            masked,
        )
    }

    @Test
    fun `masks inside stringified JSON values`() {
        val masked = DesktopPayloadMasker.maskText("""{"data":"{\"refresh_token\":\"r\",\"page\":2}"}""")
        assertEquals("""{"data":"{\"refresh_token\":\"***\",\"page\":2}"}""", masked)
    }

    @Test
    fun `masks key-value text such as decoded protobuf and query strings`() {
        assertEquals(
            "user_id: 7\nsession_token: \"***\"",
            DesktopPayloadMasker.maskText("user_id: 7\nsession_token: \"s3cr3t\""),
        )
        assertEquals("a=1&token=***&b=2", DesktopPayloadMasker.maskText("a=1&token=xyz&b=2"))
    }

    @Test
    fun `masks bearer credentials anywhere`() {
        assertEquals(
            """{"header":"Bearer ***"}""",
            DesktopPayloadMasker.maskText("""{"header":"Bearer eyJhbGciOi.abc-def"}"""),
        )
    }

    @Test
    fun `truncated JSON falls back to key-value masking`() {
        val masked = DesktopPayloadMasker.maskText("""{"token":"abc","list":[1,2""")!!
        assertFalse(masked.contains("abc"))
    }

    @Test
    fun `short keys match exactly to avoid false positives`() {
        assertTrue(DesktopPayloadMasker.isSensitiveKey("PIN"))
        assertFalse(DesktopPayloadMasker.isSensitiveKey("pinned"))
        assertFalse(DesktopPayloadMasker.isSensitiveKey("lessons"))
    }

    @Test
    fun `drops raw previews and keeps entries without payloads intact`() {
        val metadata = MessagePayloadMetadata(
            contentType = PayloadContentType.PROTOBUF,
            originalSizeBytes = 4,
            decodeStatus = PayloadDecodeStatus.DECODER_UNAVAILABLE,
            rawPreview = RawPayloadPreview(base64 = "AAEC", previewSizeBytes = 3, truncated = false),
        )
        val entry = MessageEntry(
            handlerName = "sync",
            direction = MessageDirection.APP_TO_WEB,
            requestPayloadMetadata = metadata,
        )

        val masked = DesktopPayloadMasker.mask(entry)
        assertNull(masked.requestPayloadMetadata?.rawPreview)
        assertEquals(4, masked.requestPayloadMetadata?.originalSizeBytes)
        assertNull(masked.requestData)
    }
}
