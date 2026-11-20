package com.easyhooon.dari.sample.webcontent

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.web.WebContent
import androidx.web.WebContentView
import androidx.web.WebFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Owns the [WebContent] engine across configuration changes; views come and go through [attach]/[detach]. */
class WebContentModel : ViewModel() {

    val webContent: WebContent? = runCatching {
        if (WebFeature.isFeatureSupported(WebFeature.WEB_CONTENT)) WebContent() else null
    }.onFailure { Log.w(LOG_TAG, "WebContent unavailable", it) }.getOrNull()

    private var view: WebContentView? = null
    private var attachCount = 0

    // WebView getters throw off the main thread, so bridge calls read the URL from here.
    @Volatile private var pageUrl: String? = null

    // Registered once, on the first view: the engine is expected to keep it across re-attach.
    private val bridge = Bridge()

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(context: Context): WebContentView {
        val attached = checkNotNull(webContent).attach(context, ::WebContentView)
        view = attached
        attachCount++
        Log.d(LOG_TAG, "attach #$attachCount: view=${attached.hashCode()}, url=${attached.url}")
        if (attachCount == 1) {
            attached.settings.javaScriptEnabled = true
            attached.settings.allowFileAccess = false
            attached.settings.allowContentAccess = false
            attached.webViewClient = object : WebViewClient() {
                // The Dari plugin reads the page URL here; it also fires on pushState.
                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    Log.d(LOG_TAG, "visited: $url")
                    pageUrl = url
                }
            }
            attached.addJavascriptInterface(bridge, BRIDGE_NAME)
            attached.loadUrl(START_URL)
        } else {
            sendToWeb("viewAttached", """{"attachCount":$attachCount}""")
        }
        return attached
    }

    fun detach() {
        Log.d(LOG_TAG, "detach: view=${view?.hashCode()}")
        view = null
        webContent?.detach()
    }

    override fun onCleared() {
        webContent?.close()
    }

    // App-to-Web notification; dropped while no view is attached.
    private fun sendToWeb(handlerName: String, data: String) {
        val target = view ?: return
        target.post { target.evaluateJavascript("onAppEvent('$handlerName', $data)", null) }
    }

    @Suppress("UnusedParameter") // handlerName is read by the dari { } mapping.
    private fun respond(handlerName: String, requestId: String, success: Boolean, data: String) {
        val target = view
        if (target == null) {
            Log.w(LOG_TAG, "No view for $requestId; response dropped")
            return
        }
        val escaped = JSONObject.quote(data)
        target.post { target.evaluateJavascript("onBridgeResponse('$requestId', $success, $escaped)", null) }
    }

    inner class Bridge {
        @Suppress("UnusedParameter") // data is read by the dari { } mapping.
        @JavascriptInterface
        fun request(handlerName: String, requestId: String, data: String?) {
            when (handlerName) {
                "ping" -> respond(handlerName, requestId, true, pong())
                // Rotate while this is pending to see whether the response still pairs with its request.
                "delayedPing" -> viewModelScope.launch {
                    delay(DELAY_MS)
                    respond(handlerName, requestId, true, pong())
                }
                else -> respond(handlerName, requestId, false, """{"error":"Unknown handler $handlerName"}""")
            }
        }
    }

    private fun pong(): String = JSONObject().apply {
        put("pong", System.currentTimeMillis())
        put("attachCount", attachCount)
        put("pageUrl", pageUrl ?: JSONObject.NULL)
    }.toString()

    private companion object {
        const val LOG_TAG = "Dari-WebContent"
        const val BRIDGE_NAME = "WebContentBridge"
        const val START_URL = "file:///android_asset/webcontent.html"
        const val DELAY_MS = 5_000L
    }
}
