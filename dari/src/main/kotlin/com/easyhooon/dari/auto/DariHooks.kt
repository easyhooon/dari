package com.easyhooon.dari.auto

import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
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
    private const val WEB_CONTENT_VIEW = "androidx.web.WebContentView"

    // Requests that never get a response (e.g. a page that stops answering) must not pile up.
    private const val MAX_OPEN_REQUESTS = 500

    private val mappings = ConcurrentHashMap<String, BridgeMapping>()

    // Everything below is guarded by lock: bridge calls arrive on the JavaBridge thread, navigation on main.
    private val lock = Any()
    private val bridges = WeakHashMap<Any, Registration>()
    private val webViews = WeakHashMap<WebView, WebViewState>()

    // androidx.web's WebContent moves one page across views but keeps its WebViewClient, so its state is keyed
    // by that client. The engine holds the client, so the state outlives any one view and goes with close(),
    // unless the client itself outlives the engine (e.g. a singleton).
    private val engines = WeakHashMap<WebViewClient, WebViewState>()
    private var lastWebView: WeakReference<WebView>? = null
    private val detached = HashMap<String?, DariInterceptor>()
    private val openRequests = object : LinkedHashMap<String, OpenRequest>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OpenRequest>) = size > MAX_OPEN_REQUESTS
    }

    // Reads the decoder at decode time, so registering it after the first capture still works.
    private val sharedDecoder = ProtobufPayloadDecoder { payload, context -> Dari.protobufDecoder?.decode(payload, context) }

    internal var interceptorFactory: (String?) -> DariInterceptor? = { tag -> Dari.createInterceptor(tag, sharedDecoder) }

    // webViews: every WebView the bridge was registered on with its JS name, latest last. name and page are the
    // latest one's, and page outlives it, so calls that arrive after all of them are collected stay on that page.
    private class Registration(val webViews: List<RegisteredView>, val name: String?, val page: WebViewState) {
        fun live(): RegisteredView? = webViews.asReversed().firstOrNull { it.webView.get() != null }
    }

    private class RegisteredView(val webView: WeakReference<WebView>, val name: String?)

    private class WebViewState(var jsName: String?) {
        val interceptors = HashMap<String?, DariInterceptor>()
        var pageUrl: String? = null

        // Back-forward URLs of the view last showing it; set only for androidx.web pages.
        var history: List<String>? = null
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
                val state = engineState(webView) ?: webViews.getOrPut(webView) { WebViewState(name) }
                if (state.jsName == null) state.jsName = name
                if (bridge != null) {
                    val others = bridges[bridge]?.webViews.orEmpty()
                        .filter { it.webView.get().let { view -> view != null && view !== webView } }
                    bridges[bridge] = Registration(others + RegisteredView(WeakReference(webView), name), name, state)
                }
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
                val state = engineState(webView) ?: webViews.getOrPut(webView) { WebViewState(null) }
                state.pageUrl = url
                state.interceptors.values.forEach { it.onPageUrlChanged(url) }
                lastWebView = WeakReference(webView)
            }
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Dari could not record the page URL", t)
        }
    }

    // Main thread only: reads the WebView's client. Bridges registered on earlier views of the engine move here.
    private fun engineState(webView: WebView): WebViewState? {
        if (!isWebContentView(webView)) return null
        val engine = webView.webViewClient?.takeIf { it.javaClass != WebViewClient::class.java } ?: return null
        val state = engines.getOrPut(engine) {
            val current = webViews[webView]
            current?.takeIf { it in enginePages() } ?: stateFromHistory(webView) ?: current ?: WebViewState(null)
        }
        val previous = webViews.put(webView, state)
        if (state.jsName == null) state.jsName = previous?.jsName
        state.history = historyOf(webView)
        bridges.entries
            .filter { (_, registration) ->
                registration.page === state || registration.live()?.webView?.get()?.let { webViews[it] } === state
            }
            .forEach { entry ->
                val registration = entry.value
                val others = registration.webViews.filter { registered ->
                    registered.webView.get().let { view -> view != null && view !== webView && webViews[view] !== state }
                }
                entry.setValue(Registration(others + RegisteredView(WeakReference(webView), registration.name), registration.name, state))
            }
        return state
    }

    // A client set anew on every attach hides the engine, but the new view inherits its history. Takes the one
    // engine page that no attached view shows and whose recorded history this view's history starts with.
    private fun stateFromHistory(webView: WebView): WebViewState? {
        val shown = webViews.filterKeys { it.isAttachedToWindow && it !== webView }.values.toSet()
        val history = historyOf(webView)
        return enginePages().singleOrNull { page ->
            val recorded = page.history.orEmpty()
            page !in shown && if (recorded.isEmpty()) {
                page.pageUrl != null && page.pageUrl in history
            } else {
                history.size >= recorded.size && history.subList(0, recorded.size) == recorded
            }
        }
    }

    private fun historyOf(webView: WebView): List<String> =
        webView.copyBackForwardList().let { list -> (0 until list.size).map { list.getItemAtIndex(it).url } }

    // A replaced client can be collected, so pages are also reached through their bridges.
    private fun enginePages(): Set<WebViewState> =
        (engines.values + bridges.values.map { it.page }).filter { it.history != null }.toSet()

    // Matched by name so apps without androidx.web pull nothing in.
    private fun isWebContentView(view: WebView) =
        generateSequence<Class<*>>(view.javaClass) { it.superclass }.any { it.name == WEB_CONTENT_VIEW }

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

    // Lookup order: a WebView argument, the receiver itself, the receiver as a registered bridge (its WebView, else
    // the page it was registered on or moved to), then the WebView seen last (right for single-WebView screens).
    private fun interceptorFor(mapping: BridgeMapping, receiver: Any?, args: Array<Any?>): DariInterceptor? =
        synchronized(lock) {
            val registration = receiver?.let { bridges[it] }
            val live = registration?.live()
            val webView = args.firstNotNullOfOrNull { it as? WebView }
                ?: receiver as? WebView
                ?: live?.webView?.get()
            val state = webView?.let(::stateOf)
                ?: registration?.page
                ?: lastWebView?.get()?.let(::stateOf)
                ?: return@synchronized detached[mapping.tag] ?: interceptorFactory(mapping.tag)?.also { detached[mapping.tag] = it }
            // The JS name the bridge has on that WebView; the latest one once all of them are collected.
            val tag = mapping.tag ?: (if (live != null) live.name else registration?.name) ?: state.jsName
            state.interceptors[tag] ?: interceptorFactory(tag)?.also {
                state.pageUrl?.let(it::onPageUrlChanged)
                state.interceptors[tag] = it
            }
        }

    private fun stateOf(webView: WebView): WebViewState = webViews.getOrPut(webView) { WebViewState(null) }

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
        engines.clear()
        lastWebView = null
        detached.clear()
        openRequests.clear()
    }
}
