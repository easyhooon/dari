package com.easyhooon.dari.interceptor

/**
 * Interface for intercepting WebView bridge communication.
 * Injected into WebViewBridge to capture all bridge messages.
 */
interface DariInterceptor {
    /** Called when a Web -> App request is received */
    fun onWebToAppRequest(handlerName: String, requestId: String, requestData: String?)

    /** Called when a response is sent for a Web -> App request */
    fun onWebToAppResponse(handlerName: String, requestId: String, responseData: String?, isSuccess: Boolean)

    /** Called when an App -> Web message is sent */
    fun onAppToWebMessage(handlerName: String, requestId: String, data: String?)

    /** Called when a web response is received for an App -> Web request */
    fun onAppToWebResponse(requestId: String, isSuccess: Boolean, responseData: String?)
}