package com.easyhooon.dari.kmp

import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.kmp.objc.dari_kmp_interceptor
import com.easyhooon.dari.kmp.objc.dari_kmp_status
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLog

// Resolved once; the linked Swift SDK can't change while the app runs.
@OptIn(ExperimentalForeignApi::class)
private val swiftSdkReady: Boolean by lazy {
    when (dari_kmp_status()) {
        0 -> true
        1 -> false.also { NSLog("Dari: the Dari Swift package isn't linked, so nothing is recorded.") }
        else -> false.also { NSLog("Dari: the linked Dari Swift package is older than dari-kmp. Update it.") }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformInterceptor(tag: String?): DariInterceptor? {
    if (!swiftSdkReady) return null
    val target = dari_kmp_interceptor(tag) ?: return null
    return SwiftDariInterceptor(target, tag)
}
