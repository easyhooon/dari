package com.easyhooon.dari

import android.content.Context
import android.content.pm.PackageManager

/**
 * `<meta-data android:name="com.easyhooon.dari.DESKTOP_ONLY" android:value="true"/>` turns off every in-app
 * entry point (notification, shake, launcher shortcut), so a build such as staging is inspected only from
 * the desktop app. A non-debuggable build also needs `com.easyhooon.dari.desktop.ALLOW_NON_DEBUGGABLE`.
 */
internal object DesktopOnlyGate {
    private const val META_DESKTOP_ONLY = "com.easyhooon.dari.DESKTOP_ONLY"

    @Suppress("DEPRECATION") // getApplicationInfo(String, Int) is fine for reading meta-data
    fun isEnabled(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
    }.getOrNull()?.getBoolean(META_DESKTOP_ONLY, false) == true
}
