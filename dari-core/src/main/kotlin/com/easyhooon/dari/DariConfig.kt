package com.easyhooon.dari

import com.easyhooon.dari.interceptor.DariInterceptor
import kotlin.time.Duration

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
    /**
     * Optional time-based retention policy. Messages older than this duration are deleted
     * on repository initialization and whenever a new message is added.
     *
     * `null` (default) disables TTL cleanup, preserving the previous behavior.
     * Works alongside [maxEntries] — whichever limit is hit first takes effect.
     *
     * Suggested values (pick based on how long your debug sessions run):
     * - Single interactive debugging session: `1.hours` ~ `6.hours`
     * - Developer debug build that stays installed across days: `1.days` ~ `3.days`
     *
     * Example: `retentionPeriod = 1.days`
     */
    val retentionPeriod: Duration? = null,
    /**
     * When `true`, all bridge calls are treated as fire-and-forget by default:
     * entries are immediately resolved to [MessageStatus.SUCCESS]
     * without waiting for a response.
     *
     * Can be overridden per call via the `fireAndForget` parameter on
     * [DariInterceptor.onWebToAppRequest] and
     * [DariInterceptor.onAppToWebMessage].
     *
     * Default is `false` to preserve the existing request–response pairing behavior.
     */
    val fireAndForget: Boolean = false,
) {
    init {
        require(maxContentLength > 0) { "maxContentLength must be greater than 0" }
        require(retentionPeriod == null || retentionPeriod.isPositive()) {
            "retentionPeriod must be positive when set"
        }
    }

    companion object {
        const val DEFAULT_MAX_CONTENT_LENGTH = 500_000
    }
}
