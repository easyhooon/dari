package com.easyhooon.dari.inspector

import android.content.Context
import android.os.Build
import com.easyhooon.dari.desktop.BuildConfig
import com.easyhooon.dari.inspector.protocol.AppInfo

internal fun inspectorAppInfo(context: Context): AppInfo {
    val packageManager = context.packageManager
    val versionName = runCatching {
        packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
    return AppInfo(
        applicationId = context.packageName,
        appName = context.applicationInfo.loadLabel(packageManager).toString(),
        appVersion = versionName.orEmpty(),
        dariVersion = BuildConfig.DARI_VERSION,
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        sdkInt = Build.VERSION.SDK_INT,
    )
}
