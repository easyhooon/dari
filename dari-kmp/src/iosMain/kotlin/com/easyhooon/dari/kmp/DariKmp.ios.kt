package com.easyhooon.dari.kmp

import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.kmp.objc.dari_kmp_interceptor
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLog

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformInterceptor(tag: String?): DariInterceptor? {
    val target = dari_kmp_interceptor(tag)
    if (target == null) {
        NSLog("Dari: DariObjC not found. Link the Dari Swift package (with DariObjC) into the app.")
        return null
    }
    return SwiftDariInterceptor(target, tag)
}
