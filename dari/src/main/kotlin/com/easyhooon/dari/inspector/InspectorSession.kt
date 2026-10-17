package com.easyhooon.dari.inspector

import com.easyhooon.dari.DariPayloadRedactor
import com.easyhooon.dari.data.InspectorEntry
import com.easyhooon.dari.data.RepositoryEvent
import com.easyhooon.dari.inspector.protocol.AppInfo
import com.easyhooon.dari.inspector.protocol.ClientHello
import com.easyhooon.dari.inspector.protocol.DetailInput
import com.easyhooon.dari.inspector.protocol.FrameReader
import com.easyhooon.dari.inspector.protocol.FrameWriter
import com.easyhooon.dari.inspector.protocol.HandshakeRejection
import com.easyhooon.dari.inspector.protocol.InspectorError
import com.easyhooon.dari.inspector.protocol.InspectorErrorCode
import com.easyhooon.dari.inspector.protocol.InspectorFrame
import com.easyhooon.dari.inspector.protocol.InspectorOperations
import com.easyhooon.dari.inspector.protocol.InspectorProtocol
import com.easyhooon.dari.inspector.protocol.InspectorSettings
import com.easyhooon.dari.inspector.protocol.ListInput
import com.easyhooon.dari.inspector.protocol.ListOutput
import com.easyhooon.dari.inspector.protocol.SearchInput
import com.easyhooon.dari.inspector.protocol.SearchOutput
import com.easyhooon.dari.inspector.protocol.ObserveEvent
import com.easyhooon.dari.inspector.protocol.ServerHello
import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.masking.maskedIf
import com.easyhooon.dari.matchesQuery
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal interface InspectorDataSource {
    val events: Flow<RepositoryEvent>
    fun snapshot(): List<InspectorEntry>
    fun clear()
}

