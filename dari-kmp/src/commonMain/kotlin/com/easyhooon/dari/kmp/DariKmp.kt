package com.easyhooon.dari.kmp

import com.easyhooon.dari.interceptor.DariInterceptor

/** Shared entry point for a bridge kept in `commonMain`. */
object DariKmp {
    /** Returns null when the platform SDK isn't available, e.g. the Swift SDK isn't linked on iOS. */
    fun createInterceptor(tag: String? = null): DariInterceptor? = platformInterceptor(tag)
}

internal expect fun platformInterceptor(tag: String?): DariInterceptor?
