package com.easyhooon.dari

/**
 * Dari configuration
 */
data class DariConfig(
    /** Maximum number of messages to keep in the in-memory buffer */
    val maxEntries: Int = 500,
    /** Whether to show the status notification */
    val showNotification: Boolean = true,
    /** Maximum character length for request/response body data. Bodies exceeding this limit are truncated. */
    val maxContentLength: Int = DEFAULT_MAX_CONTENT_LENGTH,
    /** Whether to open DariActivity when the device is shaken */
    val shakeToOpen: Boolean = false,
) {
    init {
        require(maxContentLength > 0) { "maxContentLength must be greater than 0" }
    }

    companion object {
        const val DEFAULT_MAX_CONTENT_LENGTH = 500_000
    }
}
