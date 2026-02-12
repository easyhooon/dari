package com.easyhooon.dari.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.easyhooon.dari.Dari

/**
 * BroadcastReceiver that clears messages when the notification's Clear button is pressed.
 */
internal class ClearDariReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Dari.clear()
    }
}