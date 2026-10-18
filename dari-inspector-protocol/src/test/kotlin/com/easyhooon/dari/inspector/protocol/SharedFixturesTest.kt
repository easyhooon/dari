package com.easyhooon.dari.inspector.protocol

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checks that dari-web's expected output in fixtures/messages is valid wire JSON. */
class SharedFixturesTest {

    // Unlike InspectorProtocol.json, unknown keys fail, so the web side can't drift silently.
    private val strictJson = Json(InspectorProtocol.json) { ignoreUnknownKeys = false }
    private val serializer = ListSerializer(WireMessageDetail.serializer())

    private val fixtures: List<File> =
        File(System.getProperty("dari.fixtures.dir")).listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }

    @Test
    fun `fixtures are present`() {
        assertTrue(fixtures.isNotEmpty(), "No fixtures found")
    }

    @Test
    fun `expected entries decode strictly and re-encode unchanged`() {
        fixtures.forEach { file ->
            val expected = Json.parseToJsonElement(file.readText()).jsonObject.getValue("expected").jsonArray
            val details = strictJson.decodeFromJsonElement(serializer, expected)
            val reencoded = InspectorProtocol.json.encodeToJsonElement(serializer, details) as JsonArray
            assertEquals(expected, reencoded, file.name)
        }
    }

    @Test
    fun `sizes are UTF-8 byte counts of the payloads`() {
        fixtures.forEach { file ->
            val expected = Json.parseToJsonElement(file.readText()).jsonObject.getValue("expected")
            strictJson.decodeFromJsonElement(serializer, expected).forEach { detail ->
                assertEquals(detail.requestData.utf8Size(), detail.summary.requestSizeBytes, file.name)
                assertEquals(detail.responseData.utf8Size(), detail.summary.responseSizeBytes, file.name)
            }
        }
    }

    private fun String?.utf8Size(): Int = this?.encodeToByteArray()?.size ?: 0
}
