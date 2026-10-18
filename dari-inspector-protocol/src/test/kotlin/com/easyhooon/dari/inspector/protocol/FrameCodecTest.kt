package com.easyhooon.dari.inspector.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FrameCodecTest {

    @Test
    fun `frames round trip through the codec`() {
        val buffer = ByteArrayOutputStream()
        val writer = FrameWriter(buffer)
        val hello = ClientHello(InspectorProtocol.VERSION, "dari-desktop", "1.0.0")
        val invoke = InspectorFrame.Invoke(
            requestId = "1",
            op = InspectorOperations.LIST,
            input = buildJsonObject { put("limit", 10) },
        )
        writer.write(ClientHello.serializer(), hello)
        writer.write(InspectorFrame.serializer(), invoke)

        val reader = FrameReader(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(hello, reader.read(ClientHello.serializer()))
        assertEquals(invoke, reader.read(InspectorFrame.serializer()))
        assertNull(reader.read(InspectorFrame.serializer()))
    }

    @Test
    fun `app info environment is optional on the wire`() {
        val json = InspectorProtocol.json
        val old = """{"applicationId":"a","appName":"A","appVersion":"1","dariVersion":"2.2.1","deviceModel":"P","sdkInt":36}"""
        assertNull(json.decodeFromString(AppInfo.serializer(), old).environment)

        val labeled = json.decodeFromString(AppInfo.serializer(), old).copy(environment = linkedMapOf("region" to "JP"))
        assertEquals(labeled, json.decodeFromString(AppInfo.serializer(), json.encodeToString(AppInfo.serializer(), labeled)))
    }

    @Test
    fun `frame type is encoded as a discriminator`() {
        val text = InspectorProtocol.json.encodeToString(
            InspectorFrame.serializer(),
            InspectorFrame.Result(requestId = "7", output = JsonPrimitive(1), isFinal = false),
        )
        assertEquals("""{"type":"result","requestId":"7","output":1,"isFinal":false}""", text)
    }

    @Test
    fun `observe events decode by type`() {
        val event = InspectorProtocol.json.decodeFromString(
            ObserveEvent.serializer(),
            """{"type":"cleared"}""",
        )
        assertEquals(ObserveEvent.Cleared, event)
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val hello = InspectorProtocol.json.decodeFromString(
            ClientHello.serializer(),
            """{"protocolVersion":1,"clientName":"x","clientVersion":"y","future":true}""",
        )
        assertEquals("x", hello.clientName)
    }

    @Test
    fun `oversized frame length is rejected`() {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).writeInt(InspectorProtocol.MAX_FRAME_BYTES + 1)

        assertFailsWith<IOException> {
            FrameReader(ByteArrayInputStream(buffer.toByteArray())).readText()
        }
    }
}
