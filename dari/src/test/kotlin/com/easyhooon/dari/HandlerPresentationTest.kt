package com.easyhooon.dari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandlerPresentationTest {
    @Test
    fun `handler label includes a non-blank display name`() {
        assertEquals("N:4-1(오늘의 건강목표)", handlerLabel("N:4-1", "오늘의 건강목표"))
    }

    @Test
    fun `handler label keeps existing behavior for blank display name`() {
        assertEquals("N:4-1", handlerLabel("N:4-1", "  "))
    }

    @Test
    fun `handler search matches the display name`() {
        val entry = MessageEntry(
            handlerName = "N:4-1",
            direction = MessageDirection.WEB_TO_APP,
            displayName = "오늘의 건강목표",
        )

        assertTrue(entry.matchesHandlerQuery("건강목표"))
    }

    @Test
    fun `handler search still matches the stable handler name`() {
        val entry = MessageEntry(
            handlerName = "N:4-1",
            direction = MessageDirection.WEB_TO_APP,
            displayName = "오늘의 건강목표",
        )

        assertTrue(entry.matchesHandlerQuery("n:4"))
    }
}
