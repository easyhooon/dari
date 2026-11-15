package com.easyhooon.dari.sample.kmp

import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.kmp.DariKmp

/** A bridge kept in commonMain; records through whatever DariKmp returns on the platform. */
class SharedBridge {
    private val interceptor: DariInterceptor? = DariKmp.createInterceptor("kmp")

    val isRecording: Boolean get() = interceptor != null

    fun simulateTraffic(pageUrl: String) {
        val interceptor = interceptor ?: return
        interceptor.onPageUrlChanged(pageUrl)
        interceptor.onWebToAppRequest("getUser", "Get user", "kmp-1", """{"id":42}""")
        interceptor.onWebToAppResponse("getUser", "kmp-1", """{"name":"Dari"}""", isSuccess = true)
        interceptor.onAppToWebRequest("onThemeChanged", "kmp-2", """{"dark":true}""")
        interceptor.onAppToWebResponse("kmp-2", isSuccess = false, responseData = """{"error":"not ready"}""")
        interceptor.onWebToAppRequest("logEvent", "kmp-3", """{"event":"open"}""", fireAndForget = true)
    }
}
