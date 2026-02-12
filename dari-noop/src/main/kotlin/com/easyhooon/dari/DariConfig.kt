package com.easyhooon.dari

/**
 * Dari configuration
 */
data class DariConfig(
    /** Maximum number of messages to keep in the in-memory buffer */
    val maxEntries: Int = 500,
    /** Whether to show the status notification */
    val showNotification: Boolean = true,
)