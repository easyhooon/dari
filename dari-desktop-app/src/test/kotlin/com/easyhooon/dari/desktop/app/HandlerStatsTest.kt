package com.easyhooon.dari.desktop.app

import com.easyhooon.dari.inspector.protocol.WireMessageSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandlerStatsTest {
    private var nextId = 0L

    private fun message(
        handler: String,
        durationMs: Long?,
        direction: String = "WEB_TO_APP",
        status: String = "SUCCESS",
        displayName: String? = null,
    ) = WireMessageSummary(
        id = nextId++,
        handlerName = handler,
        displayName = displayName,
        direction = direction,
        status = status,
        requestTimestamp = 1_000,
        responseTimestamp = durationMs?.let { 1_000 + it },
        requestSizeBytes = 0,
        responseSizeBytes = 0,
    )

    @Test
    fun `aggregates count, errors and avg min max per handler`() {
        val stats = listOf(
            message("getUser", 100),
            message("getUser", 300, status = "ERROR"),
            message("getUser", null, status = "IN_PROGRESS"),
        ).handlerStats().single()

        assertEquals(3, stats.callCount)
        assertEquals(1, stats.errorCount)
        assertEquals(2, stats.timedCount)
        assertEquals(200L, stats.avgMs)
        assertEquals(100L, stats.minMs)
        assertEquals(300L, stats.maxMs)
    }

    @Test
    fun `same handler in each direction is a separate group`() {
        val stats = listOf(
            message("sync", 10, direction = "WEB_TO_APP"),
            message("sync", 20, direction = "APP_TO_WEB"),
        ).handlerStats()

        assertEquals(setOf("WEB_TO_APP", "APP_TO_WEB"), stats.map { it.direction }.toSet())
        assertTrue(stats.all { it.callCount == 1 })
    }

    @Test
    fun `slowest average first, untimed handlers last, ties by name`() {
        val stats = listOf(
            message("fast", 10),
            message("noResponse", null, direction = "APP_TO_WEB"),
            message("slow", 900),
            message("b", 50),
            message("a", 50),
        ).handlerStats()

        assertEquals(listOf("slow", "a", "b", "fast", "noResponse"), stats.map { it.handlerName })
        val untimed = stats.last()
        assertNull(untimed.avgMs)
        assertNull(untimed.minMs)
        assertNull(untimed.maxMs)
        assertFalse(untimed.isSlow)
    }

    @Test
    fun `averages at or above the threshold are slow`() {
        val stats = listOf(
            message("atThreshold", SLOW_HANDLER_THRESHOLD_MS),
            message("justUnder", SLOW_HANDLER_THRESHOLD_MS - 1),
        ).handlerStats().associateBy { it.handlerName }

        assertTrue(stats.getValue("atThreshold").isSlow)
        assertFalse(stats.getValue("justUnder").isSlow)
    }

    @Test
    fun `uses the latest non-blank display name`() {
        val stats = listOf(
            message("getUser", 10, displayName = "Old name"),
            message("getUser", 10, displayName = "New name"),
            message("getUser", 10, displayName = " "),
        ).handlerStats().single()

        assertEquals("New name", stats.displayName)
    }

    @Test
    fun `no messages gives no stats`() {
        assertTrue(emptyList<WireMessageSummary>().handlerStats().isEmpty())
    }
}
