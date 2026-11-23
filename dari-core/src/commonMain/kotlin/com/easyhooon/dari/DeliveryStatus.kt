package com.easyhooon.dari

/** Whether the script carrying a message to the page actually ran there. Null on an entry means not tracked. */
enum class DeliveryStatus {
    /** Recorded, but the script carrying it hasn't run in the page yet. */
    PENDING,

    /** The script carrying it ran in the page. */
    DELIVERED,

    /** The script carrying it failed in the page (reported on iOS only). */
    FAILED,
}
