package com.easyhooon.dari.desktop.app

import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.inspector.protocol.WireMessageSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageExportTest {
    private val detail = WireMessageDetail(
        summary = WireMessageSummary(
            id = 7,
            requestId = "req-1",
            handlerName = "getUser",
            direction = "WEB_TO_APP",
            status = "SUCCESS",
            requestTimestamp = 1_000,
            responseTimestamp = 1_250,
            requestSizeBytes = 9,
            responseSizeBytes = 13,
        ),
        requestData = """{"id":1}""",
        responseData = "plain text",
    )

    @Test
    fun `json uses the in-app field names and order`() {
        val array = Json.parseToJsonElement(MessageExport.format(listOf(detail), ExportFormat.JSON)).jsonArray
        val message = array.single().jsonObject
        assertEquals(listOf("id", "request_id", "handler_name", "display_name"), message.keys.take(4))
        assertEquals("getUser", message["handler_name"]!!.jsonPrimitive.content)
        assertEquals(250, message["duration_ms"]!!.jsonPrimitive.long)
        assertEquals("""{"id":1}""", message["request_data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `text matches the in-app share layout`() {
        val utc = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val text = MessageExport.text(detail, utc)
        assertTrue(text.startsWith("Handler: getUser\nDirection: Web → App\nStatus: SUCCESS\nTag: -\nRequest ID: req-1\n"))
        assertTrue("Request time: ${utc.format(Date(1_000))}" in text)
        assertTrue("Duration: 250 ms" in text)
        assertTrue("---------- Request (Decoded) ----------\n\n{\n    \"id\": 1\n}" in text)
        assertTrue("---------- Response (Decoded) ----------\n\nplain text" in text)
    }

    @Test
    fun `suggests the in-app file name`() {
        val name = MessageExport.suggestedFileName(ExportFormat.JSON)
        assertTrue(Regex("""dari_export_\d{8}_\d{6}\.json""").matches(name), name)
    }
}
