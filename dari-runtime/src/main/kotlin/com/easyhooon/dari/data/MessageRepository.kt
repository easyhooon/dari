package com.easyhooon.dari.data

import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.data.local.DariDatabase
import com.easyhooon.dari.data.local.MessageDao
import com.easyhooon.dari.data.local.toEntity
import com.easyhooon.dari.data.local.toMessageEntry
import com.easyhooon.dari.runtime.InternalDariApi
import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Message store backed by Room for persistence across sessions.
 * Keeps an in-memory [StateFlow] cache for immediate UI updates,
 * while persisting to the database in the background.
 */
class MessageRepository internal constructor(
    database: DariDatabase,
    private val maxEntries: Int = 500,
    /**
     * Optional TTL in milliseconds. Messages with `requestTimestamp` older than
     * `now - retentionPeriodMs` are pruned on init and after every insert.
     * `null` disables TTL cleanup.
     */
    private val retentionPeriodMs: Long? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val dao: MessageDao = database.messageDao(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val temporaryId = AtomicLong(-1L)

    private val _entries = MutableStateFlow<List<MessageEntry>>(emptyList())
    val entries: StateFlow<List<MessageEntry>> = _entries.asStateFlow()

    // Guards entry mutations together with their events so the inspector sees them in order.
    private val lock = Any()

    // DB id -> temp id for entries created in this process, so inspector ids never change.
    private val wireIds = HashMap<Long, Long>()

    private val _events = MutableSharedFlow<RepositoryEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    @InternalDariApi
    val events: SharedFlow<RepositoryEvent> = _events.asSharedFlow()

    /**
     * Derived projection of [entries]. Always in sync by construction — we
     * never mutate a parallel count field, so TTL pruning and `maxEntries`
     * trimming can't make it drift from the visible list length.
     *
     * Uses [SharingStarted.Eagerly] instead of `WhileSubscribed` / `Lazily`
     * so the upstream collection is active from repository creation: any
     * synchronous `messageCount.value` read (e.g. from tests or non-Compose
     * callers) is guaranteed to reflect the current `entries.size` without
     * waiting for a first subscriber to kick off the derivation.
     */
    val messageCount: StateFlow<Int> = entries
        .map { it.size }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    internal val initialized = CompletableDeferred<Unit>()

    init {
        scope.launch {
            val cutoff = retentionCutoff()
            if (cutoff != null) {
                dao.deleteOlderThan(cutoff)
            }
            val persisted = dao.getAll().map { it.toMessageEntry() }
            synchronized(lock) { _entries.value = persisted }
            initialized.complete(Unit)
        }
    }

    private fun retentionCutoff(): Long? =
        RetentionPolicy.cutoff(clock(), retentionPeriodMs)

    fun addEntry(entry: MessageEntry) {
        val tempId = temporaryId.getAndDecrement()
        val entryWithTempId = entry.copy(id = tempId)

        // Add to memory first for immediate UI update (applying TTL filter on the fly).
        synchronized(lock) {
            _entries.update { current ->
                val pruned = RetentionPolicy.prune(current, retentionCutoff())
                val updated = pruned + entryWithTempId
                if (updated.size > maxEntries) updated.drop(updated.size - maxEntries) else updated
            }
            _events.tryEmit(RepositoryEvent.Added(InspectorEntry(tempId, entryWithTempId)))
        }

        // Insert to DB asynchronously and update with actual id
        scope.launch {
            val actualId = dao.insert(entry.toEntity())
            synchronized(lock) {
                _entries.update { current ->
                    current.map { e ->
                        if (e.id == tempId) e.copy(id = actualId) else e
                    }
                }
                wireIds[actualId] = tempId
                pruneWireIds()
            }
            dao.trimOldEntries(maxEntries)
            retentionCutoff()?.let { dao.deleteOlderThan(it) }
        }
    }

    fun updateEntry(requestId: String, tag: String? = null, transform: (MessageEntry) -> MessageEntry) {
        var updatedEntry: MessageEntry? = null
        synchronized(lock) {
            _entries.update { current ->
                current.map { entry ->
                    if (entry.requestId == requestId && entry.tag == tag) {
                        transform(entry).also { updatedEntry = it }
                    } else {
                        entry
                    }
                }
            }
            updatedEntry?.let { _events.tryEmit(RepositoryEvent.Updated(InspectorEntry(wireIdOf(it.id), it))) }
        }
        updatedEntry?.let { entry ->
            scope.launch {
                dao.updateByRequestId(
                    requestId = requestId,
                    tag = tag,
                    responseData = entry.responseData,
                    responseDataTruncated = entry.responseDataTruncated,
                    responseContentType = entry.responsePayloadMetadata?.contentType,
                    responseOriginalSizeBytes = entry.responsePayloadMetadata?.originalSizeBytes,
                    responseDecodeStatus = entry.responsePayloadMetadata?.decodeStatus,
                    responseRawPreviewBase64 = entry.responsePayloadMetadata?.rawPreview?.base64,
                    responseRawPreviewSizeBytes = entry.responsePayloadMetadata?.rawPreview?.previewSizeBytes,
                    responseRawPreviewTruncated = entry.responsePayloadMetadata?.rawPreview?.truncated,
                    status = entry.status,
                    responseTimestamp = entry.responseTimestamp,
                )
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
            wireIds.clear()
            _events.tryEmit(RepositoryEvent.Cleared)
        }
        scope.launch { dao.clear() }
    }

    /** Current entries with their stable inspector ids, consistent with [events]. */
    @InternalDariApi
    fun inspectorSnapshot(): List<InspectorEntry> = synchronized(lock) {
        _entries.value.map { InspectorEntry(wireIdOf(it.id), it) }
    }

    private fun wireIdOf(id: Long): Long = wireIds[id] ?: id

    // Drop mappings for entries already trimmed from memory; called with lock held.
    private fun pruneWireIds() {
        if (wireIds.size <= maxEntries * 2) return
        val liveIds = _entries.value.mapTo(HashSet()) { it.id }
        wireIds.keys.retainAll(liveIds)
    }

    @VisibleForTesting
    internal fun close() {
        scope.cancel()
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 256
    }
}
