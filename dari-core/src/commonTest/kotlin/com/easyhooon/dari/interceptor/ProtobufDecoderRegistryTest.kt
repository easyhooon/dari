package com.easyhooon.dari.interceptor

import com.easyhooon.dari.MessageDirection.APP_TO_WEB
import com.easyhooon.dari.MessageDirection.WEB_TO_APP
import com.easyhooon.dari.interceptor.PayloadPart.REQUEST
import com.easyhooon.dari.interceptor.PayloadPart.RESPONSE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProtobufDecoderRegistryTest {
    private val payload = byteArrayOf(1, 2, 3)

    private val decoder = protobufDecoder {
        webToApp("createOrder") {
            request { "web request ${it.size}" }
            response { "app response" }
        }
        appToWeb("createOrder") {
            request { "app request" }
        }
    }

    @Test
    fun `routes by handler direction and part`() {
        assertEquals("web request 3", decoder.decode(payload, ProtobufDecodeContext("createOrder", WEB_TO_APP, REQUEST)))
        assertEquals("app response", decoder.decode(payload, ProtobufDecodeContext("createOrder", WEB_TO_APP, RESPONSE)))
        assertEquals("app request", decoder.decode(payload, ProtobufDecodeContext("createOrder", APP_TO_WEB, REQUEST)))
    }

    @Test
    fun `unregistered contexts return null`() {
        assertNull(decoder.decode(payload, ProtobufDecodeContext("createOrder", APP_TO_WEB, RESPONSE)))
        assertNull(decoder.decode(payload, ProtobufDecodeContext("unknown", WEB_TO_APP, REQUEST)))
    }

    @Test
    fun `duplicate registrations fail fast`() {
        assertFailsWith<IllegalArgumentException> {
            protobufDecoder {
                webToApp("createOrder") { request { "a" } }
                webToApp("createOrder") { request { "b" } }
            }
        }
    }

    @Test
    fun `decoder exceptions propagate to the renderer`() {
        val failing = protobufDecoder {
            webToApp("createOrder") { request { error("bad bytes") } }
        }

        assertFailsWith<IllegalStateException> {
            failing.decode(payload, ProtobufDecodeContext("createOrder", WEB_TO_APP, REQUEST))
        }
    }
}
