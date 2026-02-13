package com.easyhooon.dari.data

import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageRepositoryTest {

    private lateinit var repository: MessageRepository

    @Before
    fun setup() {
        repository = MessageRepository(maxEntries = 3)
    }

    @Test
    fun `addEntry adds message to entries`() {
        val entry = createEntry("1")
        repository.addEntry(entry)

        assertEquals(1, repository.entries.value.size)
        assertEquals("1", repository.entries.value.first().requestId)
    }

    @Test
    fun `addEntry increments messageCount`() {
        repository.addEntry(createEntry("1"))
        repository.addEntry(createEntry("2"))

        assertEquals(2, repository.messageCount.value)
    }

    @Test
    fun `addEntry drops oldest when exceeding maxEntries`() {
        repository.addEntry(createEntry("1"))
        repository.addEntry(createEntry("2"))
        repository.addEntry(createEntry("3"))
        repository.addEntry(createEntry("4"))

        val entries = repository.entries.value
        assertEquals(3, entries.size)
        assertEquals("2", entries[0].requestId)
        assertEquals("3", entries[1].requestId)
        assertEquals("4", entries[2].requestId)
    }

    @Test
    fun `updateEntry transforms matching entry`() {
        repository.addEntry(createEntry("1"))
        repository.updateEntry("1") { it.copy(status = MessageStatus.SUCCESS) }

        assertEquals(MessageStatus.SUCCESS, repository.entries.value.first().status)
    }

    @Test
    fun `updateEntry does not affect non-matching entries`() {
        repository.addEntry(createEntry("1"))
        repository.addEntry(createEntry("2"))
        repository.updateEntry("1") { it.copy(status = MessageStatus.ERROR) }

        assertEquals(MessageStatus.ERROR, repository.entries.value[0].status)
        assertEquals(MessageStatus.IN_PROGRESS, repository.entries.value[1].status)
    }

    @Test
    fun `clear removes all entries and resets count`() {
        repository.addEntry(createEntry("1"))
        repository.addEntry(createEntry("2"))
        repository.clear()

        assertTrue(repository.entries.value.isEmpty())
        assertEquals(0, repository.messageCount.value)
    }

    private fun createEntry(requestId: String) = MessageEntry(
        requestId = requestId,
        handlerName = "testHandler",
        direction = MessageDirection.WEB_TO_APP,
    )
}
