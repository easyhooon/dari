package com.easyhooon.dari

import android.content.Context
import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.interceptor.ProtobufDariInterceptor
import com.easyhooon.dari.interceptor.ProtobufPayloadDecoder

/**
 * Noop implementation - does not create an interceptor in release builds.
 */
object Dari {

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context, config: DariConfig = DariConfig()) = Unit

    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun createInterceptor(tag: String? = null): DariInterceptor? = null

    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun createInterceptor(
        tag: String? = null,
        protobufDecoder: ProtobufPayloadDecoder,
    ): ProtobufDariInterceptor? = null

    @Suppress("UNUSED_PARAMETER")
    fun setShakeToOpenEnabled(enabled: Boolean) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun setDarkMode(value: Boolean?) = Unit

    fun showNotification() = Unit

    fun clear() = Unit
}
