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

    @Test
    fun `display name is stored separately from the stable handler name`() {
        val entry = MessageEntry(
            handlerName = "N:4-1",
            direction = MessageDirection.WEB_TO_APP,
            displayName = "오늘의 건강목표",
        )

        assertEquals("N:4-1", entry.handlerName)
        assertEquals("오늘의 건강목표", entry.displayName)
    }

    @Test
    fun `display name remains attached when a response updates the entry`() {
        val entry = MessageEntry(
            handlerName = "N:4-1",
            direction = MessageDirection.WEB_TO_APP,
            displayName = "오늘의 건강목표",
        )

        val completed = entry.copy(status = MessageStatus.SUCCESS, responseData = "done")

        assertEquals("오늘의 건강목표", completed.displayName)
    }
}
