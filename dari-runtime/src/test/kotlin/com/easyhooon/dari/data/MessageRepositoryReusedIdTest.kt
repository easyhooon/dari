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

/** Bridges that reuse a requestId, e.g. a callback name, across calls. */
@RunWith(RobolectricTestRunner::class)
class MessageRepositoryReusedIdTest {

    private lateinit var database: DariDatabase
    private lateinit var repository: MessageRepository

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DariDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = MessageRepository(database).apply { initialized.await() }
    }

    @After
    fun tearDown() {
        // Close only the scope; closing the database could race pending DAO writes.
        repository.close()
    }

    private fun request(status: MessageStatus = MessageStatus.IN_PROGRESS) = MessageEntry(
        requestId = REQUEST_ID,
        handlerName = "getToken",
        direction = MessageDirection.WEB_TO_APP,
        tag = TAG,
        status = status,
    )

    // Same fields DefaultDariInterceptor sets on a response.
    private fun respond(data: String, status: MessageStatus = MessageStatus.SUCCESS) =
        repository.updateEntry(REQUEST_ID, TAG, MessageDirection.WEB_TO_APP) {
            it.copy(responseData = data, status = status, responseTimestamp = System.currentTimeMillis())
        }

    private fun List<MessageEntry>.responses() = map { it.status to it.responseData }

    // DB writes run on the repository's own scope, so wait for them to settle.
    private fun awaitPersisted(expected: List<Pair<MessageStatus, String?>>) = runBlocking {
        withTimeout(5_000) {
            while (database.messageDao().getAll().map { it.status to it.responseData } != expected) delay(10)
        }
    }

    @Test
    fun `each response completes only its own call`() {
        repository.addEntry(request())
        respond("first", MessageStatus.ERROR)
        repository.addEntry(request())
        respond("second")

        val expected = listOf(MessageStatus.ERROR to "first", MessageStatus.SUCCESS to "second")
        assertEquals(expected, repository.entries.value.responses())
        awaitPersisted(expected)
    }

    @Test
    fun `a request that never got a response doesn't take the next call's response`() {
        repository.addEntry(request())
        repository.addEntry(request())
        respond("second")

        val expected = listOf(MessageStatus.IN_PROGRESS to null, MessageStatus.SUCCESS to "second")
        assertEquals(expected, repository.entries.value.responses())
        awaitPersisted(expected)
    }

    @Test
    fun `a late duplicate response leaves completed calls alone`() {
        repository.addEntry(request())
        respond("first")
        repository.addEntry(request())
        respond("second")
        respond("late")

        val expected = listOf(MessageStatus.SUCCESS to "first", MessageStatus.SUCCESS to "second")
        assertEquals(expected, repository.entries.value.responses())
        awaitPersisted(expected)
    }

    @Test
    fun `a fire-and-forget entry still records a response that arrives`() {
        repository.addEntry(request(MessageStatus.SUCCESS))
        respond("answer")

        val expected = listOf(MessageStatus.SUCCESS to "answer")
        assertEquals(expected, repository.entries.value.responses())
        awaitPersisted(expected)
    }

    @Test
    fun `a call left open by a previous session doesn't take the new call's response`() = runBlocking {
        repository.addEntry(request())
        awaitPersisted(listOf(MessageStatus.IN_PROGRESS to null))
        repository.close()
        repository = MessageRepository(database).apply { initialized.await() }

        repository.addEntry(request())
        respond("new")

        val expected = listOf(MessageStatus.IN_PROGRESS to null, MessageStatus.SUCCESS to "new")
        assertEquals(expected, repository.entries.value.responses())
        awaitPersisted(expected)
    }

    private companion object {
        const val REQUEST_ID = "onTokenResult"
        const val TAG = "bridge"
    }
}
