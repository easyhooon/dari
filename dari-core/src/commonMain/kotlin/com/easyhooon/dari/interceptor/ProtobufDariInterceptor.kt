package com.easyhooon.dari.interceptor

import com.easyhooon.dari.MessageDirection

/** Identifies which side of a bridge exchange a protobuf payload belongs to. */
enum class PayloadPart {
    REQUEST,
    RESPONSE,
}

data class ProtobufDecodeContext(
    val handlerName: String,
    val direction: MessageDirection,
    val part: PayloadPart,
)

/**
 * Converts protobuf bytes into display text without coupling Dari to a protobuf runtime.
 * Return `null` when the decoder does not recognize the supplied context.
 * Decoding runs synchronously on the interceptor caller's thread.
 */
fun interface ProtobufPayloadDecoder {
    fun decode(payload: ByteArray, context: ProtobufDecodeContext): String?
}

/** Optional protobuf extension of the existing string-based [DariInterceptor]. */
interface ProtobufDariInterceptor : DariInterceptor {
    fun onWebToAppProtobufRequest(
        handlerName: String,
        requestId: String?,
        requestData: ByteArray,
        fireAndForget: Boolean? = null,
    )

    fun onWebToAppProtobufRequest(
        handlerName: String,
        displayName: String?,
        requestId: String?,
        requestData: ByteArray,
        fireAndForget: Boolean? = null,
    ) {
        onWebToAppProtobufRequest(handlerName, requestId, requestData, fireAndForget)
    }

    fun onWebToAppProtobufResponse(
        handlerName: String,
        requestId: String?,
        responseData: ByteArray,
        isSuccess: Boolean,
    )

    fun onAppToWebProtobufRequest(
        handlerName: String,
        requestId: String?,
        requestData: ByteArray,
        fireAndForget: Boolean? = null,
    )

    fun onAppToWebProtobufRequest(
        handlerName: String,
        displayName: String?,
        requestId: String?,
        requestData: ByteArray,
        fireAndForget: Boolean? = null,
    ) {
        onAppToWebProtobufRequest(handlerName, requestId, requestData, fireAndForget)
    }

    fun onAppToWebProtobufResponse(
        requestId: String?,
        isSuccess: Boolean,
        responseData: ByteArray,
    )
}
