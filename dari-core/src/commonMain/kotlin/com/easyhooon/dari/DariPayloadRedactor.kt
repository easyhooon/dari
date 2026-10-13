package com.easyhooon.dari

/**
 * Rewrites a message before it leaves the device for the desktop inspector,
 * e.g. to mask tokens or personal data. The in-app inspector is not affected.
 */
fun interface DariPayloadRedactor {
    fun redact(entry: MessageEntry): MessageEntry
}
