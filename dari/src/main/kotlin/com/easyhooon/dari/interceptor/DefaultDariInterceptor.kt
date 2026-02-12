package com.easyhooon.dari.interceptor

import com.easyhooon.dari.Dari
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus

/**
 * Default implementation of [DariInterceptor].
 * Stores intercepted messages in [Dari]'s repository and posts notifications.
 */
class DefaultDariInterceptor : DariInterceptor {

    override fun onWebToAppRequest(handlerName: String, requestId: String, requestData: String?) {
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.WEB_TO_APP,
            requestData = requestData,
        )
        Dari.repository.addEntry(entry)
        Dari.postMessageNotification(handlerName, MessageDirection.WEB_TO_APP)
    }

    override fun onWebToAppResponse(
        handlerName: String,
        requestId: String,
        responseData: String?,
        isSuccess: Boolean,
    ) {
        Dari.repository.updateEntry(requestId) { entry ->
            entry.copy(
                responseData = responseData,
                status = if (isSuccess) MessageStatus.SUCCESS else MessageStatus.ERROR,
                responseTimestamp = System.currentTimeMillis(),
            )
        }
    }

    override fun onAppToWebMessage(handlerName: String, requestId: String, data: String?) {
        val entry = MessageEntry(
            requestId = requestId,
            handlerName = handlerName,
            direction = MessageDirection.APP_TO_WEB,
            requestData = data,
        )
        Dari.repository.addEntry(entry)
        Dari.postMessageNotification(handlerName, MessageDirection.APP_TO_WEB)
    }

    override fun onAppToWebResponse(requestId: String, isSuccess: Boolean, responseData: String?) {
        Dari.repository.updateEntry(requestId) { entry ->
            entry.copy(
                responseData = responseData,
                status = if (isSuccess) MessageStatus.SUCCESS else MessageStatus.ERROR,
                responseTimestamp = System.currentTimeMillis(),
            )
        }
    }
}