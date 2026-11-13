package com.easyhooon.dari.kmp

import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.interceptor.DefaultDariInterceptor

internal actual fun platformInterceptor(tag: String?): DariInterceptor? = DefaultDariInterceptor(tag)
