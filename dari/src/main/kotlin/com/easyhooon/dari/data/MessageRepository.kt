package com.easyhooon.dari.data

import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.data.local.DariDatabase
import com.easyhooon.dari.data.local.MessageDao
import com.easyhooon.dari.data.local.toEntity
import com.easyhooon.dari.data.local.toMessageEntry
import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
            _entries.value = persisted
            initialized.complete(Unit)
        }
    }

    private fun retentionCutoff(): Long? =
        RetentionPolicy.cutoff(clock(), retentionPeriodMs)

    fun addEntry(entry: MessageEntry) {
        val tempId = temporaryId.getAndDecrement()
        val entryWithTempId = entry.copy(id = tempId)

        // Add to memory first for immediate UI update (applying TTL filter on the fly).
        _entries.update { current ->
            val pruned = RetentionPolicy.prune(current, retentionCutoff())
            val updated = pruned + entryWithTempId
            if (updated.size > maxEntries) updated.drop(updated.size - maxEntries) else updated
        }

        // Insert to DB asynchronously and update with actual id
        scope.launch {
            val actualId = dao.insert(entry.toEntity())
            _entries.update { current ->
                current.map { e ->
                    if (e.id == tempId) e.copy(id = actualId) else e
                }
            }
            dao.trimOldEntries(maxEntries)
            retentionCutoff()?.let { dao.deleteOlderThan(it) }
        }
    }

    fun updateEntry(requestId: String, tag: String? = null, transform: (MessageEntry) -> MessageEntry) {
        var updatedEntry: MessageEntry? = null
        _entries.update { current ->
            current.map { entry ->
                if (entry.requestId == requestId && entry.tag == tag) {
                    transform(entry).also { updatedEntry = it }
                } else {
                    entry
                }
            }
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
                    status = entry.status,
                    responseTimestamp = entry.responseTimestamp,
                )
            }
        }
    }

    fun clear() {
        _entries.value = emptyList()
        scope.launch { dao.clear() }
    }

    @VisibleForTesting
    internal fun close() {
        scope.cancel()
    }
}
