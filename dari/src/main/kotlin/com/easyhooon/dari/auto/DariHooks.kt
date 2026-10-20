package com.easyhooon.dari.auto

import android.util.Log
import android.webkit.WebView
import com.easyhooon.dari.Dari
import com.easyhooon.dari.auto.BridgeMapping.Kind
import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.interceptor.ProtobufDariInterceptor
import com.easyhooon.dari.interceptor.ProtobufPayloadDecoder
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Called by bytecode the Dari Gradle plugin injects into debuggable builds; not meant for app code.
 * Each mapped call turns into the same [DariInterceptor] call a manual integration would make.
 * Hooks never throw, and the ones running on the JavaBridge thread never touch the WebView.
 */
object DariHooks {

    private const val LOG_TAG = "Dari"
    private const val UNKNOWN_HANDLER = "unknown"

    // Requests that never get a response (e.g. a page that stops answering) must not pile up.
    private const val MAX_OPEN_REQUESTS = 500

    private val mappings = ConcurrentHashMap<String, BridgeMapping>()

    // Everything below is guarded by lock: bridge calls arrive on the JavaBridge thread, navigation on main.
    private val lock = Any()
    private val bridges = WeakHashMap<Any, Registration>()
    private val webViews = WeakHashMap<WebView, WebViewState>()
    private var lastWebView: WeakReference<WebView>? = null
    private val detached = HashMap<String?, DariInterceptor>()
    private val openRequests = object : LinkedHashMap<String, OpenRequest>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OpenRequest>) = size > MAX_OPEN_REQUESTS
    }

    // Reads the decoder at decode time, so registering it after the first capture still works.
    private val sharedDecoder = ProtobufPayloadDecoder { payload, context -> Dari.protobufDecoder?.decode(payload, context) }

    internal var interceptorFactory: (String?) -> DariInterceptor? = { tag -> Dari.createInterceptor(tag, sharedDecoder) }

    private class Registration(val webView: WeakReference<WebView>, val name: String?)

    private class WebViewState(var jsName: String?) {
        val interceptors = HashMap<String?, DariInterceptor>()
        var pageUrl: String? = null
    }

    private class OpenRequest(val interceptor: DariInterceptor, val handlerName: String)

    /** Entry of a method mapped in `dari { }`. [spec] is the mapping the plugin serialized at build time. */
    @JvmStatic
    fun onMappedCall(spec: String, receiver: Any?, args: Array<Any?>?) {
        try {
            val mapping = mappings.getOrPut(spec) { BridgeMapping.parse(spec) }
            val values = args ?: emptyArray()
            dispatch(mapping, mapping.evaluate(values), receiver, values)
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Dari mapping failed for $spec", t)
        }
    }

    /** Replaces `webView.addJavascriptInterface(bridge, name)` so bridge calls know their WebView. */
    @JvmStatic
    fun addJavascriptInterface(webView: WebView, bridge: Any?, name: String?) {
        try {
            synchronized(lock) {
                if (bridge != null) bridges[bridge] = Registration(WeakReference(webView), name)
                val state = webViews.getOrPut(webView) { WebViewState(name) }
                if (state.jsName == null) state.jsName = name
                lastWebView = WeakReference(webView)
            }
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Dari could not record a JavaScript interface", t)
        }
        // Casts keep the platform's own failure for null arguments.
        webView.addJavascriptInterface(bridge as Any, name as String)
    }

    /** Entry of `WebViewClient.doUpdateVisitedHistory` overrides. */
    @JvmStatic
    fun onVisitedHistory(webView: WebView?, url: String?) {
        if (webView == null) return
        try {
            synchronized(lock) {
                val state = webViews.getOrPut(webView) { WebViewState(null) }
                state.pageUrl = url
                state.interceptors.values.forEach { it.onPageUrlChanged(url) }
                lastWebView = WeakReference(webView)
            }
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Dari could not record the page URL", t)
        }
    }

    private fun dispatch(mapping: BridgeMapping, values: Map<String, Any?>, receiver: Any?, args: Array<Any?>) {
        // Before any lookup, so a filtered call neither creates an interceptor nor consumes an open request.
        if (mapping.has("onlyIf") && !values["onlyIf"].asBoolean()) return
        val requestId = values["requestId"]?.toString()
        val data = values["data"]
        val success = if (mapping.has("success")) values["success"].asBoolean() else true
        when (mapping.kind) {
            Kind.WEB_TO_APP_REQUEST, Kind.APP_TO_WEB_REQUEST -> {
                val interceptor = interceptorFor(mapping, receiver, args) ?: return
                val handlerName = values["handlerName"]?.toString() ?: UNKNOWN_HANDLER
                val displayName = values["displayName"]?.toString()
                // false defers to DariConfig.fireAndForget, the same as passing null by hand.
                val fireAndForget = values["fireAndForget"].asBoolean().takeIf { it }
                if (mapping.kind == Kind.WEB_TO_APP_REQUEST) {
                    if (data is ByteArray && interceptor is ProtobufDariInterceptor) {
                        interceptor.onWebToAppProtobufRequest(handlerName, displayName, requestId, data, fireAndForget)
                    } else {
                        interceptor.onWebToAppRequest(handlerName, displayName, requestId, data.asPayload(), fireAndForget)
                    }
                } else {
                    if (data is ByteArray && interceptor is ProtobufDariInterceptor) {
                        interceptor.onAppToWebProtobufRequest(handlerName, displayName, requestId, data, fireAndForget)
                    } else {
                        interceptor.onAppToWebRequest(handlerName, displayName, requestId, data.asPayload(), fireAndForget)
                    }
                }
                if (requestId != null && fireAndForget != true) {
                    synchronized(lock) { openRequests[key(mapping.kind, requestId)] = OpenRequest(interceptor, handlerName) }
                }
            }
            Kind.WEB_TO_APP_RESPONSE, Kind.APP_TO_WEB_RESPONSE -> {
                // Like the manual API, a response without a requestId has nothing to pair with.
                if (requestId == null) return
                val open = synchronized(lock) { openRequests.remove(key(mapping.kind, requestId)) }
                val interceptor = open?.interceptor ?: interceptorFor(mapping, receiver, args) ?: return
                if (mapping.kind == Kind.WEB_TO_APP_RESPONSE) {
                    val handlerName = values["handlerName"]?.toString() ?: open?.handlerName ?: UNKNOWN_HANDLER
                    if (data is ByteArray && interceptor is ProtobufDariInterceptor) {
                        interceptor.onWebToAppProtobufResponse(handlerName, requestId, data, success)
                    } else {
                        interceptor.onWebToAppResponse(handlerName, requestId, data.asPayload(), success)
                    }
                } else {
                    if (data is ByteArray && interceptor is ProtobufDariInterceptor) {
                        interceptor.onAppToWebProtobufResponse(requestId, success, data)
                    } else {
                        interceptor.onAppToWebResponse(requestId, success, data.asPayload())
                    }
                }
            }
        }
    }

    // WebView lookup order: a WebView argument, the receiver itself, the receiver as a registered bridge,
    // then the WebView seen last (right for single-WebView screens).
    private fun interceptorFor(mapping: BridgeMapping, receiver: Any?, args: Array<Any?>): DariInterceptor? =
        synchronized(lock) {
            val registration = receiver?.let { bridges[it] }
            val webView = args.firstNotNullOfOrNull { it as? WebView }
                ?: receiver as? WebView
                ?: registration?.webView?.get()
                ?: lastWebView?.get()
            if (webView == null) {
                return@synchronized detached[mapping.tag] ?: interceptorFactory(mapping.tag)?.also { detached[mapping.tag] = it }
            }
            val state = webViews.getOrPut(webView) { WebViewState(null) }
            val tag = mapping.tag ?: registration?.name ?: state.jsName
            state.interceptors[tag] ?: interceptorFactory(tag)?.also {
                state.pageUrl?.let(it::onPageUrlChanged)
                state.interceptors[tag] = it
            }
        }

    private fun key(kind: Kind, requestId: String): String =
        if (kind == Kind.WEB_TO_APP_REQUEST || kind == Kind.WEB_TO_APP_RESPONSE) "w:$requestId" else "a:$requestId"

    private fun Any?.asBoolean(): Boolean = when (this) {
        is Boolean -> this
        is String -> equals("true", ignoreCase = true)
        is Number -> toInt() != 0
        else -> false
    }

    private fun Any?.asPayload(): String? = this?.toString()

    internal fun reset() = synchronized(lock) {
        mappings.clear()
        bridges.clear()
        webViews.clear()
        lastWebView = null
        detached.clear()
        openRequests.clear()
    }
}
