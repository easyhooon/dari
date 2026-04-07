package com.easyhooon.dari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DariConfigTest {

    @Test
    fun `default values are applied`() {
        val config = DariConfig()
        assertEquals(500, config.maxEntries)
        assertTrue(config.showNotification)
        assertEquals(DariConfig.DEFAULT_MAX_CONTENT_LENGTH, config.maxContentLength)
    }

    @Test
    fun `custom values override defaults`() {
        val config = DariConfig(
            maxEntries = 100,
            showNotification = false,
            maxContentLength = 1_000_000,
        )
        assertEquals(100, config.maxEntries)
        assertEquals(false, config.showNotification)
        assertEquals(1_000_000, config.maxContentLength)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `maxContentLength rejects zero`() {
        DariConfig(maxContentLength = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `maxContentLength rejects negative value`() {
        DariConfig(maxContentLength = -1)
    }
}
