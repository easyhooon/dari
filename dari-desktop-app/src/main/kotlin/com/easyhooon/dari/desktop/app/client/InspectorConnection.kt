package com.easyhooon.dari.desktop.app.client

import com.easyhooon.dari.desktop.app.adb.Adb
import com.easyhooon.dari.inspector.protocol.AppInfo
import com.easyhooon.dari.inspector.protocol.ClientHello
import com.easyhooon.dari.inspector.protocol.DetailInput
import com.easyhooon.dari.inspector.protocol.FrameReader
import com.easyhooon.dari.inspector.protocol.FrameWriter
import com.easyhooon.dari.inspector.protocol.InspectorError
import com.easyhooon.dari.inspector.protocol.InspectorErrorCode
import com.easyhooon.dari.inspector.protocol.InspectorFrame
import com.easyhooon.dari.inspector.protocol.InspectorOperations
import com.easyhooon.dari.inspector.protocol.InspectorProtocol
import com.easyhooon.dari.inspector.protocol.InspectorSettings
import com.easyhooon.dari.inspector.protocol.ListInput
import com.easyhooon.dari.inspector.protocol.ListOutput
import com.easyhooon.dari.inspector.protocol.ObserveEvent
import com.easyhooon.dari.inspector.protocol.ServerHello
import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.inspector.protocol.WireMessageSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.Closeable
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class InspectorException(val error: InspectorError) : IOException("${error.code}: ${error.message}")

