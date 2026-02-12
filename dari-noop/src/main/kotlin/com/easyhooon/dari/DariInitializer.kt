package com.easyhooon.dari

import android.content.Context
import androidx.startup.Initializer

internal class DariInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        Dari.init(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}