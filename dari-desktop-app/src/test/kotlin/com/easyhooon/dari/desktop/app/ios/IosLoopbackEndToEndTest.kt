package com.easyhooon.dari.desktop.app.ios

import com.easyhooon.dari.inspector.protocol.ObserveEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the Swift SDK's DariLoopbackDemo over the Mac's loopback, the path a Simulator app uses.
 * Skipped unless run with `-Pdari.iosDemo=<path to the built DariLoopbackDemo>`.
 */
class IosLoopbackEndToEndTest {
    private var demo: Process? = null

    @AfterTest
    fun tearDown() {
        demo?.destroy()
    }

    @Test
    fun `desktop client reads messages recorded by the Swift SDK`() = runBlocking {
        val binary = System.getProperty("dari.iosDemo")?.let(::File)
        assumeTrue("set -Pdari.iosDemo to run", binary?.canExecute() == true)
        val process = ProcessBuilder(binary!!.path).redirectErrorStream(true).start().also { demo = it }
        val port = process.inputStream.bufferedReader().readLine().removePrefix("DARI_PORT=").toInt()

        val simulator = IosSimulator("test", ports = port..port)
        assertEquals(listOf("DariLoopbackDemo"), simulator.apps())
        simulator.open("DariLoopbackDemo").use { conn ->
            assertEquals("macos", conn.app.platform)
            val events = conn.observe()
            val messages = conn.list()
            assertEquals(listOf("getUser", "onResume", "openCamera", "log"), messages.map { it.handlerName })
            assertEquals(listOf("SUCCESS", "ERROR", "IN_PROGRESS", "SUCCESS"), messages.map { it.status })
            assertEquals(listOf("WEB_TO_APP", "APP_TO_WEB", "WEB_TO_APP", "WEB_TO_APP"), messages.map { it.direction })
            assertEquals("https://example.com/checkout", messages.first().pageUrl)
            assertEquals("Open camera", messages[2].displayName)

            val first = messages.first().id
            assertEquals("""{"accessToken":"secret-value","id":7}""", conn.detail(first).requestData)
            assertEquals(setOf(first), conn.search("secret-value"))
            assertTrue(conn.setMasking(true))
            assertTrue(conn.detail(first).requestData!!.contains("\"accessToken\":\"***\""))
            assertEquals(emptySet(), conn.search("secret-value"))

            conn.clear()
            withTimeout(5_000) { assertEquals(ObserveEvent.Cleared, events.receive()) }
            assertEquals(emptyList(), conn.list())
        }
    }
}
