package com.easyhooon.dari.data

import com.easyhooon.dari.MessageEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory ring buffer message store.
 * Keeps up to [maxEntries] messages, removing the oldest when exceeded.
 */
class MessageRepository(private val maxEntries: Int = 500) {

    private val _entries = MutableStateFlow<List<MessageEntry>>(emptyList())
    val entries: StateFlow<List<MessageEntry>> = _entries.asStateFlow()

    private val _messageCount = MutableStateFlow(0)
    val messageCount: StateFlow<Int> = _messageCount.asStateFlow()

    fun addEntry(entry: MessageEntry) {
        _entries.update { current ->
            val updated = current + entry
            if (updated.size > maxEntries) updated.drop(updated.size - maxEntries) else updated
        }
        _messageCount.update { it + 1 }
    }

    fun updateEntry(requestId: String, transform: (MessageEntry) -> MessageEntry) {
        _entries.update { current ->
            current.map { entry ->
                if (entry.requestId == requestId) transform(entry) else entry
            }
        }
    }

    fun clear() {
        _entries.value = emptyList()
        _messageCount.value = 0
    }
}