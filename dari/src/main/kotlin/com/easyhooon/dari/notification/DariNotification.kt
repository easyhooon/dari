package com.easyhooon.dari.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.ui.DariActivity

/**
 * Chucker-style InboxStyle notification.
 * Displays recent bridge messages as lines within a single notification.
 */
internal class DariNotification(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "dari"
        private const val NOTIFICATION_ID = 0x44_6172  // "Dar"
        private const val MAX_LINES = 7
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Recent message lines displayed in InboxStyle */
    private val recentLines = ArrayDeque<String>(MAX_LINES)
    private var totalCount = 0

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dari",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun show() {
        postNotification()
    }

    /**
     * Adds a new bridge message to the notification.
     */
    fun postMessage(handlerName: String, direction: MessageDirection) {
        val directionLabel = when (direction) {
            MessageDirection.WEB_TO_APP -> "W\u2192A"
            MessageDirection.APP_TO_WEB -> "A\u2192W"
        }
        val line = "$directionLabel  $handlerName"

        if (recentLines.size >= MAX_LINES) {
            recentLines.removeFirst()
        }
        recentLines.addLast(line)
        totalCount++

        postNotification()
    }

    fun dismissAll() {
        recentLines.clear()
        totalCount = 0
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun postNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!isGranted) return
        }

        val pendingIntent = createPendingIntent()

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Recording bridge activity")

        recentLines.forEach { line ->
            inboxStyle.addLine(line)
        }

        val contentText = if (totalCount > 0) {
            "${recentLines.last()}"
        } else {
            "No messages yet"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Recording bridge activity")
            .setContentText(contentText)
            .setSubText("$totalCount")
            .setStyle(inboxStyle)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Clear", createClearPendingIntent())
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, DariActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createClearPendingIntent(): PendingIntent {
        val intent = Intent(context, ClearDariReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}