package com.easyhooon.dari.inspector.protocol

/** How long a message may stay pending before the inspectors call its delivery unconfirmed. */
const val DELIVERY_GRACE_MS = 5_000L

/** When a message left for the page: an App -> Web request when sent, a Web -> App response when answered. */
fun deliverySentAt(direction: String, requestTimestamp: Long, responseTimestamp: Long?): Long =
    if (direction == "APP_TO_WEB") requestTimestamp else responseTimestamp ?: requestTimestamp

/** Delay until a pending message turns into not confirmed, or null when nothing will change on its own. */
fun deliveryLabelRefreshIn(delivery: String?, sentAt: Long, now: Long): Long? =
    (sentAt + DELIVERY_GRACE_MS - now).takeIf { delivery == "PENDING" && it > 0 }

/**
 * Inspector text for a delivery value (`PENDING`, `DELIVERED`, `FAILED`), or null when it isn't tracked.
 * A message still pending [DELIVERY_GRACE_MS] after [sentAt] reads as not confirmed.
 */
fun deliveryLabel(delivery: String?, sentAt: Long, now: Long): String? = when (delivery) {
    null -> null
    "DELIVERED" -> "Delivered to the page"
    "FAILED" -> "Failed in the page"
    "PENDING" -> if (now - sentAt >= DELIVERY_GRACE_MS) "Not confirmed: the page never ran it" else "Pending"
    else -> delivery
}
