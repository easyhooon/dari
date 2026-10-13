package com.easyhooon.dari

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.easyhooon.dari.data.InspectorEntry
import com.easyhooon.dari.data.RepositoryEvent
import com.easyhooon.dari.inspector.DariInspectorServer
import com.easyhooon.dari.inspector.DesktopPayloadMasker
import com.easyhooon.dari.inspector.InspectorDataSource
import com.easyhooon.dari.inspector.InspectorSession
import com.easyhooon.dari.inspector.inspectorAppInfo
import com.easyhooon.dari.inspector.protocol.InspectorProtocol
import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.interceptor.DefaultDariInterceptor
import com.easyhooon.dari.interceptor.ProtobufDariInterceptor
import com.easyhooon.dari.interceptor.ProtobufPayloadDecoder
import com.easyhooon.dari.runtime.DariRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

/**
 * Desktop variant of Dari: messages are viewed in the Dari desktop inspector over `adb forward`
 * instead of in-app. Same API as `dari` and `dari-noop`; in-app UI calls are no-ops here.
 *
 * Only debuggable builds capture anything. Every payload is masked by default before it leaves
 * the device, and only adb (shell) or root can connect.
 */
object Dari {

    private var enabled = false
    private var server: DariInspectorServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var payloadRedactor: DariPayloadRedactor? = null

    /** Called automatically by the library's Initializer. */
    @Synchronized
    fun init(context: Context, config: DariConfig = DariConfig()) {
        val appContext = context.applicationContext
        if (!isAllowed(appContext)) {
            Log.w(TAG, "dari-desktop is off: the app is not debuggable. Use dari-noop for release builds.")
            return
        }
        DariRuntime.init(appContext, config)
        enabled = true
        if (server == null) startServer(appContext)
    }

    /**
     * Runs after the built-in masking (tokens, passwords, cookies, auth headers; raw previews
     * are dropped) on every message sent to the desktop. Throwing hides the message.
     */
    fun setPayloadRedactor(redactor: DariPayloadRedactor?) {
        payloadRedactor = redactor
    }

    @Suppress("RedundantNullableReturnType") // null when the app is not debuggable
    fun createInterceptor(tag: String? = null): DariInterceptor? =
        if (enabled) DefaultDariInterceptor(tag) else null

    fun createInterceptor(
        tag: String? = null,
        protobufDecoder: ProtobufPayloadDecoder,
    ): ProtobufDariInterceptor? = if (enabled) DefaultDariInterceptor(tag, protobufDecoder) else null

    /** Clears all stored messages; connected desktops see the list cleared. */
    fun clear() {
        if (enabled) DariRuntime.repository.clear()
    }

    @Suppress("UNUSED_PARAMETER")
    fun setShakeToOpenEnabled(enabled: Boolean) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun setDarkMode(value: Boolean?) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun setJsonFoldingEnabled(enabled: Boolean) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun setShowTagChip(show: Boolean) = Unit

    fun showNotification() = Unit

    private fun startServer(context: Context) {
        val appInfo = inspectorAppInfo(context)
        val repository = DariRuntime.repository
        val source = object : InspectorDataSource {
            override val events: Flow<RepositoryEvent> get() = repository.events
            override fun snapshot(): List<InspectorEntry> = repository.inspectorSnapshot()
            override fun clear() = this@Dari.clear()
        }
        val redactor = DariPayloadRedactor { entry ->
            val masked = DesktopPayloadMasker.mask(entry)
            payloadRedactor?.redact(masked) ?: masked
        }
        server = DariInspectorServer(InspectorProtocol.socketName(context.packageName)) { input, output ->
            InspectorSession(input, output, appInfo, source, redactor, scope).run()
        }.also { it.start() }
    }

    // A non-debuggable build means dari-desktop leaked into a release-like variant; stay silent.
    @Suppress("DEPRECATION") // getApplicationInfo(String, Int) is fine for reading meta-data
    private fun isAllowed(context: Context): Boolean {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return true
        val metaData = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        }.getOrNull()
        return metaData?.getBoolean(META_ALLOW_NON_DEBUGGABLE, false) == true
    }

    private const val TAG = "DariDesktop"

    // Manifest <meta-data android:value="true"> that opts a non-debuggable internal build (e.g. staging) in.
    private const val META_ALLOW_NON_DEBUGGABLE = "com.easyhooon.dari.desktop.ALLOW_NON_DEBUGGABLE"
}
