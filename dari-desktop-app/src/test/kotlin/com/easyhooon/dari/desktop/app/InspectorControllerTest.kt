package com.easyhooon.dari.desktop.app

import com.easyhooon.dari.inspector.protocol.ObserveEvent
import com.easyhooon.dari.inspector.protocol.WireMessageSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class InspectorControllerTest {
    private val controller = InspectorController(CoroutineScope(Dispatchers.Unconfined), adb = null, clientVersion = "test")

    private fun message(id: Long, direction: String = "WEB_TO_APP", status: String = "SUCCESS") = WireMessageSummary(
        id = id,
        handlerName = "handler$id",
        direction = direction,
        status = status,
        requestTimestamp = id,
        requestSizeBytes = 0,
        responseSizeBytes = 0,
    )

    private fun addAll(vararg ids: Long) = ids.forEach { controller.apply(ObserveEvent.Added(message(it))) }

    private fun newerCount() = controller.newerCount(controller.visibleMessages())

    @Test
    fun `selecting an older message counts nothing until a new one arrives`() {
        addAll(1, 2, 3, 4, 5)
        controller.select(1)
        assertEquals(0, newerCount())

        addAll(6, 7)
        assertEquals(2, newerCount())
    }

    @Test
    fun `selecting again resets the count`() {
        addAll(1, 2)
        controller.select(1)
        addAll(3)
        assertEquals(1, newerCount())

        controller.select(3)
        assertEquals(0, newerCount())
    }

    @Test
    fun `updates to existing messages are not new`() {
        addAll(1, 2)
        controller.select(2)
        controller.apply(ObserveEvent.Updated(message(1, status = "ERROR")))
        assertEquals(0, newerCount())
    }

    @Test
    fun `arrivals hidden by the filter are not counted`() {
        addAll(1)
        controller.select(1)
        controller.apply(ObserveEvent.Added(message(2, direction = "APP_TO_WEB")))
        addAll(3)
        controller.directionFilter = "WEB_TO_APP"
        assertEquals(1, newerCount())
    }

    @Test
    fun `closing the detail and clearing reset the count`() {
        addAll(1)
        controller.select(1)
        addAll(2)
        controller.closeDetail()
        assertEquals(0, newerCount())

        controller.select(1)
        addAll(3)
        controller.apply(ObserveEvent.Cleared)
        assertEquals(0, newerCount())
    }
}
