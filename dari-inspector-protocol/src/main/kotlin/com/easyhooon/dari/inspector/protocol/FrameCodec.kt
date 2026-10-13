package com.easyhooon.dari.inspector.protocol

import kotlinx.serialization.KSerializer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Length-prefixed framing: 4-byte big-endian length, then UTF-8 JSON. Not thread-safe per stream. */
class FrameReader(input: InputStream) {
    private val input = DataInputStream(input)

    /** Returns null on a clean end of stream before a new frame starts. */
    fun readText(): String? {
        val length = try {
            input.readInt()
        } catch (_: EOFException) {
            return null
        }
        if (length < 0 || length > InspectorProtocol.MAX_FRAME_BYTES) {
            throw IOException("Invalid frame length: $length")
        }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return bytes.decodeToString()
    }

    fun <T> read(serializer: KSerializer<T>): T? =
        readText()?.let { InspectorProtocol.json.decodeFromString(serializer, it) }
}

class FrameWriter(output: OutputStream) {
    private val output = DataOutputStream(output)

    @Synchronized
    fun writeText(text: String) {
        val bytes = text.encodeToByteArray()
        if (bytes.size > InspectorProtocol.MAX_FRAME_BYTES) {
            throw IOException("Frame too large: ${bytes.size} bytes")
        }
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    fun <T> write(serializer: KSerializer<T>, value: T) {
        writeText(InspectorProtocol.json.encodeToString(serializer, value))
    }
}
