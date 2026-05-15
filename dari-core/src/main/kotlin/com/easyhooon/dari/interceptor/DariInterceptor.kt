package com.easyhooon.dari.interceptor

/**
 * Interface for intercepting WebView bridge communication.
 * Injected into WebViewBridge to capture all bridge messages.
 */
interface DariInterceptor {
    /** Tag identifying the source of messages captured by this interceptor */
    val tag: String?
        get() = null

    // fireAndForget is Boolean? (not Boolean) to represent three states:
    // null = defer to DariConfig.fireAndForget, true = force fire-and-forget, false = force request-response.
    // A plain Boolean would collapse null into one of the two values, making per-call override impossible.

    /** Called when a Web -> App request is received */
    fun onWebToAppRequest(handlerName: String, requestId: String?, requestData: String?, fireAndForget: Boolean? = null)

    /** Called when a response is sent for a Web -> App request */
    fun onWebToAppResponse(handlerName: String, requestId: String?, responseData: String?, isSuccess: Boolean)

    /** Called when an App -> Web message is sent */
    fun onAppToWebMessage(handlerName: String, requestId: String?, data: String?, fireAndForget: Boolean? = null)

    /** Called when a web response is received for an App -> Web request */
    fun onAppToWebResponse(requestId: String?, isSuccess: Boolean, responseData: String?)
}
