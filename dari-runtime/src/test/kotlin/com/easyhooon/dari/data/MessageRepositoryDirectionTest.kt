package com.easyhooon.dari.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.data.local.DariDatabase
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
class MessageRepositoryDirectionTest {

    private lateinit var database: DariDatabase
    private lateinit var repository: MessageRepository

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DariDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = MessageRepository(database).apply { initialized.await() }
        repository.addEntry(entry(MessageDirection.WEB_TO_APP))
        repository.addEntry(entry(MessageDirection.APP_TO_WEB))
        awaitPersisted(mapOf(WEB to MessageStatus.IN_PROGRESS, APP to MessageStatus.IN_PROGRESS))
    }

    @After
    fun tearDown() {
        // Close only the scope; closing the database could race pending DAO writes.
        repository.close()
    }

    private fun entry(direction: MessageDirection) = MessageEntry(
        requestId = REQUEST_ID,
        handlerName = "handler",
        direction = direction,
        tag = TAG,
        status = MessageStatus.IN_PROGRESS,
    )

    private fun List<MessageEntry>.statuses() = associate { it.direction to it.status }

    private suspend fun persistedStatuses() = database.messageDao().getAll().associate { it.direction to it.status }

    // DB writes run on the repository's own scope, so wait for them to settle.
    private fun awaitPersisted(expected: Map<MessageDirection, MessageStatus>) = runBlocking {
        withTimeout(5_000) {
            while (persistedStatuses() != expected) delay(10)
        }
    }

    @Test
    fun `web to app response completes only the web to app request`() {
        repository.updateEntry(REQUEST_ID, TAG, WEB) { it.copy(status = MessageStatus.SUCCESS) }

        val expected = mapOf(WEB to MessageStatus.SUCCESS, APP to MessageStatus.IN_PROGRESS)
        assertEquals(expected, repository.entries.value.statuses())
        awaitPersisted(expected)
    }

    @Test
    fun `app to web response completes only the app to web request`() {
        repository.updateEntry(REQUEST_ID, TAG, APP) { it.copy(status = MessageStatus.ERROR) }

        val expected = mapOf(WEB to MessageStatus.IN_PROGRESS, APP to MessageStatus.ERROR)
        assertEquals(expected, repository.entries.value.statuses())
        awaitPersisted(expected)
    }

    @Test
    fun `overload without a direction still updates both directions`() {
        repository.updateEntry(REQUEST_ID, TAG) { it.copy(status = MessageStatus.SUCCESS) }

        val expected = mapOf(WEB to MessageStatus.SUCCESS, APP to MessageStatus.SUCCESS)
        assertEquals(expected, repository.entries.value.statuses())
        awaitPersisted(expected)
    }

    private companion object {
        const val REQUEST_ID = "r1"
        const val TAG = "bridge"
        val WEB = MessageDirection.WEB_TO_APP
        val APP = MessageDirection.APP_TO_WEB
    }
}
