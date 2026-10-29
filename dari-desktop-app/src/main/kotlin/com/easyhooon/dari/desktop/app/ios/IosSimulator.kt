package com.easyhooon.dari.desktop.app.ios

import com.easyhooon.dari.desktop.app.adb.AdbDevice
import com.easyhooon.dari.desktop.app.client.InspectorConnection
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Finds Dari iOS apps in Simulators. Simulators share the Mac's loopback, so each app listens on the first
 * free port of [ports] (`Dari.defaultPorts` in the Swift SDK) and the desktop probes the span. No adb or usbmuxd.
 *
 * Two Simulators running the same bundle id are not told apart yet; [open] picks the first one.
 */
class IosSimulator(
    private val clientVersion: String,
    private val ports: IntRange = DEFAULT_PORTS,
    private val connect: (port: Int) -> Socket = ::loopbackSocket,
) {
    /** Blocking: applicationIds of the Dari apps answering on the span, in port order. */
    fun apps(): List<String> = probeAll().mapNotNull { conn -> conn?.use { it.app.applicationId } }.distinct()

    /** Blocking: connects to [applicationId] wherever in the span it landed this launch. */
    fun open(applicationId: String): InspectorConnection {
        val found = probeAll().filterNotNull()
        val match = found.firstOrNull { it.app.applicationId == applicationId }
        found.filter { it !== match }.forEach { it.close() }
        return match ?: throw IOException("$applicationId isn't running Dari in a Simulator")
    }

    // In parallel, so a suspended app that accepts but never answers costs one timeout per poll, not one per port.
    private fun probeAll(): List<InspectorConnection?> =
        ports.map { port -> CompletableFuture.supplyAsync({ probe(port) }, probeExecutor) }.map { it.join() }

    // Anything else on these ports, including a process that answers with junk, is skipped.
    private fun probe(port: Int): InspectorConnection? = try {
        InspectorConnection.handshake(connect(port), "iOS app", clientVersion, onClose = {}, timeoutMs = PROBE_TIMEOUT_MS)
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    }

    companion object {
        /** Stands in for a device in the device picker; all Simulators on this Mac share it. */
        const val SERIAL = "ios-simulator"
        val DEVICE = AdbDevice(SERIAL, "iOS Simulator")
        val DEFAULT_PORTS = 38_670..38_677

        private const val CONNECT_TIMEOUT_MS = 200

        // Loopback answers in well under a millisecond; this only bounds apps that are suspended.
        private const val PROBE_TIMEOUT_MS = 250

        private val probeExecutor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "dari-ios-probe").apply { isDaemon = true }
        }

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
