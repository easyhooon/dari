package com.easyhooon.dari.desktop.app.ui

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportStartDirectoryTest {
    private val home = Files.createTempDirectory("home").toFile()

    @AfterTest
    fun tearDown() {
        home.deleteRecursively()
    }

    @Test
    fun `first export opens Downloads`() {
        val downloads = home.resolve("Downloads").apply { mkdirs() }
        assertEquals(downloads, exportStartDirectory(last = null, home = home))
    }

    @Test
    fun `later exports open the last folder`() {
        home.resolve("Downloads").mkdirs()
        val last = home.resolve("reports").apply { mkdirs() }
        assertEquals(last, exportStartDirectory(last = last.path, home = home))
    }

    @Test
    fun `a removed last folder falls back to Downloads, then home`() {
        val gone = home.resolve("gone").path
        val downloads = home.resolve("Downloads").apply { mkdirs() }
        assertEquals(downloads, exportStartDirectory(last = gone, home = home))
        downloads.delete()
        assertEquals(home, exportStartDirectory(last = gone, home = home))
    }
}
