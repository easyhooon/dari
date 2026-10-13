package com.easyhooon.dari.desktop.app.ui

import com.easyhooon.dari.inspector.protocol.WireMessageSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US).withZone(ZoneId.systemDefault())

/** Fixed `HH:mm:ss.SSS`, never localised. */
fun formatTime(epochMillis: Long): String = timeFormatter.format(Instant.ofEpochMilli(epochMillis))

fun directionLabel(direction: String): String = when (direction) {
    // Spaced so the arrow reads as an arrow in the monospace table.
    "WEB_TO_APP" -> "W → A"
    "APP_TO_WEB" -> "A → W"
    else -> direction
}

fun durationLabel(message: WireMessageSummary): String {
    val end = message.responseTimestamp ?: return "…"
    val ms = end - message.requestTimestamp
    return if (ms < MILLIS_PER_SECOND) "$ms ms" else String.format(Locale.US, "%.1f s", ms / MILLIS_PER_SECOND.toDouble())
}

fun formatBytes(bytes: Int): String =
    if (bytes < BYTES_PER_KB) "$bytes B" else String.format(Locale.US, "%.1f kB", bytes / BYTES_PER_KB.toDouble())

private const val MILLIS_PER_SECOND = 1_000
private const val BYTES_PER_KB = 1_000