/** Serves one desktop connection. [run] blocks until the client disconnects. */
internal class InspectorSession(
    input: InputStream,
    output: OutputStream,
    private val appInfo: AppInfo,
    private val source: InspectorDataSource,
    private val redactor: DariPayloadRedactor,
    private val scope: CoroutineScope,
) {
    private val reader = FrameReader(input)
    private val writer = FrameWriter(output)
    private val streams = ConcurrentHashMap<String, Job>()

    // Set by the desktop for this connection; read by observe on another thread.
    @Volatile
    private var maskingEnabled = false

    fun run() {
        try {
            if (!handshake()) return
            while (true) {
                when (val frame = reader.read(InspectorFrame.serializer()) ?: break) {
                    is InspectorFrame.Invoke -> handle(frame)
                    is InspectorFrame.Cancel -> streams.remove(frame.requestId)?.cancel()
                    is InspectorFrame.Result -> Unit
                }
            }
        } finally {
            streams.values.forEach { it.cancel() }
            streams.clear()
        }
    }

    private fun handshake(): Boolean {
        val hello = reader.read(ClientHello.serializer()) ?: return false
        val accepted = hello.protocolVersion == InspectorProtocol.VERSION
        writer.write(
            ServerHello.serializer(),
            ServerHello(
                accepted = accepted,
                protocolVersion = InspectorProtocol.VERSION,
                rejection = if (accepted) null else HandshakeRejection.UNSUPPORTED_PROTOCOL_VERSION,
                app = appInfo,
            ),
        )
        return accepted
    }

    @Suppress("TooGenericExceptionCaught") // A failing redactor must not end the session.
    private fun handle(invoke: InspectorFrame.Invoke) {
        try {
            when (invoke.op) {
                InspectorOperations.LIST -> {
                    val input = decode(ListInput.serializer(), invoke.input)
                    val messages = source.snapshot()
                        .takeLast(input.limit.coerceAtLeast(0))
                        .map { it.redacted().let { item -> item.entry.toWireSummary(item.wireId) } }
                    reply(invoke.requestId, ListOutput.serializer(), ListOutput(messages))
                }
                InspectorOperations.DETAIL -> {
                    val input = decode(DetailInput.serializer(), invoke.input)
                    val item = source.snapshot().find { it.wireId == input.id }?.redacted()
                    if (item == null) {
                        fail(invoke.requestId, InspectorErrorCode.NOT_FOUND, "No message with id ${input.id}")
                    } else {
                        reply(invoke.requestId, WireMessageDetail.serializer(), item.entry.toWireDetail(item.wireId))
                    }
                }
                InspectorOperations.SEARCH -> {
                    val query = decode(SearchInput.serializer(), invoke.input).query.trim()
                    val ids = if (query.isEmpty()) {
                        emptyList()
                    } else {
                        source.snapshot().map { it.redacted() }.filter { it.entry.matchesQuery(query) }.map { it.wireId }
                    }
                    reply(invoke.requestId, SearchOutput.serializer(), SearchOutput(ids))
                }
                InspectorOperations.OBSERVE -> observe(invoke.requestId)
                InspectorOperations.CLEAR -> {
                    source.clear()
                    send(InspectorFrame.Result(invoke.requestId, output = JsonObject(emptyMap())))
                }
                InspectorOperations.SETTINGS_UPDATE -> {
                    maskingEnabled = decode(InspectorSettings.serializer(), invoke.input).maskingEnabled
                    reply(invoke.requestId, InspectorSettings.serializer(), InspectorSettings(maskingEnabled))
                }
                else -> fail(invoke.requestId, InspectorErrorCode.OPERATION_NOT_FOUND, "Unknown op ${invoke.op}")
            }
        } catch (e: SerializationException) {
            fail(invoke.requestId, InspectorErrorCode.INVALID_INPUT, e.message.orEmpty())
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            fail(invoke.requestId, InspectorErrorCode.PROVIDER_FAILED, e.message.orEmpty())
        }
    }

    // UNDISPATCHED subscribes before the next frame is read, so a following `list` can't miss events.
    @Suppress("TooGenericExceptionCaught")
    private fun observe(requestId: String) {
        streams[requestId] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                source.events.collect { event ->
                    val output = InspectorProtocol.json.encodeToJsonElement(
                        ObserveEvent.serializer(),
                        event.toObserveEvent(),
                    )
                    send(InspectorFrame.Result(requestId, output = output, isFinal = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Client went away; the read loop ends the session.
            } catch (e: Exception) {
                runCatching { fail(requestId, InspectorErrorCode.PROVIDER_FAILED, e.message.orEmpty()) }
            } finally {
                streams.remove(requestId)
            }
        }
    }

    private fun RepositoryEvent.toObserveEvent(): ObserveEvent = when (this) {
        is RepositoryEvent.Added -> item.redacted().let { ObserveEvent.Added(it.entry.toWireSummary(it.wireId)) }
        is RepositoryEvent.Updated -> item.redacted().let { ObserveEvent.Updated(it.entry.toWireSummary(it.wireId)) }
        RepositoryEvent.Cleared -> ObserveEvent.Cleared
    }

    private fun InspectorEntry.redacted(): InspectorEntry =
        copy(entry = redactor.redact(entry.maskedIf(maskingEnabled)))

    private fun <T> decode(serializer: KSerializer<T>, input: JsonElement): T =
        InspectorProtocol.json.decodeFromJsonElement(serializer, input)

    private fun <T> reply(requestId: String, serializer: KSerializer<T>, value: T) {
        send(InspectorFrame.Result(requestId, output = InspectorProtocol.json.encodeToJsonElement(serializer, value)))
    }

    private fun fail(requestId: String, code: InspectorErrorCode, message: String) {
        send(InspectorFrame.Result(requestId, error = InspectorError(code, message)))
    }

    private fun send(frame: InspectorFrame) {
        writer.write(InspectorFrame.serializer(), frame)
    }
}
