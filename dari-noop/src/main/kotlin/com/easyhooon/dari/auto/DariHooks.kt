package com.easyhooon.dari.auto

import android.webkit.WebView

/**
 * Pass-through twin of the Dari Gradle plugin's hooks, so instrumented builds that use dari-noop still run.
 * Signatures must match `:dari`; `checkDariApiParity` compares them.
 */
object DariHooks {

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun onMappedCall(spec: String, receiver: Any?, args: Array<Any?>?) = Unit

    @JvmStatic
    fun addJavascriptInterface(webView: WebView, bridge: Any?, name: String?) {
        // Casts keep the platform's own failure for null arguments.
        webView.addJavascriptInterface(bridge as Any, name as String)
    }

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun onVisitedHistory(webView: WebView?, url: String?) = Unit
}
