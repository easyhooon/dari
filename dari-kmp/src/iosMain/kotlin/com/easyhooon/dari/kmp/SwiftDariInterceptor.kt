package com.easyhooon.dari.kmp

import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.kmp.objc.dari_kmp_onAppToWebRequest
import com.easyhooon.dari.kmp.objc.dari_kmp_onAppToWebResponse
import com.easyhooon.dari.kmp.objc.dari_kmp_onPageUrlChanged
import com.easyhooon.dari.kmp.objc.dari_kmp_onWebToAppRequest
import com.easyhooon.dari.kmp.objc.dari_kmp_onWebToAppResponse
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNumber

/** Forwards to the Swift SDK's DariObjCInterceptor, resolved at runtime. */
@OptIn(ExperimentalForeignApi::class)
internal class SwiftDariInterceptor(
    private val target: Any,
    override val tag: String?,
) : DariInterceptor {
    override fun onPageUrlChanged(url: String?) = dari_kmp_onPageUrlChanged(target, url)

    override fun onWebToAppRequest(handlerName: String, requestId: String?, requestData: String?, fireAndForget: Boolean?) =
        onWebToAppRequest(handlerName, null, requestId, requestData, fireAndForget)

    override fun onWebToAppRequest(
        handlerName: String,
        displayName: String?,
        requestId: String?,
        requestData: String?,
        fireAndForget: Boolean?,
    ) = dari_kmp_onWebToAppRequest(target, handlerName, displayName, requestId, requestData, fireAndForget?.let(::NSNumber))

    override fun onWebToAppResponse(handlerName: String, requestId: String?, responseData: String?, isSuccess: Boolean) =
        dari_kmp_onWebToAppResponse(target, handlerName, requestId, responseData, isSuccess)

    override fun onAppToWebRequest(handlerName: String, requestId: String?, data: String?, fireAndForget: Boolean?) =
        onAppToWebRequest(handlerName, null, requestId, data, fireAndForget)

    override fun onAppToWebRequest(
        handlerName: String,
        displayName: String?,
        requestId: String?,
        data: String?,
        fireAndForget: Boolean?,
    ) = dari_kmp_onAppToWebRequest(target, handlerName, displayName, requestId, data, fireAndForget?.let(::NSNumber))

    override fun onAppToWebResponse(requestId: String?, isSuccess: Boolean, responseData: String?) =
        dari_kmp_onAppToWebResponse(target, requestId, isSuccess, responseData)
}
