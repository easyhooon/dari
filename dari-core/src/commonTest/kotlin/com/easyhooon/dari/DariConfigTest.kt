package com.easyhooon.dari

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class DariConfigTest {

    @Test
    fun `default retention period is null (disabled)`() {
        assertNull(DariConfig().retentionPeriod)
    }

    @Test
    fun `positive retention period is accepted`() {
        val config = DariConfig(retentionPeriod = 1.days)
        assertEquals(1.days, config.retentionPeriod)
    }

    @Test
    fun `retention period can be set to smaller durations`() {
        val config = DariConfig(retentionPeriod = 6.hours)
        assertEquals(6.hours, config.retentionPeriod)
    }

    @Test
    fun `zero retention period throws`() {
        assertFailsWith<IllegalArgumentException> {
            DariConfig(retentionPeriod = Duration.ZERO)
        }
    }

    @Test
    fun `negative retention period throws`() {
        assertFailsWith<IllegalArgumentException> {
            DariConfig(retentionPeriod = (-1).seconds)
        }
    }

    @Test
    fun `other validation still applies`() {
        // Sanity check that retentionPeriod was added without breaking maxContentLength validation.
        assertFailsWith<IllegalArgumentException> {
            DariConfig(maxContentLength = 0)
        }
    }
}
