package com.easyhooon.dari.data

import com.easyhooon.dari.MessageEntry

/**
 * Pure helpers for the time-based retention policy (TTL).
 *
 * Extracted from [MessageRepository] so the cutoff / pruning logic can be
 * exercised by plain JVM unit tests without pulling in Room or Android.
 */
internal object RetentionPolicy {

    /**
     * Computes the cutoff timestamp for retention.
     *
     * Messages whose `requestTimestamp` is strictly less than the returned
     * cutoff are considered expired and should be deleted.
     *
     * @return the cutoff (`now - retentionPeriodMs`) or `null` when TTL is
     *   disabled ([retentionPeriodMs] is `null`).
     */
    fun cutoff(now: Long, retentionPeriodMs: Long?): Long? =
        retentionPeriodMs?.let { now - it }

    /**
     * Filters out entries older than the given cutoff.
     *
     * @param entries the in-memory list to prune.
     * @param cutoff the value returned by [cutoff]. When `null` the input
     *   list is returned as-is (TTL disabled).
     */
    fun prune(entries: List<MessageEntry>, cutoff: Long?): List<MessageEntry> =
        if (cutoff == null) entries else entries.filter { it.requestTimestamp >= cutoff }
}
