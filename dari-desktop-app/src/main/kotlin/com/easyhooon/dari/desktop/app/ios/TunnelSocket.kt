package com.easyhooon.dari.desktop.app.ios

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import kotlin.concurrent.thread

/**
 * Exposes a usbmux [channel] as a plain loopback [Socket], so InspectorConnection's read timeouts work unchanged.
 * Unix-domain channels have no `socket()` adaptor, and their stream wrappers block reads and writes on one lock.
 */
internal fun SocketChannel.asLoopbackSocket(): Socket {
    val channel = this
    ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
        val outer = Socket(server.inetAddress, server.localPort)
        val inner = try {
            server.accept().also { accepted ->
                // Another local process could race to the ephemeral port; only our own client may pass.
                if (accepted.port != outer.localPort) {
                    accepted.close()
                    throw IOException("Unexpected connection on the usbmux bridge")
                }
            }
        } catch (e: IOException) {
            outer.close()
            channel.close()
            throw e
        }
        val closeAll = {
            runCatching { inner.close() }
            runCatching { channel.close() }
        }
        thread(name = "dari-usbmux-down", isDaemon = true) {
            runCatching {
                val buffer = ByteBuffer.allocate(BUFFER_BYTES)
                val output = inner.getOutputStream()
                while (channel.read(buffer.clear()) >= 0) output.write(buffer.array(), 0, buffer.position())
            }
            closeAll()
        }
        thread(name = "dari-usbmux-up", isDaemon = true) {
            runCatching {
                val bytes = ByteArray(BUFFER_BYTES)
                val input = inner.getInputStream()
                while (true) {
                    val count = input.read(bytes)
                    if (count < 0) break
                    channel.writeFully(ByteBuffer.wrap(bytes, 0, count))
                }
            }
            closeAll()
        }
        return outer
    }
}

private const val BUFFER_BYTES = 64 * 1024
