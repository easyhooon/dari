package com.easyhooon.dari

import com.easyhooon.dari.interceptor.DariInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicApiSmokeTest {
    @Test
    fun `legacy positional message constructor remains available`() {
        val entry = MessageEntry(
            1L,
            "request-id",
            "handler",
            MessageDirection.WEB_TO_APP,
            "request",
            "response",
            MessageStatus.SUCCESS,
            100L,
            120L,
        )

        assertEquals(20L, entry.durationMs)
    }

    @Test
    fun `interceptor contract remains implementable from common code`() {
        val interceptor: DariInterceptor = RecordingInterceptor()

        interceptor.onAppToWebRequest("handler", "request-id", "payload")

        assertEquals("handler", (interceptor as RecordingInterceptor).handlerName)
    }

    private class RecordingInterceptor : DariInterceptor {
        var handlerName: String? = null

        override fun onWebToAppRequest(
            handlerName: String,
            requestId: String?,
            requestData: String?,
            fireAndForget: Boolean?,
        ) = Unit

        override fun onWebToAppResponse(
            handlerName: String,
            requestId: String?,
            responseData: String?,
            isSuccess: Boolean,
        ) = Unit

        override fun onAppToWebRequest(
            handlerName: String,
            requestId: String?,
            data: String?,
            fireAndForget: Boolean?,
        ) {
            this.handlerName = handlerName
        }

        override fun onAppToWebResponse(requestId: String?, isSuccess: Boolean, responseData: String?) = Unit
    }
}
