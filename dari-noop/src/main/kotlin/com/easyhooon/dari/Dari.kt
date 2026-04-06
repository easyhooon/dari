package com.easyhooon.dari

import android.content.Context
import com.easyhooon.dari.interceptor.DariInterceptor

/**
 * Noop implementation - does not create an interceptor in release builds.
 */
object Dari {

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context, config: DariConfig = DariConfig()) = Unit

    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun createInterceptor(tag: String? = null): DariInterceptor? = null

    fun clear() = Unit
}
