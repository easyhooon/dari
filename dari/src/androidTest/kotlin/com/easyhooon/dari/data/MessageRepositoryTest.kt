package com.easyhooon.dari.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.data.local.DariDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageRepositoryTest {

    private lateinit var database: DariDatabase
    private lateinit var repository: MessageRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DariDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = MessageRepository(database, maxEntries = 3)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addEntry_addsMessageToEntries() {
        val entry = createEntry("1")
        repository.addEntry(entry)

        assertEquals(1, repository.entries.value.size)
        assertEquals("1", repository.entries.value.first().requestId)
    }

    @Test
    fun addEntry_incrementsMessageCount() {
        repository.addEntry(createEntry("1"))
        repository.addEntry(createEntry("2"))

        assertEquals(2, repository.messageCount.value)
    }

    @Test
    fun addEntry_dropsOldestWhenExceedingMaxEntries() {
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
    fun updateEntry_transformsMatchingEntry() {
        repository.addEntry(createEntry("1"))
        repository.updateEntry("1") { it.copy(status = MessageStatus.SUCCESS) }

        assertEquals(MessageStatus.SUCCESS, repository.entries.value.first().status)
    }

    @Test
    fun updateEntry_doesNotAffectNonMatchingEntries() {
        repository.addEntry(createEntry("1"))
        repository.addEntry(createEntry("2"))
        repository.updateEntry("1") { it.copy(status = MessageStatus.ERROR) }

        assertEquals(MessageStatus.ERROR, repository.entries.value[0].status)
        assertEquals(MessageStatus.IN_PROGRESS, repository.entries.value[1].status)
    }

    @Test
    fun clear_removesAllEntriesAndResetsCount() {
        repository.addEntry(createEntry("1"))
        repository.addEntry(createEntry("2"))
        repository.clear()

        assertTrue(repository.entries.value.isEmpty())
        assertEquals(0, repository.messageCount.value)
    }

    @Test
    fun repository_restoresPersistedEntriesOnCreation() = runTest {
        repository.addEntry(createEntry("1"))
        repository.addEntry(createEntry("2"))

        val newRepository = MessageRepository(database, maxEntries = 3)
        newRepository.initialized.await()

        assertEquals(2, newRepository.entries.value.size)
    }

    private fun createEntry(requestId: String) = MessageEntry(
        requestId = requestId,
        handlerName = "testHandler",
        direction = MessageDirection.WEB_TO_APP,
    )
}