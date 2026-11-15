package com.easyhooon.dari.kmp

import android.util.Log
import com.easyhooon.dari.Dari
import com.easyhooon.dari.interceptor.DariInterceptor

internal actual fun platformInterceptor(tag: String?): DariInterceptor? = try {
    Dari.createInterceptor(tag)
} catch (e: NoClassDefFoundError) {
    Log.w("Dari", "Add io.github.easyhooon:dari (debug) or dari-noop (release) to the app.", e)
    null
}
