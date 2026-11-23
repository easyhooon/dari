package com.easyhooon.dari.auto

import android.os.SystemClock
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebView
import com.easyhooon.dari.Dari
import com.easyhooon.dari.DeliveryStatus
import com.easyhooon.dari.MessageDirection
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
    // How long a message waits for its script; well past the inspectors' 5 s before they call it not confirmed.
    private const val DELIVERY_WINDOW_MS = 10_000L
    private val QUOTES = listOf("'", "\"", "`")

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

    // webViews: every WebView the bridge was registered on with its JS name, latest last. name and page are the
    // latest one's, and page outlives it, so calls that arrive after all of them are collected stay on that page.
    private class Registration(val webViews: List<RegisteredView>, val name: String?, val page: WebViewState) {
        fun live(): RegisteredView? = webViews.asReversed().firstOrNull { it.webView.get() != null }
    }

    private class RegisteredView(val webView: WeakReference<WebView>, val name: String?)

    private class WebViewState(var jsName: String?) {
        val interceptors = HashMap<String?, DariInterceptor>()
        var pageUrl: String? = null

        // Messages this page should receive through a script, waiting for it to run. Only scripts run on this
        // page's WebView can match them, and they expire, so a dropped message can't match an unrelated script later.
        val awaiting = ArrayList<AwaitingDelivery>()

        // Directions whose scripts have matched on this page; only those start out pending.
        val trackedDirections = HashSet<MessageDirection>()

        fun pruneAwaiting(now: Long) {
            awaiting.removeAll { now - it.since > DELIVERY_WINDOW_MS }
        }
    }

    private class OpenRequest(val interceptor: DariInterceptor, val handlerName: String)

    private class AwaitingDelivery(
        val interceptor: DariInterceptor,
        val requestId: String,
        val direction: MessageDirection,
        val since: Long,
    )

    internal var clock: () -> Long = SystemClock::uptimeMillis

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
                val state = webViews.getOrPut(webView) { WebViewState(name) }
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
                if (mapping.kind == Kind.APP_TO_WEB_REQUEST && requestId != null) {
                    awaitDelivery(interceptor, requestId, MessageDirection.APP_TO_WEB)
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
                    awaitDelivery(interceptor, requestId, MessageDirection.WEB_TO_APP)
                } else {
                    if (data is ByteArray && interceptor is ProtobufDariInterceptor) {
                        interceptor.onAppToWebProtobufResponse(requestId, success, data)
                    } else {
                        interceptor.onAppToWebResponse(requestId, success, data.asPayload())
                    }
                    // The page answered, so the request reached it whether or not a script was matched.
                    synchronized(lock) {
                        pageOf(interceptor)?.awaiting?.removeAll {
                            it.interceptor === interceptor && it.requestId == requestId && it.direction == MessageDirection.APP_TO_WEB
                        }
                    }
                    interceptor.onDeliveryChanged(requestId, MessageDirection.APP_TO_WEB, DeliveryStatus.DELIVERED)
                }
            }
        }
    }

    private fun awaitDelivery(interceptor: DariInterceptor, requestId: String, direction: MessageDirection) {
        // An interceptor without a page (no WebView known) has no script to wait for.
        val page = synchronized(lock) { pageOf(interceptor) } ?: return
        // Reported before the wait can match, so a script run on another thread can't follow DELIVERED with PENDING.
        if (synchronized(lock) { direction in page.trackedDirections }) {
            interceptor.onDeliveryChanged(requestId, direction, DeliveryStatus.PENDING)
        }
        synchronized(lock) {
            page.pruneAwaiting(clock())
            page.awaiting.removeAll { it.interceptor === interceptor && it.requestId == requestId && it.direction == direction }
            page.awaiting += AwaitingDelivery(interceptor, requestId, direction, clock())
        }
    }

    // Interceptors belong to one page; there are only a few pages, so a scan is enough.
    private fun pageOf(interceptor: DariInterceptor): WebViewState? =
        (webViews.values.asSequence() + bridges.values.asSequence().map { it.page })
            .firstOrNull { page -> page.interceptors.values.any { it === interceptor } }

    /**
     * Replaces `webView.evaluateJavascript(script, callback)`. When the script names exactly one message waiting for
     * delivery on this WebView's page, as a quoted literal, that message is marked delivered once the script ran.
     */
    @JvmStatic
    fun evaluateJavascript(webView: WebView, script: String?, callback: ValueCallback<String>?) {
        val match = try {
            script?.let { takeAwaiting(webView, it) }
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Dari could not match a script to a message", t)
            null
        }
        val tracked = if (match == null) {
            callback
        } else {
            ValueCallback<String> { result ->
                markDelivered(match.first, match.second)
                callback?.onReceiveValue(result)
            }
        }
        // Casts keep the platform's own failure for a null script.
        webView.evaluateJavascript(script as String, tracked)
    }

    private fun takeAwaiting(webView: WebView, script: String): Pair<WebViewState, AwaitingDelivery>? = synchronized(lock) {
        val page = webViews[webView] ?: return null
        page.pruneAwaiting(clock())
        val match = page.awaiting.filter { script.quotes(it.requestId) }.singleOrNull() ?: return null
        page.awaiting.remove(match)
        page to match
    }

    private fun markDelivered(page: WebViewState, awaiting: AwaitingDelivery) {
        try {
            synchronized(lock) { page.trackedDirections += awaiting.direction }
            awaiting.interceptor.onDeliveryChanged(awaiting.requestId, awaiting.direction, DeliveryStatus.DELIVERED)
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Dari could not record a delivery", t)
        }
    }

    // Only a quoted literal counts, so an id like "1" doesn't match every script that happens to contain the digit.
    private fun String.quotes(id: String): Boolean =
        QUOTES.any { quote -> contains(quote + id + quote) } || contains("\\\"$id\\\"")

    // Lookup order: a WebView argument, the receiver itself, the receiver as a registered bridge (its WebView, else
    // the page it was registered on), then the WebView seen last (right for single-WebView screens).
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
            val selected = registration?.webViews?.lastOrNull { webView != null && it.webView.get() === webView }
            val tag = mapping.tag ?: (selected ?: live)?.name ?: (if (live == null) registration?.name else null) ?: state.jsName
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
        lastWebView = null
        detached.clear()
        openRequests.clear()
        clock = SystemClock::uptimeMillis
    }
}
