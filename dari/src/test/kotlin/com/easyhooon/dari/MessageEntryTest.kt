package com.easyhooon.dari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageEntryTest {

    @Test
    fun `durationMs returns difference between response and request timestamps`() {
        val entry = MessageEntry(
            requestId = "1",
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
            requestTimestamp = 1000L,
            responseTimestamp = 1500L,
        )
        assertEquals(500L, entry.durationMs)
    }

    @Test
    fun `durationMs returns null when responseTimestamp is null`() {
        val entry = MessageEntry(
            requestId = "1",
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
            requestTimestamp = 1000L,
            responseTimestamp = null,
        )
        assertNull(entry.durationMs)
    }

    @Test
    fun `totalSizeBytes sums request and response data sizes`() {
        val entry = MessageEntry(
            requestId = "1",
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
            requestData = "hello",
            responseData = "world!",
        )
        assertEquals(11, entry.totalSizeBytes)
    }

    @Test
    fun `totalSizeBytes returns zero when both data are null`() {
        val entry = MessageEntry(
            requestId = "1",
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
        )
        assertEquals(0, entry.totalSizeBytes)
    }

    @Test
    fun `totalSizeBytes handles multibyte characters`() {
        val entry = MessageEntry(
            requestId = "1",
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
            requestData = "다리",
        )
        // Korean characters are 3 bytes each in UTF-8
        assertEquals(6, entry.totalSizeBytes)
    }

    @Test
    fun `default status is IN_PROGRESS`() {
        val entry = MessageEntry(
            requestId = "1",
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
        )
        assertEquals(MessageStatus.IN_PROGRESS, entry.status)
    }
}
