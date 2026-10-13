package com.easyhooon.dari.inspector.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Frames exchanged after the handshake, discriminated by `type`. */
@Serializable
sealed interface InspectorFrame {

    /** Desktop → app: call [op] (see [InspectorOperations]). */
    @Serializable
    @SerialName("invoke")
    data class Invoke(
        val requestId: String,
        val op: String,
        val input: JsonObject = JsonObject(emptyMap()),
    ) : InspectorFrame

    /** App → desktop: one result. Streams send `isFinal = false` until they end. */
    @Serializable
    @SerialName("result")
    data class Result(
        val requestId: String,
        val output: JsonElement? = null,
        val error: InspectorError? = null,
        val isFinal: Boolean = true,
    ) : InspectorFrame

    /** Desktop → app: stop a running stream. */
    @Serializable
    @SerialName("cancel")
    data class Cancel(val requestId: String) : InspectorFrame
}

@Serializable
data class InspectorError(
    val code: InspectorErrorCode,
    val message: String,
)

@Serializable
enum class InspectorErrorCode {
    INVALID_INPUT,
    OPERATION_NOT_FOUND,
    NOT_FOUND,
    PROVIDER_FAILED,
}
