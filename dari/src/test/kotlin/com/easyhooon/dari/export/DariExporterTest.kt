package com.easyhooon.dari.export

import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DariExporterTest {

    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private fun createEntry(
        id: Long = 1L,
        handlerName: String = "getAppInfo",
        direction: MessageDirection = MessageDirection.WEB_TO_APP,
        tag: String? = null,
        requestData: String? = """{"key":"value"}""",
        responseData: String? = """{"result":"ok"}""",
        status: MessageStatus = MessageStatus.SUCCESS,
        requestTimestamp: Long = 1000L,
        responseTimestamp: Long? = 1500L,
    ) = MessageEntry(
        id = id,
        requestId = "req-1",
        handlerName = handlerName,
        direction = direction,
        tag = tag,
        requestData = requestData,
        responseData = responseData,
        status = status,
        requestTimestamp = requestTimestamp,
        responseTimestamp = responseTimestamp,
    )

    @Test
    fun `formatSingleEntry contains handler name`() {
        val text = DariExporter.formatSingleEntry(createEntry(handlerName = "showToast"))
        assertTrue(text.contains("Handler: showToast"))
    }

    @Test
    fun `formatSingleEntry contains direction for WEB_TO_APP`() {
        val text = DariExporter.formatSingleEntry(createEntry(direction = MessageDirection.WEB_TO_APP))
        assertTrue(text.contains("Web \u2192 App"))
    }

    @Test
    fun `formatSingleEntry contains direction for APP_TO_WEB`() {
        val text = DariExporter.formatSingleEntry(createEntry(direction = MessageDirection.APP_TO_WEB))
        assertTrue(text.contains("App \u2192 Web"))
    }

    @Test
    fun `formatSingleEntry contains status`() {
        val text = DariExporter.formatSingleEntry(createEntry(status = MessageStatus.ERROR))
        assertTrue(text.contains("Status: ERROR"))
    }

    @Test
    fun `formatSingleEntry contains tag when present`() {
        val text = DariExporter.formatSingleEntry(createEntry(tag = "PaymentWebView"))
        assertTrue(text.contains("Tag: PaymentWebView"))
    }

    @Test
    fun `formatSingleEntry shows dash for null tag`() {
        val text = DariExporter.formatSingleEntry(createEntry(tag = null))
        assertTrue(text.contains("Tag: -"))
    }

    @Test
    fun `formatSingleEntry contains duration`() {
        val text = DariExporter.formatSingleEntry(
            createEntry(requestTimestamp = 1000L, responseTimestamp = 1500L),
        )
        assertTrue(text.contains("Duration: 500 ms"))
    }

    @Test
    fun `formatSingleEntry omits duration when no response timestamp`() {
        val text = DariExporter.formatSingleEntry(
            createEntry(responseTimestamp = null),
        )
        assertTrue(!text.contains("Duration:"))
    }

    @Test
    fun `formatSingleEntry pretty prints JSON request data`() {
        val text = DariExporter.formatSingleEntry(
            createEntry(requestData = """{"name":"dari"}"""),
        )
        assertTrue(text.contains("\"name\": \"dari\""))
    }

    @Test
    fun `formatSingleEntry shows empty marker for null data`() {
        val text = DariExporter.formatSingleEntry(
            createEntry(requestData = null, responseData = null),
        )
        assertTrue(text.contains("(empty)"))
    }

    @Test
    fun `formatSingleEntry shows truncated indicator`() {
        val entry = MessageEntry(
            id = 1L,
            requestId = "req-1",
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
            requestData = "data",
            requestDataTruncated = true,
            requestTimestamp = 1000L,
        )
        val text = DariExporter.formatSingleEntry(entry)
        assertTrue(text.contains("(truncated)"))
    }

    @Test
    fun `formatSingleEntry handles non-JSON data gracefully`() {
        val text = DariExporter.formatSingleEntry(
            createEntry(requestData = "not json at all"),
        )
        assertTrue(text.contains("not json at all"))
    }

    @Test
    fun `json serialization produces valid JSON array`() {
        val entries = listOf(
            createEntry(id = 1L, handlerName = "handler1"),
            createEntry(id = 2L, handlerName = "handler2"),
        )
        val exportable = entries.map { it.toExportable() }
        val json = exportJson
        val jsonString = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ExportableMessage.serializer()),
            exportable,
        )

        val parsed = Json.parseToJsonElement(jsonString).jsonArray
        assertEquals(2, parsed.size)
        assertEquals("handler1", parsed[0].jsonObject["handler_name"]?.jsonPrimitive?.content)
        assertEquals("handler2", parsed[1].jsonObject["handler_name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `json serialization uses snake_case field names`() {
        val exportable = listOf(createEntry().toExportable())
        val json = exportJson
        val jsonString = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ExportableMessage.serializer()),
            exportable,
        )

        assertTrue(jsonString.contains("handler_name"))
        assertTrue(jsonString.contains("request_id"))
        assertTrue(jsonString.contains("request_data"))
        assertTrue(jsonString.contains("response_data"))
        assertTrue(jsonString.contains("request_timestamp"))
        assertTrue(jsonString.contains("response_timestamp"))
        assertTrue(jsonString.contains("duration_ms"))
        assertTrue(jsonString.contains("request_data_truncated"))
        assertTrue(jsonString.contains("response_data_truncated"))
    }

    @Test
    fun `json serialization includes null fields`() {
        val entry = createEntry(
            requestData = null,
            responseData = null,
            responseTimestamp = null,
        )
        val exportable = listOf(entry.toExportable())
        val json = exportJson
        val jsonString = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ExportableMessage.serializer()),
            exportable,
        )
        val parsed = Json.parseToJsonElement(jsonString).jsonArray[0].jsonObject

        assertTrue(parsed.containsKey("request_data"))
        assertTrue(parsed.containsKey("response_data"))
        assertTrue(parsed.containsKey("response_timestamp"))
        assertTrue(parsed.containsKey("duration_ms"))
    }
}