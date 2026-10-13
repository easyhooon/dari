package com.easyhooon.dari

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.easyhooon.dari.data.DariPreferences
import com.easyhooon.dari.data.InspectorEntry
import com.easyhooon.dari.data.MessageRepository
import com.easyhooon.dari.data.RepositoryEvent
import com.easyhooon.dari.data.local.DariDatabase
import com.easyhooon.dari.inspector.DariInspectorServer
import com.easyhooon.dari.inspector.InspectorDataSource
import com.easyhooon.dari.inspector.InspectorSession
import com.easyhooon.dari.inspector.inspectorAppInfo
import com.easyhooon.dari.inspector.protocol.InspectorProtocol
import java.io.File
import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.interceptor.DefaultDariInterceptor
import com.easyhooon.dari.interceptor.ProtobufDariInterceptor
import com.easyhooon.dari.interceptor.ProtobufPayloadDecoder
import com.easyhooon.dari.notification.DariNotification
import com.easyhooon.dari.shake.DariShakeManager
import com.easyhooon.dari.ui.DariActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

/**
 * Singleton entry point for Dari.
 * Automatically initialized via androidx.startup.
 */
@SuppressLint("StaticFieldLeak")
object Dari {

    internal lateinit var context: Context
        private set

    internal var config = DariConfig()
        private set

    private var database: DariDatabase? = null

    internal lateinit var repository: MessageRepository
        private set

    private var notification: DariNotification? = null
    private var shakeManager: DariShakeManager? = null
    private var inspectorServer: DariInspectorServer? = null
    private val inspectorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal lateinit var preferences: DariPreferences
        private set

    /**
     * Initializes Dari.
     * Automatically called by the library's internal Initializer.
     */
    fun init(context: Context, config: DariConfig = DariConfig()) {
        this.context = context.applicationContext
        this.config = config

        if (database == null) {
            database = DariDatabase.create(this.context)
            repository = MessageRepository(
                database = database!!,
                maxEntries = config.maxEntries,
                retentionPeriodMs = config.retentionPeriod?.inWholeMilliseconds,
            )
        }

        // Guard re-initialization: DataStore throws IllegalStateException if a
        // second instance is created for the same file, which would happen on
        // any subsequent Dari.init() call (tests, double-startup, etc.).
        if (!::preferences.isInitialized) {
            preferences = DariPreferences(
                dataStore = createPreferenceDataStore(this.context),
                defaultShakeToOpen = config.shakeToOpen,
            )
        }

        if (config.showNotification) {
            notification = DariNotification(this.context)
        }

        applyShakeToOpen(preferences.shakeToOpen)

        addDynamicShortcut()
    }

    /**
     * Enable or disable shake-to-open at runtime. Persists across process restarts.
     */
    fun setShakeToOpenEnabled(enabled: Boolean) {
        preferences.setShakeToOpen(enabled)
        applyShakeToOpen(enabled)
    }

    /**
     * Override the dark-mode setting at runtime. Pass `null` to fall back to
     * the system theme. Persists across process restarts.
     */
    fun setDarkMode(value: Boolean?) {
        preferences.setDarkMode(value)
    }

    /**
     * Enable or disable controls for folding nested JSON objects and arrays.
     * Persists across process restarts.
     */
    fun setJsonFoldingEnabled(enabled: Boolean) {
        preferences.setJsonFoldingEnabled(enabled)
    }

    /**
     * Show or hide bridge tag chips beside handler names in the message list.
     * Persists across process restarts.
     */
    fun setShowTagChip(show: Boolean) {
        preferences.setShowTagChip(show)
    }

    /**
     * Lets the Dari desktop inspector read messages over `adb forward`. Off by default.
     * Only adb or root can connect; [redactor] runs on every message before it leaves the device.
     */
    @Synchronized
    fun startDesktopInspector(redactor: DariPayloadRedactor? = null) {
        stopDesktopInspector()
        val appInfo = inspectorAppInfo(context)
        val source = object : InspectorDataSource {
            override val events: Flow<RepositoryEvent> get() = repository.events
            override fun snapshot(): List<InspectorEntry> = repository.inspectorSnapshot()
            override fun clear() = this@Dari.clear()
        }
        inspectorServer = DariInspectorServer(InspectorProtocol.socketName(context.packageName)) { input, output ->
            InspectorSession(input, output, appInfo, source, redactor, inspectorScope).run()
        }.also { it.start() }
    }

    /** Stops the desktop inspector and closes open connections. */
    @Synchronized
    fun stopDesktopInspector() {
        inspectorServer?.stop()
        inspectorServer = null
    }

    private fun applyShakeToOpen(enabled: Boolean) {
        shakeManager?.unregister()
        shakeManager = if (enabled) {
            DariShakeManager(context).also { it.register() }
        } else {
            null
        }
    }

    private fun createPreferenceDataStore(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = {
                File(context.applicationContext.filesDir, "datastore/$PREFS_FILE_NAME")
            },
        )

    /**
     * Creates a [DariInterceptor] instance.
     * Returns [DefaultDariInterceptor] in debug/staging builds,
     * or null in release builds (noop module).
     */
    @Suppress("RedundantNullableReturnType") // Returns null in noop module
    fun createInterceptor(tag: String? = null): DariInterceptor? = DefaultDariInterceptor(tag)

    /**
     * Creates an interceptor that supports both the existing string payloads and protobuf bytes.
     * The decoder is supplied by the consuming app, so Dari does not impose a protobuf runtime.
     */
    fun createInterceptor(
        tag: String? = null,
        protobufDecoder: ProtobufPayloadDecoder,
    ): ProtobufDariInterceptor? = DefaultDariInterceptor(tag, protobufDecoder)

    /**
     * Adds a new message to the notification.
     */
    internal fun postMessageNotification(
        handlerName: String,
        displayName: String?,
        direction: MessageDirection,
        tag: String? = null,
    ) {
        notification?.postMessage(handlerName, displayName, direction, tag)
    }

    /**
     * Shows the notification (called after permission is granted).
     */
    fun showNotification() {
        if (notification == null && config.showNotification) {
            notification = DariNotification(context)
        }
        notification?.show()
    }

    /**
     * Clears all stored messages.
     */
    fun clear() {
        repository.clear()
        notification?.dismissAll()
    }

    /** Registers a dynamic shortcut shown on launcher long-press */
    private fun addDynamicShortcut() {
        try {
            val intent = Intent(context, DariActivity::class.java).apply {
                action = Intent.ACTION_VIEW
            }
            val shortcut = ShortcutInfoCompat.Builder(context, "open_dari")
                .setShortLabel("Open Dari")
                .setLongLabel("Open Dari")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_dari))
                .setIntent(intent)
                .build()

            ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcut))
        } catch (_: Exception) {
            // Shortcut registration may fail in test or non-launcher contexts
        }
    }

    private const val PREFS_FILE_NAME = "dari_preferences.preferences_pb"
}
