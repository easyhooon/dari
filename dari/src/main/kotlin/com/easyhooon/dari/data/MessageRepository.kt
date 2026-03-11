package com.easyhooon.dari.data

import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.data.local.DariDatabase
import com.easyhooon.dari.data.local.toEntity
import com.easyhooon.dari.data.local.toMessageEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Message store backed by Room for persistence across sessions.
 * Keeps an in-memory [StateFlow] cache for immediate UI updates,
 * while persisting to the database in the background.
 */
class MessageRepository internal constructor(
    private val database: DariDatabase,
    private val maxEntries: Int = 500,
) {

    private val dao = database.messageDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<MessageEntry>>(emptyList())
    val entries: StateFlow<List<MessageEntry>> = _entries.asStateFlow()

    private val _messageCount = MutableStateFlow(0)
    val messageCount: StateFlow<Int> = _messageCount.asStateFlow()

    internal val initialized = CompletableDeferred<Unit>()

    init {
        scope.launch {
            val persisted = dao.getAll().map { it.toMessageEntry() }
            _entries.value = persisted
            _messageCount.value = persisted.size
            initialized.complete(Unit)
        }
    }

    fun addEntry(entry: MessageEntry) {
        _entries.update { current ->
            val updated = current + entry
            if (updated.size > maxEntries) updated.drop(updated.size - maxEntries) else updated
        }
        _messageCount.update { it + 1 }
        scope.launch {
            dao.insert(entry.toEntity())
            dao.trimOldEntries(maxEntries)
        }
    }

    fun updateEntry(requestId: String, transform: (MessageEntry) -> MessageEntry) {
        var updatedEntry: MessageEntry? = null
        _entries.update { current ->
            current.map { entry ->
                if (entry.requestId == requestId) {
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
                    responseData = entry.responseData,
                    status = entry.status,
                    responseTimestamp = entry.responseTimestamp,
                )
            }
        }
    }

    fun clear() {
        _entries.value = emptyList()
        _messageCount.value = 0
        scope.launch { dao.clear() }
    }
}