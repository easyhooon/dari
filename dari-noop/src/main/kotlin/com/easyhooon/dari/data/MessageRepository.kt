package com.easyhooon.dari.data

import com.easyhooon.dari.MessageEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Noop implementation - does nothing.
 */
@Suppress("UNUSED_PARAMETER")
class MessageRepository(private val maxEntries: Int = 500) {

    val entries: StateFlow<List<MessageEntry>> = MutableStateFlow(emptyList())
    val messageCount: StateFlow<Int> = MutableStateFlow(0)

    fun addEntry(entry: MessageEntry) = Unit
    fun updateEntry(requestId: String, transform: (MessageEntry) -> MessageEntry) = Unit
    fun clear() = Unit
}