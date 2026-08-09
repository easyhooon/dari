package com.easyhooon.dari

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageEntryTest {
    @Test
    fun `default timestamp uses platform clock`() {
        val before = currentTimeMillis()
        val entry = MessageEntry(handlerName = "handler", direction = MessageDirection.WEB_TO_APP)
        val after = currentTimeMillis()

        assertTrue(entry.requestTimestamp in before..after)
    }

    @Test
    fun `payload size is calculated in UTF-8 bytes`() {
        val entry = MessageEntry(
            handlerName = "handler",
            direction = MessageDirection.APP_TO_WEB,
            requestData = "Dari 다리",
            responseData = "ok",
        )

        assertEquals(11, entry.requestSizeBytes)
        assertEquals(2, entry.responseSizeBytes)
        assertEquals(13, entry.totalSizeBytes)
    }
}
