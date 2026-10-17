package com.easyhooon.dari

import com.easyhooon.dari.MessageDirection.APP_TO_WEB
import com.easyhooon.dari.MessageDirection.WEB_TO_APP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandlerStatsTest {
    private fun entry(
        handler: String,
        durationMs: Long?,
        direction: MessageDirection = WEB_TO_APP,
        status: MessageStatus = MessageStatus.SUCCESS,
    ) = MessageEntry(
        handlerName = handler,
        direction = direction,
        status = status,
        requestTimestamp = 1_000L,
        responseTimestamp = durationMs?.let { 1_000L + it },
    )

    @Test
    fun `aggregates count, errors and timings per handler`() {
        val stats = listOf(
            entry("getUser", 100),
            entry("getUser", 300, status = MessageStatus.ERROR),
            entry("getUser", null, status = MessageStatus.IN_PROGRESS),
        ).handlerStats().single()

        assertEquals(3, stats.callCount)
        assertEquals(1, stats.errorCount)
        assertEquals(2, stats.timedCount)
        assertEquals(200L, stats.avgMs)
        assertEquals(100L, stats.minMs)
        assertEquals(300L, stats.maxMs)
    }

    @Test
    fun `same handler in each direction is reported separately`() {
        val stats = listOf(entry("sync", 10, WEB_TO_APP), entry("sync", 20, APP_TO_WEB)).handlerStats()

        assertEquals(listOf(APP_TO_WEB, WEB_TO_APP), stats.map { it.direction })
    }

    @Test
    fun `slowest handlers come first and untimed handlers last`() {
        val stats = listOf(
            entry("log", null),
            entry("fast", 20),
            entry("slow", 900),
        ).handlerStats()

        assertEquals(listOf("slow", "fast", "log"), stats.map { it.handlerName })
        assertNull(stats.last().avgMs)
    }

    @Test
    fun `slow flag uses the average against the threshold`() {
        assertTrue(listOf(entry("slow", SLOW_HANDLER_THRESHOLD_MS)).handlerStats().single().isSlow)
        assertFalse(listOf(entry("fast", SLOW_HANDLER_THRESHOLD_MS - 1)).handlerStats().single().isSlow)
    }
}
