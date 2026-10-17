package com.easyhooon.dari.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.data.local.DariDatabase
import com.easyhooon.dari.data.local.toEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageRepositoryRetentionTest {

    private lateinit var database: DariDatabase
    private var repository: MessageRepository? = null
    private var now = 10_000L

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DariDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        // Close only the scope; closing the database could race pending DAO writes.
        repository?.close()
    }

    private fun entry(id: String, timestamp: Long) = MessageEntry(
        requestId = id,
        handlerName = "handler",
        direction = MessageDirection.WEB_TO_APP,
        status = MessageStatus.SUCCESS,
        requestTimestamp = timestamp,
    )

    private fun createRepository(maxEntries: Int = 500, retentionPeriodMs: Long? = RETENTION_MS) = runBlocking {
        MessageRepository(database, maxEntries, retentionPeriodMs, clock = { now })
            .also { repository = it }
            .apply { initialized.await() }
    }

    private suspend fun persistedIds(): List<String> = database.messageDao().getAll().mapNotNull { it.requestId }.sorted()

    private fun MessageRepository.ids() = entries.value.mapNotNull { it.requestId }.sorted()

    // DB writes run on the repository's own scope, so wait for them to settle.
    private fun awaitPersisted(expected: List<String>) = runBlocking {
        withTimeout(5_000) {
            while (persistedIds() != expected) delay(10)
        }
    }

    @Test
    fun `init deletes expired rows when a retention period is set`() = runBlocking {
        database.messageDao().insert(entry("old", now - RETENTION_MS - 1).toEntity())
        database.messageDao().insert(entry("fresh", now - 1).toEntity())

        val repository = createRepository()

        assertEquals(listOf("fresh"), repository.ids())
        assertEquals(listOf("fresh"), persistedIds())
    }

    @Test
    fun `init keeps every row when retention is disabled`() = runBlocking {
        database.messageDao().insert(entry("old", 0L).toEntity())
        database.messageDao().insert(entry("fresh", now).toEntity())

        val repository = createRepository(retentionPeriodMs = null)

        assertEquals(listOf("fresh", "old"), repository.ids())
        assertEquals(listOf("fresh", "old"), persistedIds())
    }

    @Test
    fun `addEntry prunes expired entries from memory and the database`() {
        val repository = createRepository()
        repository.addEntry(entry("first", now))
        awaitPersisted(listOf("first"))

        now += RETENTION_MS + 1
        repository.addEntry(entry("second", now))

        assertEquals(listOf("second"), repository.ids())
        awaitPersisted(listOf("second"))
    }

    @Test
    fun `advancing the clock moves the cutoff between inserts`() {
        val repository = createRepository()
        repository.addEntry(entry("a", now))
        now += RETENTION_MS / 2
        repository.addEntry(entry("b", now))

        assertEquals(listOf("a", "b"), repository.ids())
        awaitPersisted(listOf("a", "b"))

        now += RETENTION_MS / 2 + 1
        repository.addEntry(entry("c", now))

        assertEquals(listOf("b", "c"), repository.ids())
        awaitPersisted(listOf("b", "c"))
    }

    @Test
    fun `maxEntries and retention both apply, whichever is hit first`() {
        val repository = createRepository(maxEntries = 2)
        repository.addEntry(entry("a", now))
        repository.addEntry(entry("b", now))
        repository.addEntry(entry("c", now))

        assertEquals(listOf("b", "c"), repository.ids())
        awaitPersisted(listOf("b", "c"))

        now += RETENTION_MS + 1
        repository.addEntry(entry("d", now))

        assertEquals(listOf("d"), repository.ids())
        awaitPersisted(listOf("d"))
    }

    private companion object {
        const val RETENTION_MS = 1_000L
    }
}
