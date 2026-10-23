package com.easyhooon.dari.auto

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.interceptor.ProtobufDariInterceptor
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DariHooksTest {

    private val created = mutableListOf<RecordingInterceptor>()
    private val calls get() = created.flatMap { it.calls }

    @Before
    fun setup() {
        DariHooks.reset()
        DariHooks.interceptorFactory = { tag -> RecordingInterceptor(tag).also(created::add) }
    }

    @After
    fun tearDown() = DariHooks.reset()

    @Test
    fun `request and response mapped by argument position pair under the bridge's JS name`() {
        val webView = WebView(ApplicationProvider.getApplicationContext())
        val bridge = Any()
        DariHooks.addJavascriptInterface(webView, bridge, "Android")
        DariHooks.onVisitedHistory(webView, "https://example.com/a")

        DariHooks.onMappedCall(REQUEST, bridge, arrayOf("getAppInfo", "req_1", """{"a":1}"""))
        DariHooks.onMappedCall(RESPONSE, Any(), arrayOf("getAppInfo", "req_1", false, """{"error":"x"}"""))

        assertEquals(
            listOf(
                "Android url https://example.com/a",
                "Android w2a-req getAppInfo null req_1 {\"a\":1} null",
                "Android w2a-res getAppInfo req_1 {\"error\":\"x\"} false",
            ),
            calls,
        )
    }

    @Test
    fun `fire-and-forget is true or deferred, never forced false`() {
        val spec = spec("WEB_TO_APP_REQUEST", """"handlerName":{"arg":0},"fireAndForget":{"equalsTo":[{"arg":0},"ping"]}""")
        DariHooks.onMappedCall(spec, null, arrayOf("ping"))
        DariHooks.onMappedCall(spec, null, arrayOf("other"))

        assertEquals(listOf("null w2a-req ping null null null true", "null w2a-req other null null null null"), calls)
    }

    @Test
    fun `json paths read one JSON string argument`() {
        val spec = spec(
            "WEB_TO_APP_REQUEST",
            """"handlerName":{"json":[{"arg":0},"action"]},"requestId":{"json":[{"arg":0},"meta.id"]},""" +
                """"data":{"json":[{"arg":0},"params"]},"displayName":{"const":"Hello"}""",
            tag = "Sample",
        )
        DariHooks.onMappedCall(spec, null, arrayOf("""{"action":"login","meta":{"id":7},"params":{"u":"x"}}"""))

        assertEquals(listOf("Sample w2a-req login Hello 7 {\"u\":\"x\"} null"), calls)
    }

    @Test
    fun `json paths read objects that print themselves as JSON`() {
        val spec = spec(
            "APP_TO_WEB_REQUEST",
            """"handlerName":{"json":[{"arg":0},"action"]},"requestId":{"json":[{"arg":0},"meta.id"]},""" +
                """"data":{"json":[{"arg":0},"params"]}""",
        )
        val kotlinx = buildJsonObject {
            put("action", "login")
            putJsonObject("meta") { put("id", 7) }
            putJsonObject("params") { put("u", "x") }
        }
        // Stands in for Gson's JsonObject and any other type whose toString() is JSON.
        val other = object {
            override fun toString() = """{"action":"logout","meta":{"id":"8"}}"""
        }

        DariHooks.onMappedCall(spec, null, arrayOf(kotlinx))
        DariHooks.onMappedCall(spec, null, arrayOf(other))

        assertEquals(listOf("null a2w-req login 7 {\"u\":\"x\"} null", "null a2w-req logout 8 null null"), calls)
    }

    @Test
    fun `onlyIf splits one entry point into requests and responses`() {
        val webView = WebView(ApplicationProvider.getApplicationContext())
        DariHooks.addJavascriptInterface(webView, Any(), "Android")
        val request = spec(
            "APP_TO_WEB_REQUEST",
            """"handlerName":{"json":[{"arg":0},"action"]},"requestId":{"json":[{"arg":0},"id"]},""" +
                """"onlyIf":{"equalsTo":[{"json":[{"arg":0},"type"]},"request"]}""",
        )
        val response = spec(
            "APP_TO_WEB_RESPONSE",
            """"requestId":{"json":[{"arg":0},"id"]},"data":{"arg":0},""" +
                """"onlyIf":{"equalsTo":[{"json":[{"arg":0},"type"]},"response"]}""",
        )
        val outgoing = buildJsonObject {
            put("type", "request")
            put("action", "getPageState")
            put("id", "a1")
        }
        val answer = buildJsonObject {
            put("type", "response")
            put("id", "a1")
        }

        // One method carries both kinds, so every message reaches both mappings.
        listOf(outgoing, answer).forEach { message ->
            DariHooks.onMappedCall(request, Any(), arrayOf(message))
            DariHooks.onMappedCall(response, Any(), arrayOf(message))
        }

        assertEquals(1, created.size)
        assertEquals(
            listOf("Android a2w-req getPageState a1 null null", "Android a2w-res a1 true {\"type\":\"response\",\"id\":\"a1\"}"),
            calls,
        )
    }

    @Test
    fun `a filtered call creates no interceptor`() {
        val spec = spec("WEB_TO_APP_REQUEST", """"handlerName":{"arg":0},"onlyIf":{"isNull":{"arg":1}}""")
        DariHooks.onMappedCall(spec, null, arrayOf("ping", "not null"))

        assertEquals(0, created.size)
    }

    @Test
    fun `base64 data goes through the protobuf API`() {
        val spec = spec("WEB_TO_APP_REQUEST", """"handlerName":{"const":"proto"},"requestId":{"arg":0},"data":{"base64":{"arg":1}}""")
        DariHooks.onMappedCall(spec, null, arrayOf("r1", "AQID"))

        assertEquals(listOf("null w2a-proto-req proto r1"), calls)
        assertArrayEquals(byteArrayOf(1, 2, 3), created.single().lastBytes)
    }

    @Test
    fun `app to web request and response pair even when the response runs on another object`() {
        val webView = WebView(ApplicationProvider.getApplicationContext())
        DariHooks.addJavascriptInterface(webView, Any(), "Android")
        val request = spec("APP_TO_WEB_REQUEST", """"handlerName":{"arg":0},"requestId":{"arg":1},"data":{"arg":2}""")
        val response = spec("APP_TO_WEB_RESPONSE", """"requestId":{"arg":0},"success":{"arg":1},"data":{"arg":2}""")

        DariHooks.onMappedCall(request, Any(), arrayOf("getPageState", "app_1", null))
        DariHooks.onMappedCall(response, Any(), arrayOf("app_1", true, "{}"))

        assertEquals(listOf("Android a2w-req getPageState app_1 null null", "Android a2w-res app_1 true {}"), calls)
    }

    @Test
    fun `each WebView gets its own interceptor and page URL`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = Any()
        val second = Any()
        val firstView = WebView(context)
        val secondView = WebView(context)
        DariHooks.addJavascriptInterface(firstView, first, "Android")
        DariHooks.addJavascriptInterface(secondView, second, "Android")
        DariHooks.onVisitedHistory(secondView, "https://two")

        DariHooks.onMappedCall(REQUEST, first, arrayOf("a", "1", null))
        DariHooks.onMappedCall(REQUEST, second, arrayOf("b", "2", null))

        assertEquals(2, created.size)
        assertEquals(listOf("Android w2a-req a null 1 null null"), created[0].calls)
        assertEquals(listOf("Android url https://two", "Android w2a-req b null 2 null null"), created[1].calls)
    }

    @Test
    fun `a broken spec never throws into the bridge`() {
        DariHooks.onMappedCall("not json", null, arrayOf("x"))
        assertEquals(emptyList<String>(), calls)
    }

    private class RecordingInterceptor(override val tag: String?) : ProtobufDariInterceptor {
        val calls = mutableListOf<String>()
        var lastBytes: ByteArray? = null

        override fun onPageUrlChanged(url: String?) {
            calls += "$tag url $url"
        }

        override fun onWebToAppRequest(handlerName: String, requestId: String?, requestData: String?, fireAndForget: Boolean?) =
            onWebToAppRequest(handlerName, null, requestId, requestData, fireAndForget)

        override fun onWebToAppRequest(
            handlerName: String,
            displayName: String?,
            requestId: String?,
            requestData: String?,
            fireAndForget: Boolean?,
        ) {
            calls += "$tag w2a-req $handlerName $displayName $requestId $requestData $fireAndForget"
        }

        override fun onWebToAppResponse(handlerName: String, requestId: String?, responseData: String?, isSuccess: Boolean) {
            calls += "$tag w2a-res $handlerName $requestId $responseData $isSuccess"
        }

        override fun onAppToWebRequest(handlerName: String, requestId: String?, data: String?, fireAndForget: Boolean?) {
            calls += "$tag a2w-req $handlerName $requestId $data $fireAndForget"
        }

        override fun onAppToWebResponse(requestId: String?, isSuccess: Boolean, responseData: String?) {
            calls += "$tag a2w-res $requestId $isSuccess $responseData"
        }

        override fun onWebToAppProtobufRequest(handlerName: String, requestId: String?, requestData: ByteArray, fireAndForget: Boolean?) {
            calls += "$tag w2a-proto-req $handlerName $requestId"
            lastBytes = requestData
        }

        override fun onWebToAppProtobufResponse(handlerName: String, requestId: String?, responseData: ByteArray, isSuccess: Boolean) {
            calls += "$tag w2a-proto-res $handlerName $requestId $isSuccess"
        }

        override fun onAppToWebProtobufRequest(handlerName: String, requestId: String?, requestData: ByteArray, fireAndForget: Boolean?) {
            calls += "$tag a2w-proto-req $handlerName $requestId"
        }

        override fun onAppToWebProtobufResponse(requestId: String?, isSuccess: Boolean, responseData: ByteArray) {
            calls += "$tag a2w-proto-res $requestId $isSuccess"
        }
    }

    private companion object {
        fun spec(kind: String, fields: String, tag: String? = null): String =
            """{"kind":"$kind",${tag?.let { "\"tag\":\"$it\"," }.orEmpty()}"fields":{$fields}}"""

        val REQUEST = spec("WEB_TO_APP_REQUEST", """"handlerName":{"arg":0},"requestId":{"arg":1},"data":{"arg":2}""")
        val RESPONSE = spec(
            "WEB_TO_APP_RESPONSE",
            """"handlerName":{"arg":0},"requestId":{"arg":1},"success":{"arg":2},"data":{"arg":3}""",
        )
    }
}
