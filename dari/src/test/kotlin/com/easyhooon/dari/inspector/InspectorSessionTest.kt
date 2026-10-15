package com.easyhooon.dari.inspector

import com.easyhooon.dari.DariPayloadRedactor
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.data.InspectorEntry
import com.easyhooon.dari.data.RepositoryEvent
import com.easyhooon.dari.inspector.protocol.AppInfo
import com.easyhooon.dari.inspector.protocol.ClientHello
import com.easyhooon.dari.inspector.protocol.FrameReader
import com.easyhooon.dari.inspector.protocol.FrameWriter
import com.easyhooon.dari.inspector.protocol.HandshakeRejection
import com.easyhooon.dari.inspector.protocol.InspectorErrorCode
import com.easyhooon.dari.inspector.protocol.InspectorFrame
import com.easyhooon.dari.inspector.protocol.InspectorOperations
import com.easyhooon.dari.inspector.protocol.InspectorProtocol
import com.easyhooon.dari.inspector.protocol.InspectorSettings
import com.easyhooon.dari.inspector.protocol.ListOutput
import com.easyhooon.dari.inspector.protocol.ObserveEvent
import com.easyhooon.dari.inspector.protocol.ServerHello
import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorSessionTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = MutableSharedFlow<RepositoryEvent>(extraBufferCapacity = 16)
    private val entries = mutableListOf(
        InspectorEntry(-1L, entry("getUser", requestData = """{"token":"secret"}""")),
        InspectorEntry(-2L, entry("openCamera")),
    )
    private var cleared = false

    private val source = object : InspectorDataSource {
        override val events = this@InspectorSessionTest.events
        override fun snapshot() = entries.toList()
        override fun clear() {
            cleared = true
        }
    }

    private val appInfo = AppInfo("com.example", "Example", "1.0", "1.8.0", "Pixel", 36)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `rejects a different protocol version`() {
        val client = connect()
        client.writer.write(ClientHello.serializer(), ClientHello(InspectorProtocol.VERSION + 1, "test", "0"))

        val hello = client.reader.read(ServerHello.serializer())!!
        assertFalse(hello.accepted)
        assertEquals(HandshakeRejection.UNSUPPORTED_PROTOCOL_VERSION, hello.rejection)
        assertNull(client.reader.readText())
    }

    @Test
    fun `list returns summaries limited to the newest entries`() {
        val client = connect().handshake()
        client.invoke("1", InspectorOperations.LIST, buildJsonObject { put("limit", 1) })

        val output = client.output(ListOutput.serializer())
        assertEquals(listOf(-2L), output.messages.map { it.id })
        assertEquals("openCamera", output.messages.single().handlerName)
    }

    @Test
    fun `detail applies the redactor`() {
        val redactor = DariPayloadRedactor { it.copy(requestData = "[redacted]") }
        val client = connect(redactor).handshake()
        client.invoke("1", InspectorOperations.DETAIL, buildJsonObject { put("id", -1L) })

        val detail = client.output(WireMessageDetail.serializer())
        assertEquals("[redacted]", detail.requestData)
        assertEquals(-1L, detail.summary.id)
    }

    @Test
    fun `detail for an unknown id reports not found`() {
        val client = connect().handshake()
        client.invoke("1", InspectorOperations.DETAIL, buildJsonObject { put("id", 99L) })

        assertEquals(InspectorErrorCode.NOT_FOUND, client.result().error?.code)
    }

    @Test
    fun `unknown op and bad input are reported as errors`() {
        val client = connect().handshake()
        client.invoke("1", "messages.unknown")
        assertEquals(InspectorErrorCode.OPERATION_NOT_FOUND, client.result().error?.code)

        client.invoke("2", InspectorOperations.DETAIL, buildJsonObject { put("id", "abc") })
        assertEquals(InspectorErrorCode.INVALID_INPUT, client.result().error?.code)
    }

    @Test
    fun `masking is off until the client turns it on for its own connection`() {
        val client = connect().handshake()
        client.invoke("1", InspectorOperations.DETAIL, buildJsonObject { put("id", -1L) })
        assertEquals("""{"token":"secret"}""", client.output(WireMessageDetail.serializer()).requestData)

        client.invoke("2", InspectorOperations.SETTINGS_UPDATE, buildJsonObject { put("maskingEnabled", true) })
        assertEquals(InspectorSettings(maskingEnabled = true), client.output(InspectorSettings.serializer()))
        client.invoke("3", InspectorOperations.DETAIL, buildJsonObject { put("id", -1L) })
        assertEquals("""{"token":"***"}""", client.output(WireMessageDetail.serializer()).requestData)

        val other = connect().handshake()
        other.invoke("4", InspectorOperations.DETAIL, buildJsonObject { put("id", -1L) })
        assertEquals("""{"token":"secret"}""", other.output(WireMessageDetail.serializer()).requestData)

        client.invoke("5", InspectorOperations.SETTINGS_UPDATE)
        assertEquals(InspectorErrorCode.INVALID_INPUT, client.result().error?.code)
    }

    @Test
    fun `observe streams repository events until cancelled`() {
        val client = connect().handshake()
        client.invoke("obs", InspectorOperations.OBSERVE)
        // The subscription is active before the next frame is read, so this list can't race it.
        client.invoke("list", InspectorOperations.LIST)
        assertEquals("list", client.result().requestId)

        val added = InspectorEntry(-3L, entry("share"))
        assertTrue(events.tryEmit(RepositoryEvent.Added(added)))
        val addedFrame = client.result()
        assertEquals("obs", addedFrame.requestId)
        assertFalse(addedFrame.isFinal)
        val event = InspectorProtocol.json.decodeFromJsonElement(ObserveEvent.serializer(), addedFrame.output!!)
        assertEquals(-3L, (event as ObserveEvent.Added).message.id)

        events.tryEmit(RepositoryEvent.Updated(added.copy(entry = added.entry.copy(status = MessageStatus.SUCCESS))))
        val updated = InspectorProtocol.json.decodeFromJsonElement(ObserveEvent.serializer(), client.result().output!!)
        assertEquals("SUCCESS", (updated as ObserveEvent.Updated).message.status)

        client.writer.write(InspectorFrame.serializer(), InspectorFrame.Cancel("obs"))
        client.invoke("clear", InspectorOperations.CLEAR)
        assertEquals("clear", client.result().requestId)
        assertTrue(cleared)
    }

    private fun connect(redactor: DariPayloadRedactor = DariPayloadRedactor { it }): TestClient {
        val toSession = PipedOutputStream()
        val sessionInput = PipedInputStream(toSession, PIPE_SIZE)
        val fromSession = PipedInputStream(PIPE_SIZE)
        val sessionOutput = PipedOutputStream(fromSession)
        thread(isDaemon = true) {
            sessionOutput.use {
                InspectorSession(sessionInput, it, appInfo, source, redactor, scope).run()
            }
        }
        return TestClient(FrameReader(fromSession), FrameWriter(toSession))
    }

    private class TestClient(val reader: FrameReader, val writer: FrameWriter) {
        fun handshake(): TestClient = apply {
            writer.write(ClientHello.serializer(), ClientHello(InspectorProtocol.VERSION, "test", "0"))
            assertTrue(reader.read(ServerHello.serializer())!!.accepted)
        }

        fun invoke(requestId: String, op: String, input: JsonObject = JsonObject(emptyMap())) {
            writer.write(InspectorFrame.serializer(), InspectorFrame.Invoke(requestId, op, input))
        }

        fun result(): InspectorFrame.Result = reader.read(InspectorFrame.serializer()) as InspectorFrame.Result

        fun <T> output(serializer: KSerializer<T>): T =
            InspectorProtocol.json.decodeFromJsonElement(serializer, result().output!!)
    }

    private companion object {
        const val PIPE_SIZE = 64 * 1024

        fun entry(handlerName: String, requestData: String? = null) = MessageEntry(
            handlerName = handlerName,
            direction = MessageDirection.WEB_TO_APP,
            requestData = requestData,
            requestTimestamp = 1_000L,
        )
    }
}
