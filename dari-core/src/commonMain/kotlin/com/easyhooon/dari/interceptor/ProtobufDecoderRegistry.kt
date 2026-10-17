package com.easyhooon.dari.interceptor

import com.easyhooon.dari.MessageDirection

@DslMarker
annotation class ProtobufDecoderDsl

/**
 * Builds a [ProtobufPayloadDecoder] from per-handler registrations instead of a central `when` block.
 * Unregistered contexts return `null` (decoder unavailable); duplicate registrations fail fast.
 */
fun protobufDecoder(block: ProtobufDecoderRegistry.() -> Unit): ProtobufPayloadDecoder =
    ProtobufDecoderRegistry().apply(block).build()

@ProtobufDecoderDsl
class ProtobufDecoderRegistry internal constructor() {
    private val decoders = mutableMapOf<ProtobufDecodeContext, (ByteArray) -> String?>()

    /** Registers decoders for a request sent from Web to App and the App's response. */
    fun webToApp(handlerName: String, block: ProtobufHandlerDecoders.() -> Unit) =
        handler(handlerName, MessageDirection.WEB_TO_APP, block)

    /** Registers decoders for a request sent from App to Web and the Web's response. */
    fun appToWeb(handlerName: String, block: ProtobufHandlerDecoders.() -> Unit) =
        handler(handlerName, MessageDirection.APP_TO_WEB, block)

    private fun handler(
        handlerName: String,
        direction: MessageDirection,
        block: ProtobufHandlerDecoders.() -> Unit,
    ) {
        ProtobufHandlerDecoders { part, decode ->
            val context = ProtobufDecodeContext(handlerName, direction, part)
            require(context !in decoders) {
                "Duplicate protobuf decoder for $handlerName ($direction, $part)"
            }
            decoders[context] = decode
        }.apply(block)
    }

    internal fun build(): ProtobufPayloadDecoder {
        val snapshot = decoders.toMap()
        return ProtobufPayloadDecoder { payload, context -> snapshot[context]?.invoke(payload) }
    }
}

@ProtobufDecoderDsl
class ProtobufHandlerDecoders internal constructor(
    private val register: (PayloadPart, (ByteArray) -> String?) -> Unit,
) {
    fun request(decode: (ByteArray) -> String?) = register(PayloadPart.REQUEST, decode)

    fun response(decode: (ByteArray) -> String?) = register(PayloadPart.RESPONSE, decode)
}
