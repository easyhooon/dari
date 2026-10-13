package com.easyhooon.dari.inspector

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens on an abstract-namespace socket reached via `adb forward`.
 * Only adb (shell, uid 2000) or root may connect, so other apps on the device can't read messages.
 */
internal class DariInspectorServer(
    private val socketName: String,
    private val runSession: (InputStream, OutputStream) -> Unit,
) {
    @Volatile
    private var running = false
    private var server: LocalServerSocket? = null
    private val clients = CopyOnWriteArraySet<LocalSocket>()

    private val _connectedClients = MutableStateFlow(0)

    /** Desktops currently connected; the in-app UI goes quiet while this is above zero. */
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

    @Synchronized
    fun start() {
        if (running) return
        val socket = try {
            LocalServerSocket(socketName)
        } catch (e: IOException) {
            Log.w(TAG, "Desktop inspector could not listen on @$socketName", e)
            return
        }
        server = socket
        running = true
        thread(name = "dari-inspector-accept", isDaemon = true) { acceptLoop(socket) }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        // close() does not interrupt accept() on an abstract socket, so wake it with a connection.
        runCatching { LocalSocket().use { it.connect(LocalSocketAddress(socketName)) } }
        runCatching { server?.close() }
        server = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        _connectedClients.value = 0
    }

    private fun acceptLoop(serverSocket: LocalServerSocket) {
        while (running) {
            val client = try {
                serverSocket.accept()
            } catch (_: IOException) {
                break
            }
            if (!isTrustedPeer(client) || !register(client)) {
                runCatching { client.close() }
                continue
            }
            thread(name = "dari-inspector-session", isDaemon = true) { serve(client) }
        }
    }

    // Same lock as stop(), so a client accepted while stopping is never added after the clear.
    @Synchronized
    private fun register(client: LocalSocket): Boolean {
        if (!running) return false
        clients += client
        _connectedClients.value = clients.size
        return true
    }

    private fun isTrustedPeer(client: LocalSocket): Boolean {
        val uid = runCatching { client.peerCredentials.uid }.getOrNull()
        val trusted = uid in TRUSTED_UIDS
        if (!trusted) Log.w(TAG, "Rejected desktop inspector connection from uid=$uid")
        return trusted
    }

    @Suppress("TooGenericExceptionCaught") // Any session failure only closes that connection.
    private fun serve(client: LocalSocket) {
        try {
            runSession(client.inputStream, client.outputStream)
        } catch (e: Exception) {
            Log.d(TAG, "Desktop inspector session ended", e)
        } finally {
            clients -= client
            _connectedClients.value = clients.size
            runCatching { client.close() }
        }
    }

    private companion object {
        const val TAG = "DariInspector"
        const val ROOT_UID = 0
        const val SHELL_UID = 2000
        val TRUSTED_UIDS = setOf(ROOT_UID, SHELL_UID)
    }
}
