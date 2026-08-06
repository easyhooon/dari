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
    fun `root remains expanded and is not collapsible`() {
        val root = structuredElement(
            """{"items":[],"meta":{}}""",
        )

        val rows = buildJsonTreeRows(root, collapsedPaths = setOf("$"))

        val rootRow = rows.first()
        assertEquals("{", rootRow.text)
        assertEquals(null, rootRow.containerPath)
        assertEquals(null, rootRow.expanded)
        assertTrue(rows.any { it.text == "\"items\": []," })
        assertTrue(rows.any { it.text == "\"meta\": {}" })
    }

    @Test
    fun `root array remains expanded and is not collapsible`() {
        val root = structuredElement("""[{"id":1}]""")

        val rows = buildJsonTreeRows(root, collapsedPaths = setOf("$"))

        val rootRow = rows.first()
        assertEquals("[", rootRow.text)
        assertEquals(null, rootRow.containerPath)
        assertEquals(null, rootRow.expanded)
        assertTrue(rows.any { it.text == "\"id\": 1" })
    }

    @Test
    fun `default state collapses nonempty containers from the second level`() {
        val root = structuredElement(
            """{"trip":{"destination":{"city":"Jeju"},"days":[{"activities":[1]}]}}""",
        )

        val collapsedPaths = defaultCollapsedPaths(root)

        assertEquals(
            setOf(
                "$/trip/destination",
                "$/trip/days",
                "$/trip/days/0",
                "$/trip/days/0/activities",
            ),
            collapsedPaths,
        )
    }

    @Test
    fun `empty containers render without collapse controls`() {
        val root = structuredElement("""{"items":[],"meta":{}}""")

        val rows = buildJsonTreeRows(root, collapsedPaths = emptySet())

        val emptyRows = rows.filter { it.key == "items" || it.key == "meta" }
        assertEquals(listOf("\"items\": [],", "\"meta\": {}"), emptyRows.map { it.text })
        assertTrue(emptyRows.all { it.containerPath == null && it.expanded == null })
    }

    @Test
    fun `primitive rows expose syntax token types`() {
        val root = structuredElement(
            """{"string":"value","number":1,"boolean":true,"null":null}""",
        )

        val tokenTypes = buildJsonTreeRows(root, collapsedPaths = emptySet())
            .filter { it.key != null }
            .associate { it.key to it.tokenType }

        assertEquals(JsonTokenType.STRING, tokenTypes["string"])
        assertEquals(JsonTokenType.NUMBER, tokenTypes["number"])
        assertEquals(JsonTokenType.BOOLEAN, tokenTypes["boolean"])
        assertEquals(JsonTokenType.NULL, tokenTypes["null"])
    }

    @Test
    fun `disabling folding expands every container without controls`() {
        val root = structuredElement(
            """{"trip":{"days":[{"activities":[1]}]}}""",
        )

        val rows = buildJsonTreeRows(
            element = root,
            collapsedPaths = defaultCollapsedPaths(root),
            foldingEnabled = false,
        )

        assertTrue(rows.all { it.containerPath == null && it.expanded == null })
        assertTrue(rows.any { it.text == "1" })
    }

    private fun structuredElement(json: String) =
        assertType<JsonViewerContent.Structured>(parseJsonViewerContent(json)).element

    private inline fun <reified T> assertType(value: Any?): T {
        assertTrue("Expected ${T::class.simpleName}, but was ${value?.let { it::class.simpleName }}", value is T)
        return value as T
    }
}
