package com.easyhooon.dari.data

import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryTest {

    @Test
    fun `addEntry is no-op and entries remain empty`() {
        val repository = MessageRepository()
        val entry = MessageEntry(
            requestId = "1",
            handlerName = "test",
            direction = MessageDirection.WEB_TO_APP,
        )
        repository.addEntry(entry)

        assertTrue(repository.entries.value.isEmpty())
        assertEquals(0, repository.messageCount.value)
    }

    @Test
    fun `updateEntry is no-op and does not throw`() {
        val repository = MessageRepository()
        repository.updateEntry("1") { it.copy(status = MessageStatus.SUCCESS) }

        assertTrue(repository.entries.value.isEmpty())
    }

    @Test
    fun `clear is no-op and does not throw`() {
        val repository = MessageRepository()
        repository.clear()

        assertTrue(repository.entries.value.isEmpty())
        assertEquals(0, repository.messageCount.value)
    }
}