/** One session with a Dari app. Requests are matched to results by request id. */
class InspectorConnection private constructor(
    private val socket: Socket,
    val app: AppInfo,
    private val onClose: () -> Unit,
) : Closeable {
    private val reader = FrameReader(socket.getInputStream())
    private val writer = FrameWriter(socket.getOutputStream())
    private val pending = ConcurrentHashMap<String, CompletableDeferred<InspectorFrame.Result>>()
    private val streams = ConcurrentHashMap<String, Channel<ObserveEvent>>()
    private val nextId = AtomicLong()
    private val closed = AtomicBoolean()

    /** Completes when the app or the cable goes away. */
    val disconnected = CompletableDeferred<Unit>()

    init {
        thread(name = "dari-inspector-reader", isDaemon = true) { readLoop() }
    }

    /** Must be called before [list] so no event between the two is missed; merge results by id. */
    suspend fun observe(): ReceiveChannel<ObserveEvent> {
        val requestId = nextId.incrementAndGet().toString()
        val events = Channel<ObserveEvent>(Channel.UNLIMITED)
        streams[requestId] = events
        send(InspectorFrame.Invoke(requestId, InspectorOperations.OBSERVE))
        return events
    }

    suspend fun list(limit: Int = ListInput.DEFAULT_LIST_LIMIT): List<WireMessageSummary> =
        call(InspectorOperations.LIST, ListInput.serializer(), ListInput(limit), ListOutput.serializer()).messages

    suspend fun detail(id: Long): WireMessageDetail =
        call(InspectorOperations.DETAIL, DetailInput.serializer(), DetailInput(id), WireMessageDetail.serializer())

    suspend fun clear() {
        val requestId = nextId.incrementAndGet().toString()
        await(requestId, InspectorFrame.Invoke(requestId, InspectorOperations.CLEAR))
    }

    /** Masks what the app sends on this connection. Returns false when its dari predates 2.2.0, which always masks. */
    suspend fun setMasking(enabled: Boolean): Boolean = try {
        call(InspectorOperations.SETTINGS_UPDATE, InspectorSettings.serializer(), InspectorSettings(enabled), InspectorSettings.serializer())
        true
    } catch (e: InspectorException) {
        if (e.error.code != InspectorErrorCode.OPERATION_NOT_FOUND) throw e
        false
    }

    // Idempotent: the connect job and InspectorController.disconnect() may both close it.
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        onClose()
    }

    private suspend fun <I, O> call(op: String, inputSerializer: KSerializer<I>, input: I, outputSerializer: KSerializer<O>): O {
        val requestId = nextId.incrementAndGet().toString()
        val inputJson = InspectorProtocol.json.encodeToJsonElement(inputSerializer, input) as JsonObject
        val result = await(requestId, InspectorFrame.Invoke(requestId, op, inputJson))
        return InspectorProtocol.json.decodeFromJsonElement(outputSerializer, result.output ?: JsonObject(emptyMap()))
    }

    private suspend fun await(requestId: String, invoke: InspectorFrame.Invoke): InspectorFrame.Result {
        val deferred = CompletableDeferred<InspectorFrame.Result>()
        pending[requestId] = deferred
        try {
            send(invoke)
            val result = withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
            result.error?.let { throw InspectorException(it) }
            return result
        } finally {
            pending.remove(requestId)
        }
    }

    private suspend fun send(frame: InspectorFrame) = withContext(Dispatchers.IO) {
        writer.write(InspectorFrame.serializer(), frame)
    }

    private fun readLoop() {
        try {
            while (true) {
                val frame = reader.read(InspectorFrame.serializer()) ?: break
                if (frame is InspectorFrame.Result) dispatch(frame)
            }
        } catch (_: IOException) {
            // Treated as a disconnect below.
        } finally {
            val error = IOException("Disconnected from ${app.applicationId}")
            pending.values.forEach { it.completeExceptionally(error) }
            streams.values.forEach { it.close(error) }
            disconnected.complete(Unit)
        }
    }

    private fun dispatch(result: InspectorFrame.Result) {
        val stream = streams[result.requestId]
        if (stream == null) {
            pending[result.requestId]?.complete(result)
            return
        }
        val error = result.error
        result.output?.takeIf { error == null }?.let { stream.trySend(decodeEvent(it)) }
        if (error != null || result.isFinal) {
            streams.remove(result.requestId)
            stream.close(error?.let(::InspectorException))
        }
    }

    private fun decodeEvent(element: JsonElement): ObserveEvent =
        InspectorProtocol.json.decodeFromJsonElement(ObserveEvent.serializer(), element)

    companion object {
        /** First library release with the desktop socket; 1.x apps never answer. */
        const val MIN_DARI_VERSION = "2.0.0"
        private const val HANDSHAKE_TIMEOUT_MS = 5_000
        private const val REQUEST_TIMEOUT_MS = 10_000L

        /** Blocking: forwards the port, connects and completes the handshake. */
        fun open(adb: Adb, serial: String, applicationId: String, clientVersion: String): InspectorConnection {
            val port = adb.forward(serial, applicationId)
            val removeForward = { adb.removeForward(serial, port) }
            return try {
                handshake(Socket("127.0.0.1", port), applicationId, clientVersion, onClose = removeForward)
            } catch (e: IOException) {
                removeForward()
                throw e
            }
        }

        /** Blocking: completes the handshake on [socket]. [onClose] runs once, when the connection closes. */
        internal fun handshake(socket: Socket, applicationId: String, clientVersion: String, onClose: () -> Unit): InspectorConnection {
            try {
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                FrameWriter(socket.getOutputStream()).write(
                    ClientHello.serializer(),
                    ClientHello(InspectorProtocol.VERSION, "dari-desktop", clientVersion),
                )
                // adb accepts locally even when nothing listens on the device, then closes: no app.
                val hello = try {
                    FrameReader(socket.getInputStream()).read(ServerHello.serializer())
                } catch (_: SocketTimeoutException) {
                    // Android freezes cached apps: the socket accepts but nothing answers.
                    throw IOException("app is in the background")
                } ?: throw IOException("$applicationId isn't running Dari $MIN_DARI_VERSION+ on this device")
                if (!hello.accepted) throw IOException("App rejected the connection: ${hello.rejection}")
                socket.soTimeout = 0
                // An IOException keeps the cleanup below; requireNotNull would leak the socket and forward.
                val app = hello.app ?: throw IOException("Handshake is missing app info")
                return InspectorConnection(socket, app, onClose)
            } catch (e: IOException) {
                runCatching { socket.close() }
                throw e
            }
        }
    }
}
