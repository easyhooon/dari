package com.easyhooon.dari.interceptor

import com.easyhooon.dari.Dari
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus

/**
 * Default implementation of [DariInterceptor].
 * Stores intercepted messages in [Dari]'s repository and posts notifications.
 *
 * When requestId is null, the entry is treated as a standalone (fire-and-forget) message
 * that doesn't require request-response pairing.
 */
class DefaultDariInterceptor(
    override val tag: String? = null,
    private val protobufDecoder: ProtobufPayloadDecoder? = null,
) : ProtobufDariInterceptor {

    /** Preserves the original constructor for binary compatibility. */
    constructor(tag: String?) : this(tag, null)

    private val maxContentLength: Int
        get() = Dari.config.maxContentLength

    override fun onWebToAppRequest(handlerName: String, requestId: String?, requestData: String?, fireAndForget: Boolean?) {
        val (truncatedData, wasTruncated) = MessageEntry.truncateIfNeeded(requestData, maxContentLength)
        val resolvedAsSuccess = fireAndForget ?: Dari.config.fireAndForget
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.WEB_TO_APP,
            tag = tag,
            requestData = truncatedData,
            requestDataTruncated = wasTruncated,
            status = if (resolvedAsSuccess) MessageStatus.SUCCESS else MessageStatus.IN_PROGRESS,
        )
        Dari.repository.addEntry(entry)
        Dari.postMessageNotification(handlerName, MessageDirection.WEB_TO_APP, tag)
    }

    override fun onWebToAppResponse(
        handlerName: String,
        requestId: String?,
        responseData: String?,
        isSuccess: Boolean,
    ) {
        // Skip request-response matching when requestId is null (fire-and-forget message)
        if (requestId == null) return

        val (truncatedData, wasTruncated) = MessageEntry.truncateIfNeeded(responseData, maxContentLength)
        Dari.repository.updateEntry(requestId = requestId, tag = tag) { entry ->
            entry.copy(
                responseData = truncatedData,
                responseDataTruncated = wasTruncated,
                status = if (isSuccess) MessageStatus.SUCCESS else MessageStatus.ERROR,
                responseTimestamp = System.currentTimeMillis(),
            )
        }
    }

    override fun onAppToWebRequest(handlerName: String, requestId: String?, data: String?, fireAndForget: Boolean?) {
        val (truncatedData, wasTruncated) = MessageEntry.truncateIfNeeded(data, maxContentLength)
        val resolvedAsSuccess = fireAndForget ?: Dari.config.fireAndForget
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.APP_TO_WEB,
            tag = tag,
            requestData = truncatedData,
            requestDataTruncated = wasTruncated,
            status = if (resolvedAsSuccess) MessageStatus.SUCCESS else MessageStatus.IN_PROGRESS,
        )
        Dari.repository.addEntry(entry)
        Dari.postMessageNotification(handlerName, MessageDirection.APP_TO_WEB, tag)
    }

    override fun onAppToWebResponse(requestId: String?, isSuccess: Boolean, responseData: String?) {
        // Skip request-response matching when requestId is null (fire-and-forget message)
        if (requestId == null) return

        val (truncatedData, wasTruncated) = MessageEntry.truncateIfNeeded(responseData, maxContentLength)
        Dari.repository.updateEntry(requestId = requestId, tag = tag) { entry ->
            entry.copy(
                responseData = truncatedData,
                responseDataTruncated = wasTruncated,
                status = if (isSuccess) MessageStatus.SUCCESS else MessageStatus.ERROR,
                responseTimestamp = System.currentTimeMillis(),
            )
        }
    }

    override fun onWebToAppProtobufRequest(
        handlerName: String,
        requestId: String?,
        requestData: ByteArray,
        fireAndForget: Boolean?,
    ) {
        val rendered = renderProtobuf(
            payload = requestData,
            handlerName = handlerName,
            direction = MessageDirection.WEB_TO_APP,
            part = PayloadPart.REQUEST,
        )
        val resolvedAsSuccess = fireAndForget ?: Dari.config.fireAndForget
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.WEB_TO_APP,
            tag = tag,
            requestData = rendered.data,
            requestDataTruncated = rendered.wasTruncated,
            requestPayloadMetadata = rendered.metadata,
            status = if (resolvedAsSuccess) MessageStatus.SUCCESS else MessageStatus.IN_PROGRESS,
        )
        Dari.repository.addEntry(entry)
        Dari.postMessageNotification(handlerName, MessageDirection.WEB_TO_APP, tag)
    }

    override fun onWebToAppProtobufResponse(
        handlerName: String,
        requestId: String?,
        responseData: ByteArray,
        isSuccess: Boolean,
    ) {
        if (requestId == null) return

        val rendered = renderProtobuf(
            payload = responseData,
            handlerName = handlerName,
            direction = MessageDirection.WEB_TO_APP,
            part = PayloadPart.RESPONSE,
        )
        Dari.repository.updateEntry(requestId = requestId, tag = tag) { entry ->
            entry.copy(
                responseData = rendered.data,
                responseDataTruncated = rendered.wasTruncated,
                responsePayloadMetadata = rendered.metadata,
                status = if (isSuccess) MessageStatus.SUCCESS else MessageStatus.ERROR,
                responseTimestamp = System.currentTimeMillis(),
            )
        }
    }

    override fun onAppToWebProtobufRequest(
        handlerName: String,
        requestId: String?,
        requestData: ByteArray,
        fireAndForget: Boolean?,
    ) {
        val rendered = renderProtobuf(
            payload = requestData,
            handlerName = handlerName,
            direction = MessageDirection.APP_TO_WEB,
            part = PayloadPart.REQUEST,
        )
        val resolvedAsSuccess = fireAndForget ?: Dari.config.fireAndForget
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.APP_TO_WEB,
            tag = tag,
            requestData = rendered.data,
            requestDataTruncated = rendered.wasTruncated,
            requestPayloadMetadata = rendered.metadata,
            status = if (resolvedAsSuccess) MessageStatus.SUCCESS else MessageStatus.IN_PROGRESS,
        )
        Dari.repository.addEntry(entry)
        Dari.postMessageNotification(handlerName, MessageDirection.APP_TO_WEB, tag)
    }

    override fun onAppToWebProtobufResponse(
        requestId: String?,
        isSuccess: Boolean,
        responseData: ByteArray,
    ) {
        if (requestId == null) return

        Dari.repository.updateEntry(requestId = requestId, tag = tag) { entry ->
            val rendered = renderProtobuf(
                payload = responseData,
                handlerName = entry.handlerName,
                direction = MessageDirection.APP_TO_WEB,
                part = PayloadPart.RESPONSE,
            )
            entry.copy(
                responseData = rendered.data,
                responseDataTruncated = rendered.wasTruncated,
                responsePayloadMetadata = rendered.metadata,
                status = if (isSuccess) MessageStatus.SUCCESS else MessageStatus.ERROR,
                responseTimestamp = System.currentTimeMillis(),
            )
        }
    }

    private fun renderProtobuf(
        payload: ByteArray,
        handlerName: String,
        direction: MessageDirection,
        part: PayloadPart,
    ): RenderedProtobufPayload {
        return ProtobufPayloadRenderer.render(
            payload = payload,
            context = ProtobufDecodeContext(handlerName, direction, part),
            decoder = protobufDecoder,
            maxContentLength = maxContentLength,
        )
    }
}
