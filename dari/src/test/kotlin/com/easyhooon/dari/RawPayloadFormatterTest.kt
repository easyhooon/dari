package com.easyhooon.dari

import org.junit.Assert.assertEquals
import org.junit.Test

class RawPayloadFormatterTest {
    @Test
    fun `formatHex groups bytes into sixteen byte lines`() {
        val preview = RawPayloadPreview(
            base64 = "AAECAwQFBgcICQoLDA0ODxA=",
            previewSizeBytes = 17,
            truncated = false,
        )

        assertEquals(
            "00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F\n10",
            RawPayloadFormatter.formatHex(preview),
        )
    }

    @Test
    fun `formatHex handles invalid preview safely`() {
        val preview = RawPayloadPreview("not base64", 0, false)

        assertEquals("(raw preview unavailable)", RawPayloadFormatter.formatHex(preview))
    }
}
