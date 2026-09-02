package com.easyhooon.dari.export

import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.MessagePayloadMetadata
import com.easyhooon.dari.PayloadContentType
import com.easyhooon.dari.PayloadDecodeStatus
import com.easyhooon.dari.RawPayloadPreview
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
    fun `formatSingleEntry contains display name as a separate field`() {
        val text = DariExporter.formatSingleEntry(
            createEntry(handlerName = "N:4-1").copy(displayName = "오늘의 건강목표"),
        )

        assertTrue(text.contains("Display name: 오늘의 건강목표"))
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
    fun `formatSingleEntry includes protobuf raw preview`() {
        val metadata = MessagePayloadMetadata(
            contentType = PayloadContentType.PROTOBUF,
            originalSizeBytes = 4,
            decodeStatus = PayloadDecodeStatus.DECODED,
            rawPreview = RawPayloadPreview("AQID", 3, true),
        )
        val text = DariExporter.formatSingleEntry(
            createEntry().copy(requestPayloadMetadata = metadata),
        )

        assertTrue(text.contains("Request (Raw / Hex)"))
        assertTrue(text.contains("01 02 03"))
        assertTrue(text.contains("Request (Raw / Base64)"))
        assertTrue(text.contains("AQID"))
        assertTrue(text.contains("Captured: 3 of 4 bytes (truncated)"))
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
        assertTrue(jsonString.contains("display_name"))
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
        assertTrue(parsed.containsKey("display_name"))
        assertTrue(parsed.containsKey("response_data"))
        assertTrue(parsed.containsKey("response_timestamp"))
        assertTrue(parsed.containsKey("duration_ms"))
    }

    @Test
    fun `suggestedFilename uses txt extension for TEXT format`() {
        val name = DariExporter.suggestedFilename(ExportFormat.TEXT)
        assertTrue(name.startsWith("dari_export_"))
        assertTrue(name.endsWith(".txt"))
    }

    @Test
    fun `suggestedFilename uses json extension for JSON format`() {
        val name = DariExporter.suggestedFilename(ExportFormat.JSON)
        assertTrue(name.startsWith("dari_export_"))
        assertTrue(name.endsWith(".json"))
    }

    @Test
    fun `suggestedFilename contains a timestamp segment`() {
        val name = DariExporter.suggestedFilename(ExportFormat.JSON)
        // Format: dari_export_yyyyMMdd_HHmmss.json -> strip prefix + extension
        val timestamp = name.removePrefix("dari_export_").removeSuffix(".json")
        assertEquals(15, timestamp.length) // yyyyMMdd_HHmmss
        assertTrue(timestamp.matches(Regex("""\d{8}_\d{6}""")))
    }

    @Test
    fun `mimeTypeFor returns text plain for TEXT`() {
        assertEquals("text/plain", DariExporter.mimeTypeFor(ExportFormat.TEXT))
    }

    @Test
    fun `mimeTypeFor returns application json for JSON`() {
        assertEquals("application/json", DariExporter.mimeTypeFor(ExportFormat.JSON))
    }
}
