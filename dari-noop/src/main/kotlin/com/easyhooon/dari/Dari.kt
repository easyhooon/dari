package com.easyhooon.dari

import android.content.Context
import com.easyhooon.dari.interceptor.DariInterceptor

/**
 * Noop implementation - does not create an interceptor in release builds.
 */
object Dari {

    fun init(context: Context, config: DariConfig = DariConfig()) = Unit

    fun createInterceptor(): DariInterceptor? = null

    fun clear() = Unit
}