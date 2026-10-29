package com.easyhooon.dari.desktop.app.ios

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Path

/** An iPhone or iPad attached over USB, as usbmuxd reports it. [deviceId] is valid only while it stays attached. */
data class UsbmuxDevice(val deviceId: Int, val serial: String)

/**
 * Talks to usbmuxd, the macOS daemon behind Finder and Xcode device access. Like `adb forward`, [connect] opens a
 * tunnel to a TCP port on the device's own loopback, which is where Dari iOS apps listen.
 * Protocol modelled on Necto's NectoUSBHub and PeerTalk's PTUSBHub (both MIT).
 */
class Usbmux(private val socketPath: Path = DEFAULT_SOCKET) {
    val isAvailable: Boolean get() = socketPath.toFile().exists()

    /** Blocking: devices attached over USB. Wi-Fi pairings are skipped; they would need a network path. */
    fun devices(): List<UsbmuxDevice> = open().use { channel ->
        channel.request(message("ListDevices"))
        val list = channel.reply()["DeviceList"] as? List<*> ?: throw IOException("usbmuxd sent no DeviceList")
        list.mapNotNull { entry ->
            val device = entry as? Map<*, *> ?: return@mapNotNull null
            val properties = device["Properties"] as? Map<*, *> ?: return@mapNotNull null
            if (properties["ConnectionType"] != "USB") return@mapNotNull null
            val id = (device["DeviceID"] as? Long)?.toInt() ?: return@mapNotNull null
            UsbmuxDevice(id, properties["SerialNumber"] as? String ?: return@mapNotNull null)
        }
    }

    /** Blocking: a channel tunnelled to [port] on the device's loopback. Fails when nothing listens there. */
    fun connect(deviceId: Int, port: Int): SocketChannel {
        val channel = open()
        try {
            // usbmuxd wants the port in network byte order even inside the plist integer.
            channel.request(message("Connect") + mapOf("DeviceID" to deviceId, "PortNumber" to swapBytes(port)))
            val result = (channel.reply()["Number"] as? Long)?.toInt()
            if (result != RESULT_OK) throw IOException("usbmuxd could not connect to port $port (result $result)")
            return channel
        } catch (e: IOException) {
            channel.close()
            throw e
        }
    }

    /** Blocking: the name set on the device, e.g. "Jihun's iPhone", read from lockdownd without pairing. */
    fun deviceName(deviceId: Int): String? = runCatching {
        connect(deviceId, LOCKDOWN_PORT).use { channel ->
            val request = XmlPlist.encode(mapOf("Request" to "GetValue", "Key" to "DeviceName", "Label" to CLIENT_NAME))
            channel.writeFully(ByteBuffer.allocate(4 + request.size).putInt(request.size).put(request).flip())
            val length = channel.readFully(4).int
            if (length !in 1..MAX_PLIST_BYTES) throw IOException("Bad lockdown reply length $length")
            XmlPlist.decode(channel.readFully(length).array())["Value"] as? String
        }
    }.getOrNull()

    private fun open(): SocketChannel {
        val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
        try {
            channel.connect(UnixDomainSocketAddress.of(socketPath))
        } catch (e: IOException) {
            channel.close()
            throw e
        }
        return channel
    }

    private fun SocketChannel.request(values: Map<String, Any>) {
        val body = XmlPlist.encode(values)
        val buffer = ByteBuffer.allocate(HEADER_BYTES + body.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(HEADER_BYTES + body.size).putInt(VERSION_PLIST).putInt(MESSAGE_PLIST).putInt(TAG)
            .put(body)
        writeFully(buffer.flip())
    }

    private fun SocketChannel.reply(): Map<String, Any?> {
        val length = readFully(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).int
        if (length - HEADER_BYTES !in 1..MAX_PLIST_BYTES) throw IOException("Bad usbmuxd reply length $length")
        return XmlPlist.decode(readFully(length - HEADER_BYTES).array())
    }

    private fun message(type: String): Map<String, Any> = mapOf(
        "MessageType" to type,
        "ClientVersionString" to CLIENT_NAME,
        "ProgName" to CLIENT_NAME,
        "kLibUSBMuxVersion" to LIBUSBMUX_VERSION,
    )

    companion object {
        val DEFAULT_SOCKET: Path = Path.of("/var/run/usbmuxd")

        /** lockdownd, which every iOS device runs; answers basic queries before pairing. */
        const val LOCKDOWN_PORT = 62_078

        private const val CLIENT_NAME = "dari-desktop"
        private const val HEADER_BYTES = 16
        private const val VERSION_PLIST = 1
        private const val MESSAGE_PLIST = 8
        private const val TAG = 1
        private const val LIBUSBMUX_VERSION = 3
        private const val RESULT_OK = 0
        private const val MAX_PLIST_BYTES = 1 shl 20

        internal fun swapBytes(port: Int): Int = ((port and 0xFF) shl 8) or ((port shr 8) and 0xFF)
    }
}

internal fun SocketChannel.writeFully(buffer: ByteBuffer) {
    while (buffer.hasRemaining()) write(buffer)
}

internal fun SocketChannel.readFully(count: Int): ByteBuffer {
    val buffer = ByteBuffer.allocate(count)
    while (buffer.hasRemaining()) {
        if (read(buffer) < 0) throw IOException("usbmuxd closed the connection")
    }
    return buffer.flip()
}
