package com.easyhooon.dari.shake

import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.easyhooon.dari.ui.DariActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Lifecycle-aware manager that listens for shake gestures while the app is in the foreground
 * and opens DariActivity when a shake is detected.
 */
internal class DariShakeManager(private val context: Context) {

    private var scope: CoroutineScope? = null

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            startListening()
        }

        override fun onStop(owner: LifecycleOwner) {
            stopListening()
        }
    }

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    fun unregister() {
        stopListening()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
    }

    private fun startListening() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        context.shakeEvents()
            .onEach { openDariActivity() }
            .launchIn(scope!!)
    }

    private fun stopListening() {
        scope?.cancel()
        scope = null
    }

    private fun openDariActivity() {
        val intent = Intent(context, DariActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}