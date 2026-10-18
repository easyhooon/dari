package com.easyhooon.dari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertTrue(entry.matchesQuery("건강목표"))
    }

    @Test
    fun `handler search still matches the stable handler name`() {
        val entry = MessageEntry(
            handlerName = "N:4-1",
            direction = MessageDirection.WEB_TO_APP,
            displayName = "오늘의 건강목표",
        )

        assertTrue(entry.matchesQuery("n:4"))
    }

    @Test
    fun `search also matches tag and request ID, like the desktop filter`() {
        val entry = MessageEntry(
            handlerName = "getUser",
            direction = MessageDirection.WEB_TO_APP,
            tag = "CheckoutWebView",
            requestId = "req-42",
        )

        assertTrue(entry.matchesQuery("checkout"))
        assertTrue(entry.matchesQuery("REQ-4"))
        assertFalse(entry.matchesQuery("payment"))
    }

    @Test
    fun `search matches request and response payloads`() {
        val entry = MessageEntry(
            handlerName = "getUser",
            direction = MessageDirection.WEB_TO_APP,
            requestData = """{"userId":"u-981"}""",
            responseData = """{"error":"Session expired"}""",
        )

        assertTrue(entry.matchesQuery("U-981"))
        assertTrue(entry.matchesQuery("session expired"))
        assertFalse(entry.matchesQuery("u-982"))
    }

    @Test
    fun `filters combine and null means any`() {
        val entry = MessageEntry(
            handlerName = "getUser",
            direction = MessageDirection.APP_TO_WEB,
            tag = "main",
            status = MessageStatus.ERROR,
        )

        assertTrue(entry.matchesFilters(" ", null, null, null))
        assertTrue(entry.matchesFilters(" user ", "main", MessageStatus.ERROR, MessageDirection.APP_TO_WEB))
        assertFalse(entry.matchesFilters("", null, null, MessageDirection.WEB_TO_APP))
        assertFalse(entry.matchesFilters("", "other", null, null))
        assertFalse(entry.matchesFilters("", null, MessageStatus.SUCCESS, null))
        assertFalse(entry.matchesFilters("profile", null, null, null))
    }

    @Test
    fun `origin filter and search use the page url`() {
        val entry = MessageEntry(
            handlerName = "getUser",
            direction = MessageDirection.WEB_TO_APP,
            pageUrl = "https://jp.example.com/cart",
        )

        assertTrue(entry.matchesFilters("", null, null, null, "https://jp.example.com"))
        assertFalse(entry.matchesFilters("", null, null, null, "https://kr.example.com"))
        assertTrue(entry.matchesQuery("jp.example"))
    }
}
