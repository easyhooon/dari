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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val reason: String? = null) : ConnectionState
    data class Connected(val app: AppInfo) : ConnectionState
}

/** Why the selected message has no [InspectorController.detail] yet. */
enum class DetailLoad { Loading, Failed }

/** UI state and connection lifecycle. All state is read and written on the main thread. */
class InspectorController(
    private val scope: CoroutineScope,
    private val adb: Adb?,
    private val clientVersion: String,
    preferredSerial: String? = null,
    private val maskingEnabled: () -> Boolean = { false },
    // Blocking; replaced in tests so sessions run without a device.
    private val openConnection: ((serial: String, applicationId: String) -> InspectorConnection)? =
        adb?.let { adb -> { serial, applicationId -> InspectorConnection.open(adb, serial, applicationId, clientVersion) } },
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

    // Messages added after the current selection was made; drives the "N new · Show latest" hint.
    private var arrivedSinceSelection by mutableStateOf<Set<Long>>(emptySet())
    var detail by mutableStateOf<WireMessageDetail?>(null)
        private set
    var detailLoad by mutableStateOf<DetailLoad?>(null)
        private set
    private var filterText by mutableStateOf("")
    var filter: String
        get() = filterText
        set(value) {
            filterText = value
            searchPayloads()
        }
    var directionFilter by mutableStateOf<String?>(null)
    var statusFilter by mutableStateOf<String?>(null)
    var tagFilter by mutableStateOf<String?>(null)
    var originFilter by mutableStateOf<String?>(null)

    /** Tags seen in this session, in order of first appearance. */
    val availableTags: List<String> get() = messages.values.mapNotNull { it.tag }.distinct()

    /** Page origins seen in this session, in order of first appearance. */
    val availableOrigins: List<String> get() = messages.values.mapNotNull { it.pageOrigin }.distinct()

    /** The connected app's dari predates 2.2.0 and masks regardless of the setting. */
    var appAlwaysMasks by mutableStateOf(false)
        private set

    /** The connected app's dari predates payload search, so the filter only matches summary fields. */
    var appLacksPayloadSearch by mutableStateOf(false)
        private set

    /** Short confirmation such as "Copied", cleared after a moment. */
    var notice by mutableStateOf<String?>(null)
        private set

    private var connection: InspectorConnection? = null

    // Masking updates run one at a time and send the latest setting, so rapid toggles apply in order.
    private val maskingMutex = Mutex()

    // Bumped once a masking change applies; detail responses requested before it are dropped.
    private var maskingGeneration = 0
    private var noticeJob: Job? = null

    // Payloads stay on the device, so the app runs the search; only valid for the query it was run with.
    private var payloadMatches by mutableStateOf("" to emptySet<Long>())
    private var searchJob: Job? = null
    private var detailJob: Job? = null

    // ANDROID_SERIAL or a pick in the device menu; automatic picks may be revised later.
    private var deviceChosenByUser = preferredSerial != null

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
        deviceChosenByUser = true
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
        val open = openConnection ?: return
        disconnect()
        connectJob = scope.launch {
            while (isActive) {
                state = ConnectionState.Connecting(state.reasonOrNull())
                val reason = try {
                    openAndRunSession(open, serial, appId)
                    null
                } catch (e: IOException) {
                    e.message
                } catch (_: TimeoutCancellationException) {
                    // A request timeout, not this job's cancellation: keep retrying.
                    "the app did not respond in time"
                } catch (e: SerializationException) {
                    "unexpected data from the app: ${e.message}"
                }
                // withContext still rethrows open()'s IOException after a cancel (e.g. a frozen app's handshake
                // timeout), so a replaced job would otherwise overwrite the newer session's state.
                ensureActive()
                reason?.let { state = ConnectionState.Connecting(it) }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun openAndRunSession(open: (String, String) -> InspectorConnection, serial: String, appId: String) {
        var opened: InspectorConnection? = null
        try {
            // Kept outside withContext's result, which is dropped when this job is cancelled while open() blocks.
            val conn = withContext(Dispatchers.IO) { open(serial, appId).also { opened = it } }
            connection = conn
            runSession(conn)
        } finally {
            opened?.let { conn ->
                if (connection === conn) connection = null
                withContext(NonCancellable + Dispatchers.IO) { conn.close() }
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
        searchJob?.cancel()
        searchJob = null
        connection?.let { conn -> scope.launch(Dispatchers.IO) { conn.close() } }
        connection = null
        appAlwaysMasks = false
        appLacksPayloadSearch = false
        state = ConnectionState.Idle
    }

    /** Applies the desktop's current masking setting to the connected app. */
    fun onMaskingChanged() {
        val conn = connection ?: return
        scope.launch {
            maskingMutex.withLock { runCatching { conn.setMasking(maskingEnabled()) } }
                .onSuccess {
                    // Summaries carry no payloads, so only the open detail needs refetching.
                    maskingGeneration++
                    detail = null
                    selectedId?.let(::loadDetail)
                    payloadMatches = "" to emptySet()
                    searchPayloads()
                }
                .onFailure {
                    AppLog.error("Changing masking failed", it)
                    maskingGeneration++
                    detailJob?.cancel()
                    detail = null
                    detailLoad = DetailLoad.Failed
                    // Session already gone (e.g. user disconnected): the next one applies the saved setting.
                    if (connection !== conn) return@onFailure
                    // The app may still send under the old setting; a new session applies the saved one.
                    showNotice("Couldn't change masking, reconnecting")
                    connect()
                }
        }
    }

    fun select(id: Long) {
        detailDismissed = false
        selectedId = id
        arrivedSinceSelection = emptySet()
        loadDetail(id)
    }

    fun closeDetail() {
        detailDismissed = true
        selectedId = null
        arrivedSinceSelection = emptySet()
        detailJob?.cancel()
        detail = null
        detailLoad = null
    }

    fun retryDetail() {
        selectedId?.let(::loadDetail)
    }

    /** Messages in [visible] that arrived while the selected one was open. */
    fun newerCount(visible: List<WireMessageSummary>): Int =
        if (selectedId == null) 0 else visible.count { it.id in arrivedSinceSelection }

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

    // Read Compose state (not `connection`) so menu items re-enable when a session starts.
    val canCopy: Boolean get() = state is ConnectionState.Connected && selectedId != null

    val canExport: Boolean get() = state is ConnectionState.Connected && messages.isNotEmpty()

    /** Copies the selected message as readable text, like the app's "Share as text". */
    fun copySelected(onCopied: () -> Unit = {}) {
        val id = selectedId ?: return
        scope.launch {
            val message = fetchDetail(id) ?: return@launch showNotice("Couldn't copy: message unavailable")
            copyText(MessageExport.text(message))
            showNotice("Copied message")
            onCopied()
        }
    }

    fun copy(text: String, what: String) {
        copyText(text)
        showNotice("Copied $what")
    }

    /** Saves the selected message, or with [all] every message passing the filters (oldest first). */
    fun export(target: File, format: ExportFormat, all: Boolean) {
        val app = (state as? ConnectionState.Connected)?.app ?: return
        val ids = if (all) visibleMessages().reversed().map { it.id } else listOfNotNull(selectedId)
        scope.launch {
            if (all) showNotice("Exporting ${ids.size} messages…", clearAfter = false)
            val details = ids.mapNotNull { fetchDetail(it) }
            val result = runCatching {
                withContext(Dispatchers.IO) { target.writeText(MessageExport.format(app, details, format)) }
            }
            result.exceptionOrNull()?.let { AppLog.error("Export to ${target.name} failed", it) }
            val saved = if (details.size == 1) "message" else "${details.size} messages"
            showNotice(if (result.isSuccess) "Saved $saved to ${target.name}" else "Export failed")
        }
    }

    private suspend fun fetchDetail(id: Long): WireMessageDetail? {
        detail?.takeIf { it.summary.id == id && messages[id] == it.summary }?.let { return it }
        val conn = connection ?: return null
        val generation = maskingGeneration
        return runCatching { conn.detail(id) }.getOrNull()?.takeIf { generation == maskingGeneration }
    }

    private fun showNotice(text: String, clearAfter: Boolean = true) {
        noticeJob?.cancel()
        notice = text
        if (clearAfter) {
            noticeJob = scope.launch {
                delay(NOTICE_MILLIS)
                notice = null
            }
        }
    }

    /** Newest first, after the search, direction, status, tag and origin facets. */
    fun visibleMessages(): List<WireMessageSummary> {
        val query = filter.trim()
        return messages.values.reversed().filter { message ->
            (directionFilter == null || message.direction == directionFilter) &&
                (statusFilter == null || message.status == statusFilter) &&
                (tagFilter == null || message.tag == tagFilter) &&
                (originFilter == null || message.pageOrigin == originFilter) &&
                (
                    query.isEmpty() ||
                        listOfNotNull(message.handlerName, message.displayName, message.tag, message.requestId, message.pageUrl)
                            .any { it.contains(query, ignoreCase = true) } ||
                        (payloadMatches.first == query && message.id in payloadMatches.second)
                    )
        }
    }

    private suspend fun runSession(conn: InspectorConnection) {
        appAlwaysMasks = !maskingMutex.withLock { conn.setMasking(maskingEnabled()) }
        val events = conn.observe()
        val backlog = conn.list()
        messages = emptyMap()
        arrivedSinceSelection = emptySet()
        detailJob?.cancel()
        detail = null
        detailLoad = null
        // Ids are only stable per app process, so a reconnect drops matches from the last one.
        payloadMatches = "" to emptySet()
        appLacksPayloadSearch = false
        merge(backlog)
        state = ConnectionState.Connected(conn.app)
        selectedId?.let { if (it in messages) loadDetail(it) }
        selectNewestIfNone()
        searchPayloads()
        for (event in events) apply(event)
    }

    // Internal so tests can feed events without a device.
    internal fun apply(event: ObserveEvent) {
        when (event) {
            is ObserveEvent.Added -> {
                merge(listOf(event.message))
                if (selectedId != null) {
                    val arrived = arrivedSinceSelection + event.message.id
                    // Drops evicted ids so the set stays bounded by MAX_MESSAGES.
                    arrivedSinceSelection = if (arrived.size > messages.size) arrived.filterTo(HashSet()) { it in messages } else arrived
                }
                selectNewestIfNone()
                searchPayloads()
            }
            is ObserveEvent.Updated -> {
                merge(listOf(event.message))
                if (event.message.id == selectedId) loadDetail(event.message.id)
                searchPayloads()
            }
            ObserveEvent.Cleared -> {
                messages = emptyMap()
                arrivedSinceSelection = emptySet()
                selectedId = null
                detailJob?.cancel()
                detail = null
                detailLoad = null
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

    // Debounced: typing and event bursts send one request once they settle.
    private fun searchPayloads() {
        searchJob?.cancel()
        val query = filter.trim()
        val conn = connection
        if (query.isEmpty() || conn == null || appLacksPayloadSearch) return
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val ids = try {
                conn.search(query)
            } catch (_: IOException) {
                return@launch
            } catch (_: TimeoutCancellationException) {
                return@launch
            }
            // An answer from a replaced session says nothing about the current app.
            if (connection !== conn) return@launch
            if (ids == null) {
                appLacksPayloadSearch = true
                return@launch
            }
            payloadMatches = query to ids
        }
    }

    private fun loadDetail(id: Long) {
        val conn = connection ?: return
        detailJob?.cancel()
        val generation = maskingGeneration
        detailLoad = DetailLoad.Loading
        detailJob = scope.launch {
            val result = runCatching { conn.detail(id) }
            // A request timeout also lands here; only this job's own cancellation stops it.
            ensureActive()
            if (selectedId != id || generation != maskingGeneration) return@launch
            result.exceptionOrNull()?.let { AppLog.error("Loading message $id failed", it) }
            detail = result.getOrNull()
            detailLoad = if (result.isSuccess) null else DetailLoad.Failed
        }
    }

    private suspend fun refreshDevices(adb: Adb) {
        val found = withContext(Dispatchers.IO) { runCatching { adb.devices() }.getOrDefault(emptyList()) }
        devices = found
        // Until the user picks a device, prefer one that is actually running a Dari app, and switch
        // to it if it shows up after another device was picked automatically.
        val current = selectedSerial?.takeIf { serial -> found.any { it.serial == serial } }
        val currentApps = current?.let { withContext(Dispatchers.IO) { adb.dariApps(it) } }.orEmpty()
        if (!deviceChosenByUser && state == ConnectionState.Idle && currentApps.isEmpty()) {
            val better = withContext(Dispatchers.IO) {
                found.firstOrNull { it.serial != current && adb.dariApps(it.serial).isNotEmpty() }
            }?.serial ?: current ?: found.firstOrNull()?.serial
            if (better != selectedSerial) {
                selectedSerial = better
                applicationId = ""
                discoveredApps = emptyList()
                return
            }
        }
        val serial = current ?: return
        val apps = currentApps
        discoveredApps = apps
        if (applicationId.isBlank() && apps.size == 1) applicationId = apps.single()
        if (autoConnect && state == ConnectionState.Idle && apps.size == 1 && applicationId == apps.single()) connect()
    }

    private fun ConnectionState.reasonOrNull(): String? = (this as? ConnectionState.Connecting)?.reason

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val MAX_MESSAGES = 2_000
        const val NOTICE_MILLIS = 2_500L
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
