package com.easyhooon.dari

import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.interceptor.ProtobufDariInterceptor
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

    @Test
    fun `display name overload remains compatible with existing interceptor implementations`() {
        val interceptor: DariInterceptor = RecordingInterceptor()

        interceptor.onWebToAppRequest(
            handlerName = "N:4-1",
            displayName = "오늘의 건강목표",
            requestId = "request-id",
            requestData = "payload",
        )

        assertEquals("N:4-1", (interceptor as RecordingInterceptor).handlerName)
    }

    @Test
    fun `protobuf display name overloads remain compatible with existing implementations`() {
        val interceptor: ProtobufDariInterceptor = RecordingProtobufInterceptor()

        interceptor.onWebToAppProtobufRequest("N:4-1", "오늘의 건강목표", "web-request", byteArrayOf(1))
        interceptor.onAppToWebProtobufRequest("N:4-2", "이번 주 건강목표", "app-request", byteArrayOf(2))

        assertEquals(listOf("N:4-1", "N:4-2"), (interceptor as RecordingProtobufInterceptor).handlerNames)
    }

    private class RecordingInterceptor : DariInterceptor {
        var handlerName: String? = null

        override fun onWebToAppRequest(
            handlerName: String,
            requestId: String?,
            requestData: String?,
            fireAndForget: Boolean?,
        ) {
            this.handlerName = handlerName
        }

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

    private class RecordingProtobufInterceptor : ProtobufDariInterceptor {
        val handlerNames = mutableListOf<String>()

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
        ) = Unit

        override fun onAppToWebResponse(requestId: String?, isSuccess: Boolean, responseData: String?) = Unit

        override fun onWebToAppProtobufRequest(
            handlerName: String,
            requestId: String?,
            requestData: ByteArray,
            fireAndForget: Boolean?,
        ) {
            handlerNames += handlerName
        }

        override fun onWebToAppProtobufResponse(
            handlerName: String,
            requestId: String?,
            responseData: ByteArray,
            isSuccess: Boolean,
        ) = Unit

        override fun onAppToWebProtobufRequest(
            handlerName: String,
            requestId: String?,
            requestData: ByteArray,
            fireAndForget: Boolean?,
        ) {
            handlerNames += handlerName
        }

        override fun onAppToWebProtobufResponse(
            requestId: String?,
            isSuccess: Boolean,
            responseData: ByteArray,
        ) = Unit
    }
}
