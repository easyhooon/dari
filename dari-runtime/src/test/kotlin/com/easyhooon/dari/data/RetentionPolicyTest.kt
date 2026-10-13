package com.easyhooon.dari.data

import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RetentionPolicyTest {

    private fun entry(id: Long, timestamp: Long) = MessageEntry(
        id = id,
        requestId = null,
        handlerName = "handler",
        direction = MessageDirection.WEB_TO_APP,
        tag = null,
        requestData = null,
        responseData = null,
        requestDataTruncated = false,
        responseDataTruncated = false,
        status = MessageStatus.SUCCESS,
        requestTimestamp = timestamp,
        responseTimestamp = null,
    )

    // region cutoff

    @Test
    fun `cutoff returns null when retention is disabled`() {
        assertNull(RetentionPolicy.cutoff(now = 10_000L, retentionPeriodMs = null))
    }

    @Test
    fun `cutoff subtracts retention from now`() {
        assertEquals(4_000L, RetentionPolicy.cutoff(now = 10_000L, retentionPeriodMs = 6_000L))
    }

    @Test
    fun `cutoff can go negative for very early clocks`() {
        // Not a realistic case on real devices, but the function must stay numeric-only
        // so tests that advance the clock backwards still behave predictably.
        assertEquals(-1_000L, RetentionPolicy.cutoff(now = 1_000L, retentionPeriodMs = 2_000L))
    }

    // endregion

    // region prune

    @Test
    fun `prune returns the same list instance when cutoff is null`() {
        val list = listOf(entry(1, 100), entry(2, 200))
        assertSame(list, RetentionPolicy.prune(list, cutoff = null))
    }

    @Test
    fun `prune keeps entries whose timestamp is greater or equal to cutoff`() {
        val list = listOf(
            entry(1, 100),
            entry(2, 500),
            entry(3, 1_000),
        )
        val kept = RetentionPolicy.prune(list, cutoff = 500)
        assertEquals(listOf(2L, 3L), kept.map { it.id })
    }

    @Test
    fun `prune drops entries strictly older than cutoff`() {
        val list = listOf(entry(1, 100), entry(2, 499))
        val kept = RetentionPolicy.prune(list, cutoff = 500)
        assertEquals(emptyList<Long>(), kept.map { it.id })
    }

    @Test
    fun `prune on empty list returns empty list`() {
        assertEquals(emptyList<MessageEntry>(), RetentionPolicy.prune(emptyList(), cutoff = 1_000))
    }

    // endregion
}
