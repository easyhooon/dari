package com.easyhooon.dari.desktop.app.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticVersionTest {
    @Test
    fun `parses release tags`() {
        assertEquals(SemanticVersion(2, 0, 1), SemanticVersion.parse("2.0.1"))
        assertEquals(SemanticVersion(2, 10, 0), SemanticVersion.parse("v2.10.0"))
    }

    @Test
    fun `rejects anything that is not major minor patch`() {
        listOf("dev", "2.0", "2.0.0-beta", "2.0.0.1", "").forEach { assertNull(SemanticVersion.parse(it), it) }
    }

    @Test
    fun `compares numerically`() {
        assertTrue(SemanticVersion(2, 10, 0) > SemanticVersion(2, 9, 9))
        assertTrue(SemanticVersion(3, 0, 0) > SemanticVersion(2, 99, 99))
        assertTrue(SemanticVersion(2, 0, 1) > SemanticVersion(2, 0, 0))
    }
}
