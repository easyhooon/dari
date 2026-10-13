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
import com.easyhooon.dari.data.MessageRepository
import kotlinx.coroutines.flow.Flow
import com.easyhooon.dari.inspector.protocol.InspectorProtocol
import com.easyhooon.dari.inspector.inspectorAppInfo
import com.easyhooon.dari.inspector.InspectorSession
import com.easyhooon.dari.inspector.InspectorDataSource
import com.easyhooon.dari.inspector.DesktopPayloadMasker
import com.easyhooon.dari.inspector.DesktopInspectorGate
import com.easyhooon.dari.inspector.DariInspectorServer
import com.easyhooon.dari.data.InspectorEntry
import com.easyhooon.dari.data.RepositoryEvent
import java.io.File
import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.interceptor.DefaultDariInterceptor
import com.easyhooon.dari.interceptor.ProtobufDariInterceptor
import com.easyhooon.dari.interceptor.ProtobufPayloadDecoder
import com.easyhooon.dari.notification.DariNotification
import com.easyhooon.dari.runtime.DariRuntime
import com.easyhooon.dari.shake.DariShakeManager
import com.easyhooon.dari.ui.DariActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Singleton entry point for Dari.
 * Automatically initialized via androidx.startup.
 */
@SuppressLint("StaticFieldLeak")
object Dari {

    internal lateinit var context: Context
        private set

    internal val config: DariConfig
        get() = DariRuntime.config

    internal val repository: MessageRepository
        get() = DariRuntime.repository

    private var notificationJob: Job? = null

    @Volatile
    private var payloadRedactor: DariPayloadRedactor? = null

    private var inspectorServer: DariInspectorServer? = null
    private var inspectorJob: Job? = null
    private var desktopInspectorEnabled = true

    // While a desktop is connected the in-app notification and shake stay quiet.
    @Volatile
    private var desktopConnected = false

    private var notification: DariNotification? = null
    private var shakeManager: DariShakeManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal lateinit var preferences: DariPreferences
        private set

    /**
     * Initializes Dari.
     * Automatically called by the library's internal Initializer.
     */
    fun init(context: Context, config: DariConfig = DariConfig()) {
        this.context = context.applicationContext
        DariRuntime.init(this.context, config)

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
        observeNewMessages()

        applyShakeToOpen(preferences.shakeToOpen)

        addDynamicShortcut()

        if (desktopInspectorEnabled) startDesktopInspector()
    }

    /**
     * Enable or disable shake-to-open at runtime. Persists across process restarts.
     */
    fun setShakeToOpenEnabled(enabled: Boolean) {
        preferences.setShakeToOpen(enabled)
        applyShakeToOpen(enabled)
    }

    /**
     * Lets the Dari desktop app connect over `adb` (on by default in debuggable builds).
     * Only adb or root can connect, and payloads are masked before they leave the device.
     */
    @Synchronized
    fun setDesktopInspectorEnabled(enabled: Boolean) {
        desktopInspectorEnabled = enabled
        if (!::context.isInitialized) return
        if (enabled) startDesktopInspector() else stopDesktopInspector()
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
     * Runs after the built-in masking (tokens, passwords, cookies, auth headers; raw previews are
     * dropped) on every message sent to the desktop app. Throwing hides the message.
     */
    fun setPayloadRedactor(redactor: DariPayloadRedactor?) {
        payloadRedactor = redactor
    }

    @Synchronized
    private fun startDesktopInspector() {
        if (inspectorServer != null || !DesktopInspectorGate.isAllowed(context)) return
        val repository = repository
        val source = object : InspectorDataSource {
            override val events: Flow<RepositoryEvent> get() = repository.events
            override fun snapshot(): List<InspectorEntry> = repository.inspectorSnapshot()
            override fun clear() = this@Dari.clear()
        }
        val redactor = DariPayloadRedactor { entry ->
            val masked = DesktopPayloadMasker.mask(entry)
            payloadRedactor?.redact(masked) ?: masked
        }
        val appInfo = inspectorAppInfo(context)
        val server = DariInspectorServer(InspectorProtocol.socketName(context.packageName)) { input, output ->
            InspectorSession(input, output, appInfo, source, redactor, scope).run()
        }
        inspectorServer = server.also { it.start() }
        // Shake registration touches the process lifecycle, which only allows the main thread.
        inspectorJob = scope.launch(Dispatchers.Main) {
            server.connectedClients.collect { count -> onDesktopConnectionChanged(count > 0) }
        }
    }

    @Synchronized
    private fun stopDesktopInspector() {
        inspectorJob?.cancel()
        inspectorJob = null
        inspectorServer?.stop()
        inspectorServer = null
        scope.launch(Dispatchers.Main) { onDesktopConnectionChanged(false) }
    }

    private fun onDesktopConnectionChanged(connected: Boolean) {
        if (connected == desktopConnected) return
        desktopConnected = connected
        if (connected) notification?.dismissAll()
        applyShakeToOpen(preferences.shakeToOpen)
    }

    private fun applyShakeToOpen(enabled: Boolean) {
        shakeManager?.unregister()
        shakeManager = if (enabled && !desktopConnected) {
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

    // UNDISPATCHED subscribes before init returns, so the first captured message is not missed.
    private fun observeNewMessages() {
        if (notificationJob != null) return
        notificationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.events.collect { event ->
                if (event is RepositoryEvent.Added && !desktopConnected) {
                    val entry = event.item.entry
                    notification?.postMessage(entry.handlerName, entry.displayName, entry.direction, entry.tag)
                }
            }
        }
    }

    /**
     * Shows the notification (called after permission is granted).
     */
    fun showNotification() {
        if (desktopConnected) return
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
