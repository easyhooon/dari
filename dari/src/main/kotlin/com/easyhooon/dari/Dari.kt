package com.easyhooon.dari

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.easyhooon.dari.data.MessageRepository
import com.easyhooon.dari.interceptor.DariInterceptor
import com.easyhooon.dari.interceptor.DefaultDariInterceptor
import com.easyhooon.dari.notification.DariNotification
import com.easyhooon.dari.ui.DariActivity

/**
 * Singleton entry point for Dari.
 * Automatically initialized via androidx.startup.
 */
object Dari {

    internal lateinit var context: Context
        private set

    internal var config = DariConfig()
        private set

    internal val repository = MessageRepository()

    private var notification: DariNotification? = null

    /**
     * Initializes Dari.
     * Automatically called by the library's internal Initializer.
     */
    fun init(context: Context, config: DariConfig = DariConfig()) {
        this.context = context.applicationContext
        this.config = config

        if (config.showNotification) {
            notification = DariNotification(this.context)
        }

        addDynamicShortcut()
    }

    /**
     * Creates a [DariInterceptor] instance.
     * Returns [DefaultDariInterceptor] in debug/staging builds,
     * or null in release builds (noop module).
     */
    fun createInterceptor(): DariInterceptor? = DefaultDariInterceptor()

    /**
     * Adds a new message to the notification.
     */
    internal fun postMessageNotification(handlerName: String, direction: MessageDirection) {
        notification?.postMessage(handlerName, direction)
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
    }
}