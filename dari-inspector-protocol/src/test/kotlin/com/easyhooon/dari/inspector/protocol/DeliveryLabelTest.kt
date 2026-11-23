package com.easyhooon.dari.inspector.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeliveryLabelTest {

    @Test
    fun `untracked delivery has no label`() {
        assertNull(deliveryLabel(null, sentAt = 0, now = 60_000))
    }

    @Test
    fun `pending turns into not confirmed after the grace period`() {
        assertEquals("Pending", deliveryLabel("PENDING", sentAt = 1_000, now = 1_000 + DELIVERY_GRACE_MS - 1))
        assertEquals(
            "Not confirmed: the page never ran it",
            deliveryLabel("PENDING", sentAt = 1_000, now = 1_000 + DELIVERY_GRACE_MS),
        )
    }

    @Test
    fun `settled values and unknown ones from newer apps`() {
        assertEquals("Delivered to the page", deliveryLabel("DELIVERED", 0, 0))
        assertEquals("Failed in the page", deliveryLabel("FAILED", 0, 0))
        assertEquals("SOMETHING_NEW", deliveryLabel("SOMETHING_NEW", 0, 0))
    }

    @Test
    fun `an app-to-web request counts from when it was sent, a response from when it was answered`() {
        assertEquals(1_000, deliverySentAt("APP_TO_WEB", requestTimestamp = 1_000, responseTimestamp = 9_000))
        assertEquals(9_000, deliverySentAt("WEB_TO_APP", requestTimestamp = 1_000, responseTimestamp = 9_000))
        assertEquals(1_000, deliverySentAt("WEB_TO_APP", requestTimestamp = 1_000, responseTimestamp = null))
    }

    @Test
    fun `only a pending message inside the grace period needs a refresh`() {
        assertEquals(DELIVERY_GRACE_MS - 1_000, deliveryLabelRefreshIn("PENDING", sentAt = 0, now = 1_000))
        assertNull(deliveryLabelRefreshIn("PENDING", sentAt = 0, now = DELIVERY_GRACE_MS))
        assertNull(deliveryLabelRefreshIn("DELIVERED", sentAt = 0, now = 0))
    }

    @Test
    fun `a summary from an app without delivery decodes as untracked`() {
        val json = """{"id":1,"handlerName":"h","direction":"WEB_TO_APP","status":"SUCCESS","requestTimestamp":0,""" +
            """"requestSizeBytes":0,"responseSizeBytes":0}"""

        assertNull(InspectorProtocol.json.decodeFromString(WireMessageSummary.serializer(), json).delivery)
    }
}
