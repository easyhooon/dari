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
) : DariInterceptor {

    override fun onWebToAppRequest(handlerName: String, requestId: String?, requestData: String?) {
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.WEB_TO_APP,
            tag = tag,
            requestData = requestData,
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

        Dari.repository.updateEntry(requestId = requestId, tag = tag) { entry ->
            entry.copy(
                responseData = responseData,
                status = if (isSuccess) MessageStatus.SUCCESS else MessageStatus.ERROR,
                responseTimestamp = System.currentTimeMillis(),
            )
        }
    }

    override fun onAppToWebMessage(handlerName: String, requestId: String?, data: String?) {
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.APP_TO_WEB,
            tag = tag,
            requestData = data,
        )
        Dari.repository.addEntry(entry)
        Dari.postMessageNotification(handlerName, MessageDirection.APP_TO_WEB, tag)
    }

    override fun onAppToWebResponse(requestId: String?, isSuccess: Boolean, responseData: String?) {
        // Skip request-response matching when requestId is null (fire-and-forget message)
        if (requestId == null) return

        Dari.repository.updateEntry(requestId = requestId, tag = tag) { entry ->
            entry.copy(
                responseData = responseData,
                status = if (isSuccess) MessageStatus.SUCCESS else MessageStatus.ERROR,
                responseTimestamp = System.currentTimeMillis(),
            )
        }
    }
}