package com.easyhooon.dari.desktop.app.ios

import com.easyhooon.dari.desktop.app.adb.AdbDevice
import com.easyhooon.dari.desktop.app.client.InspectorConnection
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Finds Dari iOS apps in Simulators. Simulators share the Mac's loopback, so each app listens on the first
 * free port of [ports] (`Dari.defaultPorts` in the Swift SDK) and the desktop probes the span. No adb or usbmuxd.
 */
class IosSimulator(
    private val clientVersion: String,
    private val ports: IntRange = DEFAULT_PORTS,
    private val connect: (port: Int) -> Socket = ::loopbackSocket,
) {
    /** Blocking: applicationIds of the Dari apps answering on the span, in port order. */
    fun apps(): List<String> = ports.mapNotNull { port -> probe(port)?.use { it.app.applicationId } }.distinct()

    /** Blocking: connects to [applicationId] wherever in the span it landed this launch. */
    fun open(applicationId: String): InspectorConnection {
        for (port in ports) {
            val conn = probe(port) ?: continue
            if (conn.app.applicationId == applicationId) return conn
            conn.close()
        }
        throw IOException("$applicationId isn't running Dari in a Simulator")
    }

    private fun probe(port: Int): InspectorConnection? = try {
        InspectorConnection.handshake(connect(port), "iOS app", clientVersion, onClose = {}, timeoutMs = PROBE_TIMEOUT_MS)
    } catch (_: IOException) {
        null
    }

    companion object {
        /** Stands in for a device in the device picker; all Simulators on this Mac share it. */
        const val SERIAL = "ios-simulator"
        val DEVICE = AdbDevice(SERIAL, "iOS Simulator")
        val DEFAULT_PORTS = 38_670..38_677

        private const val CONNECT_TIMEOUT_MS = 200
        private const val PROBE_TIMEOUT_MS = 1_000

        val isSupported: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Mac")

        private fun loopbackSocket(port: Int): Socket {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS)
            } catch (e: IOException) {
                socket.close()
                throw e
            }
            return socket
        }
    }
}
