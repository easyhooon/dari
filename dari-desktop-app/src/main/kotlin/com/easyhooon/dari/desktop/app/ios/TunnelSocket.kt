package com.easyhooon.dari.desktop.app.ios

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Exposes a usbmux [channel] as a plain loopback [Socket], so InspectorConnection's read timeouts work unchanged.
 * Unix-domain channels have no `socket()` adaptor, and their stream wrappers block reads and writes on one lock.
 */
internal fun SocketChannel.asLoopbackSocket(): Socket {
    val channel = this
    val server = try {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    } catch (e: IOException) {
        channel.close()
        throw e
    }
    server.use {
        val outer = try {
            Socket(server.inetAddress, server.localPort)
        } catch (e: IOException) {
            channel.close()
            throw e
        }
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
        // An end of stream is passed on as a half-close; everything closes once both directions end or one fails.
        val ended = AtomicInteger()
        val closeAll = {
            runCatching { inner.close() }
            runCatching { channel.close() }
        }
        thread(name = "dari-usbmux-down", isDaemon = true) {
            val clean = runCatching {
                val buffer = ByteBuffer.allocate(BUFFER_BYTES)
                val output = inner.getOutputStream()
                while (channel.read(buffer.clear()) >= 0) output.write(buffer.array(), 0, buffer.position())
                inner.shutdownOutput()
            }.isSuccess
            if (!clean || ended.incrementAndGet() == 2) closeAll()
        }
        thread(name = "dari-usbmux-up", isDaemon = true) {
            val clean = runCatching {
                val bytes = ByteArray(BUFFER_BYTES)
                val input = inner.getInputStream()
                while (true) {
                    val count = input.read(bytes)
                    if (count < 0) break
                    channel.writeFully(ByteBuffer.wrap(bytes, 0, count))
                }
                channel.shutdownOutput()
            }.isSuccess
            if (!clean || ended.incrementAndGet() == 2) closeAll()
        }
        return outer
    }
}

private const val BUFFER_BYTES = 64 * 1024
