package com.easyhooon.dari.ui.components

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonViewerModelTest {
    @Test
    fun `valid nested JSON is parsed as structured content`() {
        val content = parseJsonViewerContent(
            """{"items":[{"id":1,"name":"A"}],"meta":{"page":1}}""",
        )

        val root = assertType<JsonViewerContent.Structured>(content).element
        val rootObject = assertType<JsonObject>(root)
        val items = assertType<JsonArray>(rootObject["items"])
        assertType<JsonObject>(items.single())
        assertType<JsonObject>(rootObject["meta"])
    }

    @Test
    fun `container summaries include collection sizes`() {
        val content = parseJsonViewerContent(
            """{"items":[1,2],"emptyItems":[],"meta":{"page":1},"emptyMeta":{}}""",
        )
        val root = assertType<JsonObject>(
            assertType<JsonViewerContent.Structured>(content).element,
        )

        assertEquals("[2 items]", root.getValue("items").collapsedSummary())
        assertEquals("[0 items]", root.getValue("emptyItems").collapsedSummary())
        assertEquals("{1 field}", root.getValue("meta").collapsedSummary())
        assertEquals("{0 fields}", root.getValue("emptyMeta").collapsedSummary())
    }

    @Test
    fun `invalid JSON is preserved as plain text`() {
        val malformed = "{not-json"

        val content = assertType<JsonViewerContent.PlainText>(
            parseJsonViewerContent(malformed),
        )

        assertEquals(malformed, content.text)
    }

    @Test
    fun `collapsing an array hides only its descendants`() {
        val root = structuredElement(
            """{"items":[{"id":1}],"meta":{"page":1}}""",
        )

        val rows = buildJsonTreeRows(root, collapsedPaths = setOf("$/items"))

        val itemsRow = rows.single { it.containerPath == "$/items" }
        assertEquals(false, itemsRow.expanded)
        assertEquals("\"items\": [1 item],", itemsRow.text)
        assertTrue(rows.none { it.text.contains("\"id\"") })
        assertTrue(rows.any { it.text == "\"page\": 1" })
    }

    @Test
    fun `collapsing the root shows one summary row`() {
        val root = structuredElement(
            """{"items":[],"meta":{}}""",
        )

        val rows = buildJsonTreeRows(root, collapsedPaths = setOf("$"))

        assertEquals(1, rows.size)
        assertEquals("{2 fields}", rows.single().text)
        assertEquals(false, rows.single().expanded)
    }

    private fun structuredElement(json: String) =
        assertType<JsonViewerContent.Structured>(parseJsonViewerContent(json)).element

    private inline fun <reified T> assertType(value: Any?): T {
        assertTrue("Expected ${T::class.simpleName}, but was ${value?.let { it::class.simpleName }}", value is T)
        return value as T
    }
}
