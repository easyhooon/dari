package com.easyhooon.dari.desktop.app.ios

import com.easyhooon.dari.inspector.protocol.AppInfo
import com.easyhooon.dari.inspector.protocol.ClientHello
import com.easyhooon.dari.inspector.protocol.FrameReader
import com.easyhooon.dari.inspector.protocol.FrameWriter
import com.easyhooon.dari.inspector.protocol.InspectorProtocol
import com.easyhooon.dari.inspector.protocol.ServerHello
import java.io.Closeable
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosSimulatorTest {
    private val apps = mutableListOf<FakeIosApp>()

    @AfterTest
    fun tearDown() = apps.forEach { it.close() }

    @Test
    fun `finds apps across the span and skips ports that refuse or don't speak the protocol`() {
        val simulator = simulator(listOf(app("com.example.one"), null, silent(), app("com.example.two")))
        assertEquals(listOf("com.example.one", "com.example.two"), simulator.apps())
    }

    @Test
    fun `opens the app with the requested id wherever it landed`() {
        val simulator = simulator(listOf(app("com.example.one"), app("com.example.two")))
        simulator.open("com.example.two").use { assertEquals("com.example.two", it.app.applicationId) }
        assertFailsWith<IOException> { simulator.open("com.example.missing") }
    }

    private fun simulator(slots: List<Int?>) = IosSimulator("test", ports = slots.indices) { slot ->
        val port = slots[slot] ?: throw ConnectException("refused")
        Socket(InetAddress.getLoopbackAddress(), port)
    }

    private fun app(applicationId: String): Int = FakeIosApp(applicationId).also(apps::add).port

    // Accepts and never answers, like an unrelated service on a port in the span.
    private fun silent(): Int = FakeIosApp(null).also(apps::add).port

    private class FakeIosApp(applicationId: String?) : Closeable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true) {
                while (true) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) {
                        runCatching {
                            client.use {
                                if (applicationId == null) {
                                    it.getInputStream().read()
                                    return@use
                                }
                                FrameReader(it.getInputStream()).read(ClientHello.serializer())
                                FrameWriter(it.getOutputStream()).write(
                                    ServerHello.serializer(),
                                    ServerHello(accepted = true, protocolVersion = InspectorProtocol.VERSION, app = appInfo(applicationId)),
                                )
                                it.getInputStream().read()
                            }
                        }
                    }
                }
            }
        }

        override fun close() = server.close()

        private fun appInfo(applicationId: String) = AppInfo(
            applicationId = applicationId,
            appName = "App",
            appVersion = "1.0",
            dariVersion = "3.0.0-alpha01",
            deviceModel = "iPhone17,1 (Simulator)",
            sdkInt = 0,
            platform = "ios",
            osVersion = "18.2.0",
        )
    }
}
