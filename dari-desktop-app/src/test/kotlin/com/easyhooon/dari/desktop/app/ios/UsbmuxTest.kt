package com.easyhooon.dari.desktop.app.ios

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UsbmuxTest {
    private val dir: Path = Files.createTempDirectory("mux")
    private val path: Path = dir.resolve("usbmuxd")
    private val server = FakeUsbmuxd(path)

    @AfterTest
    fun tearDown() {
        server.close()
        Files.deleteIfExists(path)
        Files.deleteIfExists(dir)
    }

    @Test
    fun `lists only devices attached over USB`() {
        assertEquals(listOf(UsbmuxDevice(7, "00008140-0001")), Usbmux(path).devices())
    }

    @Test
    fun `tunnels to a listening port and exposes it as a loopback socket`() {
        Usbmux(path).connect(7, APP_PORT).asLoopbackSocket().use { socket ->
            socket.soTimeout = 2_000
            socket.getOutputStream().write("ping".encodeToByteArray())
            assertEquals("ping", socket.getInputStream().readNBytes(4).decodeToString())
        }
    }

    @Test
    fun `fails when nothing listens on the device port`() {
        assertFailsWith<IOException> { Usbmux(path).connect(7, APP_PORT + 1) }
    }

    @Test
    fun `reads the device name from lockdownd`() {
        assertEquals("Jihun's iPhone", Usbmux(path).deviceName(7))
    }

    @Test
    fun `plist round trips the types usbmuxd uses`() {
        val values = mapOf("A" to "x < y & z", "B" to 3, "C" to true, "D" to listOf(mapOf("E" to 1L)))
        assertEquals(
            mapOf("A" to "x < y & z", "B" to 3L, "C" to true, "D" to listOf(mapOf("E" to 1L))),
            XmlPlist.decode(XmlPlist.encode(values)),
        )
    }

    /** Answers ListDevices and Connect like usbmuxd; a connected app port echoes, lockdownd answers DeviceName. */
    private class FakeUsbmuxd(path: Path) : AutoCloseable {
        private val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(path))

        init {
            thread(isDaemon = true) {
                while (true) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { runCatching { client.use(::serve) } }
                }
            }
        }

        private fun serve(client: SocketChannel) {
            val length = client.readFully(16).order(ByteOrder.LITTLE_ENDIAN).int
            val request = XmlPlist.decode(client.readFully(length - 16).array())
            when (request["MessageType"]) {
                "ListDevices" -> reply(client, mapOf("DeviceList" to listOf(device(7, "USB", "00008140-0001"), device(8, "Network", "wifi"))))
                "Connect" -> when ((request["PortNumber"] as Long).toInt()) {
                    Usbmux.swapBytes(APP_PORT) -> {
                        reply(client, mapOf("MessageType" to "Result", "Number" to 0))
                        val buffer = ByteBuffer.allocate(64)
                        while (client.read(buffer.clear()) > 0) client.writeFully(buffer.flip())
                    }
                    Usbmux.swapBytes(Usbmux.LOCKDOWN_PORT) -> {
                        reply(client, mapOf("MessageType" to "Result", "Number" to 0))
                        client.readFully(client.readFully(4).int)
                        val body = XmlPlist.encode(mapOf("Key" to "DeviceName", "Value" to "Jihun's iPhone"))
                        client.writeFully(ByteBuffer.allocate(4 + body.size).putInt(body.size).put(body).flip())
                    }
                    else -> reply(client, mapOf("MessageType" to "Result", "Number" to 3))
                }
            }
        }

        private fun device(id: Int, connection: String, serial: String) = mapOf(
            "DeviceID" to id,
            "Properties" to mapOf("ConnectionType" to connection, "SerialNumber" to serial),
        )

        private fun reply(client: SocketChannel, values: Map<String, Any>) {
            val body = XmlPlist.encode(values)
            val buffer = ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(16 + body.size).putInt(1).putInt(8).putInt(1).put(body)
            client.writeFully(buffer.flip())
        }

        override fun close() = server.close()
    }

    private companion object {
        const val APP_PORT = 38_670
    }
}
