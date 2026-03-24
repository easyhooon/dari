package com.easyhooon.dari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `tag defaults to null`() {
        val entry = MessageEntry(
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
        )
        assertNull(entry.tag)
    }

    @Test
    fun `tag is preserved when set`() {
        val entry = MessageEntry(
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
            tag = "PaymentWebView",
        )
        assertEquals("PaymentWebView", entry.tag)
    }

    @Test
    fun `tag is preserved through copy`() {
        val entry = MessageEntry(
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
            tag = "MainWebView",
        )
        val updated = entry.copy(status = MessageStatus.SUCCESS)
        assertEquals("MainWebView", updated.tag)
        assertEquals(MessageStatus.SUCCESS, updated.status)
    }

    @Test
    fun `truncateIfNeeded returns original data when within limit`() {
        val (result, wasTruncated) = MessageEntry.truncateIfNeeded("short data", 100)
        assertEquals("short data", result)
        assertFalse(wasTruncated)
    }

    @Test
    fun `truncateIfNeeded returns null for null input`() {
        val (result, wasTruncated) = MessageEntry.truncateIfNeeded(null, 100)
        assertNull(result)
        assertFalse(wasTruncated)
    }

    @Test
    fun `truncateIfNeeded truncates data exceeding limit`() {
        val longData = "a".repeat(200)
        val (result, wasTruncated) = MessageEntry.truncateIfNeeded(longData, 100)
        assertTrue(wasTruncated)
        assertTrue(result!!.startsWith("a".repeat(100)))
        assertTrue(result.contains("...[truncated, original length: 200 chars]"))
    }

    @Test
    fun `truncateIfNeeded does not truncate data at exact limit`() {
        val data = "a".repeat(100)
        val (result, wasTruncated) = MessageEntry.truncateIfNeeded(data, 100)
        assertEquals(data, result)
        assertFalse(wasTruncated)
    }

    @Test
    fun `truncation flags default to false`() {
        val entry = MessageEntry(
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
        )
        assertFalse(entry.requestDataTruncated)
        assertFalse(entry.responseDataTruncated)
    }
}
