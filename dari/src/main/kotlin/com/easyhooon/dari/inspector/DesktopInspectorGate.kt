package com.easyhooon.dari.inspector

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

/**
 * The desktop socket opens only in debuggable builds. A non-debuggable internal build (e.g. staging)
 * can opt in with `<meta-data android:name="com.easyhooon.dari.desktop.ALLOW_NON_DEBUGGABLE" android:value="true"/>`.
 */
internal object DesktopInspectorGate {
    private const val TAG = "DariInspector"
    private const val META_ALLOW_NON_DEBUGGABLE = "com.easyhooon.dari.desktop.ALLOW_NON_DEBUGGABLE"

    @Suppress("DEPRECATION") // getApplicationInfo(String, Int) is fine for reading meta-data
    fun isAllowed(context: Context): Boolean {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return true
        val metaData = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        }.getOrNull()
        val allowed = metaData?.getBoolean(META_ALLOW_NON_DEBUGGABLE, false) == true
        if (!allowed) Log.i(TAG, "Desktop inspector is off: the app is not debuggable.")
        return allowed
    }
}
