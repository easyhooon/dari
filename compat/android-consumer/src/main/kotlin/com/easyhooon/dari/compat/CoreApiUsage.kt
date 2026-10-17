package com.easyhooon.dari.compat

import com.easyhooon.dari.DariConfig
import com.easyhooon.dari.DariPayloadRedactor
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.interceptor.ProtobufPayloadDecoder
import com.easyhooon.dari.interceptor.protobufDecoder

// Touches the dari-core API an Android app relies on, compiled against the published artifact.
internal object CoreApiUsage {
    val decoder: ProtobufPayloadDecoder = protobufDecoder {
        webToApp("getUser") {
            request { it.size.toString() }
            response { null }
        }
    }

    val redactor = DariPayloadRedactor { entry -> entry.copy(requestData = null) }

    fun entry(): MessageEntry = MessageEntry(
        handlerName = "getUser",
        direction = MessageDirection.WEB_TO_APP,
        status = MessageStatus.SUCCESS,
        requestTimestamp = 0L,
        responseTimestamp = 10L,
    )

    fun config(): DariConfig = DariConfig()

    fun interceptor(): Class<DariInterceptor> = DariInterceptor::class.java
}
