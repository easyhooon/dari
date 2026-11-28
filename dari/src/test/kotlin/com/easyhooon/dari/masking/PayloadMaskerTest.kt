package com.easyhooon.dari.masking

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

class PayloadMaskerTest {

    @Test
    fun `masks sensitive JSON keys at any depth and keeps the rest`() {
        val masked = PayloadMasker.maskText(
            """{"user":{"name":"kim","accessToken":"abc","Password":"pw"},"items":[{"api_key":"k","id":1}]}""",
        )
        assertEquals(
            """{"user":{"name":"kim","accessToken":"***","Password":"***"},"items":[{"api_key":"***","id":1}]}""",
            masked,
        )
    }

    @Test
    fun `masks inside stringified JSON values`() {
        val masked = PayloadMasker.maskText("""{"data":"{\"refresh_token\":\"r\",\"page\":2}"}""")
        assertEquals("""{"data":"{\"refresh_token\":\"***\",\"page\":2}"}""", masked)
    }

    @Test
    fun `masks key-value text such as decoded protobuf and query strings`() {
        assertEquals(
            "user_id: 7\nsession_token: \"***\"",
            PayloadMasker.maskText("user_id: 7\nsession_token: \"s3cr3t\""),
        )
        assertEquals("a=1&token=***&b=2", PayloadMasker.maskText("a=1&token=xyz&b=2"))
    }

    @Test
    fun `masks bearer credentials anywhere`() {
        assertEquals(
            """{"header":"Bearer ***"}""",
            PayloadMasker.maskText("""{"header":"Bearer eyJhbGciOi.abc-def"}"""),
        )
    }

    @Test
    fun `truncated JSON falls back to key-value masking`() {
        val masked = PayloadMasker.maskText("""{"token":"abc","list":[1,2""")!!
        assertFalse(masked.contains("abc"))
    }

    @Test
    fun `short keys match exactly to avoid false positives`() {
        assertTrue(PayloadMasker.isSensitiveKey("PIN"))
        assertFalse(PayloadMasker.isSensitiveKey("pinned"))
        assertFalse(PayloadMasker.isSensitiveKey("lessons"))
    }

    @Test
    fun `passes empty and payload-free inputs through unchanged`() {
        listOf("", "   ", "null", "[]", "{}").forEach { assertEquals(it, PayloadMasker.maskText(it)) }
    }

    @Test
    fun `keeps non-ASCII and emoji values while masking sensitive ones`() {
        assertEquals(
            """{"name":"김철수","token":"***","emoji":"😀"}""",
            PayloadMasker.maskText("""{"name":"김철수","token":"비밀값","emoji":"😀"}"""),
        )
    }

    @Test
    fun `keeps number literals as written and masks non-string sensitive values`() {
        assertEquals(
            """{"pin":"***","otp":"***","cvv":"***","amount":12345678901234567890,"z":-0.0,"e":1.0E10}""",
            PayloadMasker.maskText(
                """{"pin":1234,"otp":null,"cvv":true,"amount":12345678901234567890,"z":-0.0,"e":1.0E10}""",
            ),
        )
    }

    @Test
    fun `duplicate sensitive keys leak neither value`() {
        assertEquals("""{"token":"***"}""", PayloadMasker.maskText("""{"token":"first","token":"second"}"""))
    }

    @Test
    fun `masks triple-nested stringified JSON`() {
        assertEquals(
            """{"data":"{\"inner\":\"{\\\"password\\\":\\\"***\\\"}\"}"}""",
            PayloadMasker.maskText("""{"data":"{\"inner\":\"{\\\"password\\\":\\\"deep\\\"}\"}"}"""),
        )
    }

    @Test
    fun `parses JSON after leading whitespace`() {
        assertEquals("""{"token":"***"}""", PayloadMasker.maskText("""  {"token":"a"}"""))
    }

    @Test
    fun `single-quoted pseudo-JSON falls back to key-value masking`() {
        assertEquals("{'token':'***'}", PayloadMasker.maskText("{'token':'abc'}"))
    }

    @Test
    fun `masks hyphenated and underscored keys in query strings`() {
        assertEquals(
            "access_token=***&refresh-token=***",
            PayloadMasker.maskText("access_token=abc&refresh-token=def"),
        )
    }

    @Test
    fun `deeply nested input leaks no sensitive value`() {
        // Either fully masked or REDACTION_FAILED, depending on the thread's stack size.
        val deep = "[".repeat(20_000) + """{"token":"leak"}""" + "]".repeat(20_000)
        assertFalse(PayloadMasker.maskText(deep)!!.contains("leak"))
    }

    @Test
    fun `large payload keeps no sensitive value`() {
        val items = (1..20_000).joinToString(",") { """{"id":$it,"token":"t$it"}""" }
        val masked = PayloadMasker.maskText("""{"items":[$items]}""")!!
        assertFalse(Regex(""""t\d+"""").containsMatchIn(masked))
        assertTrue(masked.contains(""""id":20000"""))
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

        val masked = PayloadMasker.mask(entry)
        assertNull(masked.requestPayloadMetadata?.rawPreview)
        assertEquals(4, masked.requestPayloadMetadata?.originalSizeBytes)
        assertNull(masked.requestData)
    }
}
