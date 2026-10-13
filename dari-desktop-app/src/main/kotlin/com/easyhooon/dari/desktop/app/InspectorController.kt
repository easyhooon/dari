package com.easyhooon.dari.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.easyhooon.dari.desktop.app.adb.Adb
import com.easyhooon.dari.desktop.app.adb.AdbDevice
import com.easyhooon.dari.desktop.app.client.InspectorConnection
import com.easyhooon.dari.inspector.protocol.AppInfo
import com.easyhooon.dari.inspector.protocol.ObserveEvent
import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.inspector.protocol.WireMessageSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val reason: String? = null) : ConnectionState
    data class Connected(val app: AppInfo) : ConnectionState
}

/** UI state and connection lifecycle. All state is read and written on the main thread. */
class InspectorController(
    private val scope: CoroutineScope,
    private val adb: Adb?,
    private val clientVersion: String,
    preferredSerial: String? = null,
) {
    var devices by mutableStateOf<List<AdbDevice>>(emptyList())
        private set
    var selectedSerial by mutableStateOf(preferredSerial)
        private set
    var discoveredApps by mutableStateOf<List<String>>(emptyList())
        private set
    var applicationId by mutableStateOf("")
    var state by mutableStateOf<ConnectionState>(ConnectionState.Idle)
        private set

    /** Oldest first, keyed by the stable wire id. */
    var messages by mutableStateOf<Map<Long, WireMessageSummary>>(emptyMap())
        private set
    var selectedId by mutableStateOf<Long?>(null)
        private set
    var detail by mutableStateOf<WireMessageDetail?>(null)
        private set
    var filter by mutableStateOf("")
    var directionFilter by mutableStateOf<String?>(null)
    var statusFilter by mutableStateOf<String?>(null)

    private var connection: InspectorConnection? = null

    // Set when the user closes the detail pane; new messages then stop reopening it.
    private var detailDismissed = false

    // Connects on its own when exactly one Dari app is found, until the user disconnects.
    private var autoConnect = true
    private var connectJob: Job? = null

    val adbMissing: Boolean get() = adb == null

    fun start() {
        if (adb == null) return
        // Forwards outlive the process when it is killed; clear them now and on JVM exit.
        Runtime.getRuntime().addShutdownHook(Thread { adb.removeDariForwards() })
        scope.launch {
            withContext(Dispatchers.IO) { adb.removeDariForwards() }
            while (isActive) {
                refreshDevices(adb)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun selectDevice(serial: String) {
        if (serial == selectedSerial) return
        disconnect()
        autoConnect = true
        selectedSerial = serial
        discoveredApps = emptyList()
    }

    /** Keeps reconnecting (e.g. app restart, cable replug) until [disconnect]. */
    fun connect() {
        val serial = selectedSerial ?: return
        val appId = applicationId.trim().ifEmpty { return }
        val adb = adb ?: return
        disconnect()
        connectJob = scope.launch {
            while (isActive) {
                state = ConnectionState.Connecting(state.reasonOrNull())
                try {
                    val conn = withContext(Dispatchers.IO) { InspectorConnection.open(adb, serial, appId, clientVersion) }
                    connection = conn
                    runSession(conn)
                } catch (e: IOException) {
                    state = ConnectionState.Connecting(e.message)
                } finally {
                    connection?.let { conn -> withContext(Dispatchers.IO) { conn.close() } }
                    connection = null
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** User-initiated: also stops auto-connect. */
    fun disconnectByUser() {
        autoConnect = false
        disconnect()
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        connection?.let { conn -> scope.launch(Dispatchers.IO) { conn.close() } }
        connection = null
        state = ConnectionState.Idle
    }

    fun select(id: Long) {
        detailDismissed = false
        selectedId = id
        loadDetail(id)
    }

    fun closeDetail() {
        detailDismissed = true
        selectedId = null
        detail = null
    }

    /** Moves the selection within [visible] by [delta] rows (keyboard ↑/↓). */
    fun moveSelection(visible: List<WireMessageSummary>, delta: Int) {
        if (visible.isEmpty()) return
        val current = visible.indexOfFirst { it.id == selectedId }
        val next = if (current < 0) 0 else (current + delta).coerceIn(0, visible.lastIndex)
        select(visible[next].id)
    }

    fun clearMessages() {
        val conn = connection ?: return
        scope.launch { runCatching { conn.clear() } }
    }

    /** Newest first, after the search, direction and status facets. */
    fun visibleMessages(): List<WireMessageSummary> {
        val query = filter.trim()
        return messages.values.reversed().filter { message ->
            (directionFilter == null || message.direction == directionFilter) &&
                (statusFilter == null || message.status == statusFilter) &&
                (
                    query.isEmpty() ||
                        listOfNotNull(message.handlerName, message.displayName, message.tag, message.requestId)
                            .any { it.contains(query, ignoreCase = true) }
                    )
        }
    }

    private suspend fun runSession(conn: InspectorConnection) {
        val events = conn.observe()
        val backlog = conn.list()
        messages = emptyMap()
        detail = null
        merge(backlog)
        state = ConnectionState.Connected(conn.app)
        selectedId?.let { if (it in messages) loadDetail(it) }
        selectNewestIfNone()
        for (event in events) apply(event)
    }

    private fun apply(event: ObserveEvent) {
        when (event) {
            is ObserveEvent.Added -> {
                merge(listOf(event.message))
                selectNewestIfNone()
            }
            is ObserveEvent.Updated -> {
                merge(listOf(event.message))
                if (event.message.id == selectedId) loadDetail(event.message.id)
            }
            ObserveEvent.Cleared -> {
                messages = emptyMap()
                selectedId = null
                detail = null
            }
        }
    }

    // Keeps the detail pane filled so the latest payloads are visible without a click.
    private fun selectNewestIfNone() {
        if (selectedId == null && !detailDismissed) {
            messages.values.lastOrNull()?.let { newest ->
                selectedId = newest.id
                loadDetail(newest.id)
            }
        }
    }

    private fun merge(items: List<WireMessageSummary>) {
        val updated = LinkedHashMap(messages)
        items.forEach { updated[it.id] = it }
        messages = if (updated.size > MAX_MESSAGES) {
            updated.entries.drop(updated.size - MAX_MESSAGES).associate { it.key to it.value }
        } else {
            updated
        }
    }

    private fun loadDetail(id: Long) {
        val conn = connection ?: return
        scope.launch {
            val result = runCatching { conn.detail(id) }.getOrNull()
            if (selectedId == id) detail = result
        }
    }

    private suspend fun refreshDevices(adb: Adb) {
        val found = withContext(Dispatchers.IO) { runCatching { adb.devices() }.getOrDefault(emptyList()) }
        devices = found
        // With nothing chosen yet, prefer a device that is actually running a Dari app.
        val serial = selectedSerial ?: withContext(Dispatchers.IO) {
            found.firstOrNull { adb.dariApps(it.serial).isNotEmpty() } ?: found.firstOrNull()
        }?.serial?.also { selectedSerial = it } ?: return
        if (found.none { it.serial == serial }) return
        val apps = withContext(Dispatchers.IO) { adb.dariApps(serial) }
        discoveredApps = apps
        if (applicationId.isBlank() && apps.size == 1) applicationId = apps.single()
        if (autoConnect && state == ConnectionState.Idle && apps.size == 1 && applicationId == apps.single()) connect()
    }

    private fun ConnectionState.reasonOrNull(): String? = (this as? ConnectionState.Connecting)?.reason

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val MAX_MESSAGES = 2_000
    }
}
