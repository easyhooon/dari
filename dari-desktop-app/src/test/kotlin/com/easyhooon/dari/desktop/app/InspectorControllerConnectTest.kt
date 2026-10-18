package com.easyhooon.dari.desktop.app

import com.easyhooon.dari.desktop.app.client.InspectorConnection
import com.easyhooon.dari.inspector.protocol.AppInfo
import com.easyhooon.dari.inspector.protocol.ClientHello
import com.easyhooon.dari.inspector.protocol.DetailInput
import com.easyhooon.dari.inspector.protocol.FrameReader
import com.easyhooon.dari.inspector.protocol.FrameWriter
import com.easyhooon.dari.inspector.protocol.InspectorError
import com.easyhooon.dari.inspector.protocol.InspectorErrorCode
import com.easyhooon.dari.inspector.protocol.InspectorFrame
import com.easyhooon.dari.inspector.protocol.InspectorOperations
import com.easyhooon.dari.inspector.protocol.InspectorProtocol
import com.easyhooon.dari.inspector.protocol.InspectorSettings
import com.easyhooon.dari.inspector.protocol.ListOutput
import com.easyhooon.dari.inspector.protocol.SearchInput
import com.easyhooon.dari.inspector.protocol.SearchOutput
import com.easyhooon.dari.inspector.protocol.ServerHello
import com.easyhooon.dari.inspector.protocol.WireMessageDetail
import com.easyhooon.dari.inspector.protocol.WireMessageSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Session lifecycle against a fake app on a local socket: cancelled connects, dropped sessions, masking changes. */
class InspectorControllerConnectTest {
    private val dispatcher: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private val app = FakeApp()

    // The first open() stands in for a frozen app: it blocks until released, then fails or succeeds.
    private val firstOpenStarted = CountDownLatch(1)
    private val releaseFirstOpen = CountDownLatch(1)
    private val opens = AtomicInteger()
    private val releaseReopen = CountDownLatch(1)

    @Volatile
    private var masking = false

    @AfterTest
    fun tearDown() {
        releaseFirstOpen.countDown()
        releaseReopen.countDown()
        scope.cancel()
        app.close()
        dispatcher.close()
    }

    private fun controller(first: () -> InspectorConnection) = sessionController { attempt ->
        if (attempt == 0) {
            firstOpenStarted.countDown()
            releaseFirstOpen.await()
            first()
        } else {
            app.connect()
        }
    }

    private fun sessionController(open: (attempt: Int) -> InspectorConnection = { app.connect() }) = InspectorController(
        scope,
        adb = null,
        clientVersion = "test",
        preferredSerial = "serial",
        maskingEnabled = { masking },
        openConnection = { _, _ -> open(opens.getAndIncrement()) },
    ).apply { applicationId = APP.applicationId }

    // Suspends rather than blocks: the connect job needs this test's thread to start.
    private suspend fun awaitFirstOpen() = withTimeout(5_000) { while (firstOpenStarted.count > 0) delay(10) }

    private suspend fun InspectorController.awaitState(predicate: (ConnectionState) -> Boolean) =
        withTimeout(5_000) { while (!predicate(state)) delay(10) }

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) delay(10) }

    @Test
    fun `a handshake timeout from a cancelled connect does not overwrite the new session`(): Unit = runBlocking(dispatcher) {
        val controller = controller { throw IOException("app is in the background") }
        controller.connect()
        awaitFirstOpen()
        // Picking the app again in the title bar restarts the connect job.
        controller.connect()
        controller.awaitState { it is ConnectionState.Connected }

        releaseFirstOpen.countDown()
        delay(300)
        assertIs<ConnectionState.Connected>(controller.state)
    }

    @Test
    fun `a handshake timeout after disconnecting leaves the state idle`(): Unit = runBlocking(dispatcher) {
        val controller = controller { throw IOException("app is in the background") }
        controller.connect()
        awaitFirstOpen()
        controller.disconnectByUser()

        releaseFirstOpen.countDown()
        delay(300)
        assertEquals(ConnectionState.Idle, controller.state)
    }

    @Test
    fun `a connection opened after its connect was cancelled is closed`(): Unit = runBlocking(dispatcher) {
        val controller = controller { app.connect() }
        controller.connect()
        awaitFirstOpen()
        controller.disconnectByUser()

        releaseFirstOpen.countDown()
        withTimeout(5_000) { while (app.closed.get() < 1) delay(10) }
        assertEquals(ConnectionState.Idle, controller.state)
        assertEquals(1, opens.get())
    }

    @Test
    fun `a dropped session shows the reason until it reconnects`(): Unit = runBlocking(dispatcher) {
        val controller = sessionController()
        controller.connect()
        controller.awaitState { it is ConnectionState.Connected }

        app.dropSessions()
        controller.awaitState { it is ConnectionState.Connecting }
        assertEquals(ConnectionState.Connecting("Disconnected from ${APP.applicationId}"), controller.state)
        controller.awaitState { it is ConnectionState.Connected }
        assertEquals(2, opens.get())
    }

    @Test
    fun `a masking change refetches the open detail under the new setting`(): Unit = runBlocking(dispatcher) {
        app.messages = listOf(MESSAGE)
        val controller = sessionController()
        controller.connect()
        awaitUntil { controller.detail?.requestData == SECRET }

        masking = true
        controller.onMaskingChanged()
        awaitUntil { controller.detail?.requestData == MASKED }
        assertEquals(1, opens.get())
    }

    @Test
    fun `a failed masking change drops the open detail and reconnects`(): Unit = runBlocking(dispatcher) {
        app.messages = listOf(MESSAGE)
        // Holds the reconnect so the dropped detail is visible before the new session refills it.
        val controller = sessionController { attempt ->
            if (attempt > 0) releaseReopen.await()
            app.connect()
        }
        controller.connect()
        awaitUntil { controller.detail != null }

        app.failNextSettings.set(true)
        masking = true
        controller.onMaskingChanged()
        awaitUntil { opens.get() == 2 }
        assertEquals("Couldn't change masking, reconnecting", controller.notice)
        assertNull(controller.detail)

        releaseReopen.countDown()
        awaitUntil { controller.detail?.requestData == MASKED }
        assertIs<ConnectionState.Connected>(controller.state)
    }

    @Test
    fun `a masking change failing after a disconnect does not reconnect`(): Unit = runBlocking(dispatcher) {
        app.messages = listOf(MESSAGE)
        val controller = sessionController()
        controller.connect()
        awaitUntil { controller.detail != null }

        app.holdNextSettings.set(true)
        controller.onMaskingChanged()
        awaitUntil { app.settingsHeld.get() }
        // Closing the socket fails the pending request once the session is already gone.
        controller.disconnectByUser()
        // Disconnecting keeps the detail, so its drop shows the failure was handled.
        awaitUntil { controller.detail == null }

        delay(300)
        assertNull(controller.notice)
        assertEquals(ConnectionState.Idle, controller.state)
        assertEquals(1, opens.get())
    }

    @Test
    fun `the filter matches payloads through the app, masked when masking is on`(): Unit = runBlocking(dispatcher) {
        app.messages = listOf(MESSAGE)
        val controller = sessionController()
        controller.connect()
        controller.awaitState { it is ConnectionState.Connected }

        controller.filter = SECRET
        awaitUntil { controller.visibleMessages() == listOf(MESSAGE) }

        masking = true
        controller.onMaskingChanged()
        awaitUntil { controller.visibleMessages().isEmpty() }
    }

    @Test
    fun `an app without search still filters by summary fields`(): Unit = runBlocking(dispatcher) {
        app.messages = listOf(MESSAGE)
        app.searchSupported = false
        val controller = sessionController()
        controller.connect()
        controller.awaitState { it is ConnectionState.Connected }

        controller.filter = SECRET
        awaitUntil { controller.appLacksPayloadSearch }
        assertEquals(emptyList(), controller.visibleMessages())
        controller.filter = "gettoken"
        assertEquals(listOf(MESSAGE), controller.visibleMessages())
        assertIs<ConnectionState.Connected>(controller.state)

        // A reconnect may reach an updated app, so the hint starts over.
        controller.disconnect()
        assertFalse(controller.appLacksPayloadSearch)
    }

    @Test
    fun `an app with search is not flagged as lacking it`(): Unit = runBlocking(dispatcher) {
        app.messages = listOf(MESSAGE)
        val controller = sessionController()
        controller.connect()
        controller.awaitState { it is ConnectionState.Connected }

        controller.filter = SECRET
        awaitUntil { controller.visibleMessages() == listOf(MESSAGE) }
        assertFalse(controller.appLacksPayloadSearch)
    }

    /** Answers like a foreground app: hello, settings.update, list, detail, search, and an observe stream that stays open. */
    private class FakeApp : Closeable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val clients = ConcurrentLinkedQueue<Socket>()

        /** Connections closed by the desktop (forward removals in the real app). */
        val closed = AtomicInteger()

        /** Returned by messages.list. Details carry [SECRET], or [MASKED] once masking is on for that connection. */
        @Volatile
        var messages: List<WireMessageSummary> = emptyList()

        /** False answers messages.search like an app from before it existed. */
        @Volatile
        var searchSupported = true

        /** Answers the next settings.update with an error. */
        val failNextSettings = AtomicBoolean()

        /** Leaves the next settings.update unanswered; [settingsHeld] turns true once it arrives. */
        val holdNextSettings = AtomicBoolean()
        val settingsHeld = AtomicBoolean()

        init {
            thread(isDaemon = true) {
                while (true) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { runCatching { serve(client) } }
                }
            }
        }

        fun connect(): InspectorConnection =
            InspectorConnection.handshake(Socket(server.inetAddress, server.localPort), APP.applicationId, "test") {
                closed.incrementAndGet()
            }

        /** Closes every session from the app side, as when the app process dies. */
        fun dropSessions() = clients.forEach { runCatching { it.close() } }

        private fun serve(client: Socket) = client.use {
            clients += it
            val reader = FrameReader(it.getInputStream())
            val writer = FrameWriter(it.getOutputStream())
            reader.read(ClientHello.serializer()) ?: return
            writer.write(ServerHello.serializer(), ServerHello(accepted = true, protocolVersion = InspectorProtocol.VERSION, app = APP))
            val masking = AtomicBoolean()
            while (true) {
                val frame = reader.read(InspectorFrame.serializer()) ?: return
                if (frame !is InspectorFrame.Invoke || frame.op == InspectorOperations.OBSERVE) continue
                answer(frame, masking)?.let { result -> writer.write(InspectorFrame.serializer(), result) }
            }
        }

        /** Null leaves the request unanswered. [masking] is this connection's setting. */
        private fun answer(frame: InspectorFrame.Invoke, masking: AtomicBoolean): InspectorFrame.Result? {
            val json = InspectorProtocol.json
            return when (frame.op) {
                InspectorOperations.LIST ->
                    InspectorFrame.Result(frame.requestId, json.encodeToJsonElement(ListOutput.serializer(), ListOutput(messages)))
                InspectorOperations.DETAIL -> {
                    val id = json.decodeFromJsonElement(DetailInput.serializer(), frame.input).id
                    val detail = WireMessageDetail(messages.first { it.id == id }, requestData = if (masking.get()) MASKED else SECRET)
                    InspectorFrame.Result(frame.requestId, json.encodeToJsonElement(WireMessageDetail.serializer(), detail))
                }
                InspectorOperations.SETTINGS_UPDATE -> when {
                    holdNextSettings.getAndSet(false) -> null.also { settingsHeld.set(true) }
                    failNextSettings.getAndSet(false) ->
                        InspectorFrame.Result(frame.requestId, error = InspectorError(InspectorErrorCode.PROVIDER_FAILED, "settings unavailable"))
                    else -> {
                        masking.set(json.decodeFromJsonElement(InspectorSettings.serializer(), frame.input).maskingEnabled)
                        InspectorFrame.Result(frame.requestId, frame.input)
                    }
                }
                InspectorOperations.SEARCH -> if (searchSupported) {
                    val query = json.decodeFromJsonElement(SearchInput.serializer(), frame.input).query
                    val payload = if (masking.get()) MASKED else SECRET
                    val ids = messages.filter { payload.contains(query, ignoreCase = true) }.map { it.id }
                    InspectorFrame.Result(frame.requestId, json.encodeToJsonElement(SearchOutput.serializer(), SearchOutput(ids)))
                } else {
                    InspectorFrame.Result(frame.requestId, error = InspectorError(InspectorErrorCode.OPERATION_NOT_FOUND, "Unknown op"))
                }
                else -> InspectorFrame.Result(frame.requestId, frame.input)
            }
        }

        override fun close() = server.close()
    }

    private companion object {
        val APP = AppInfo(
            applicationId = "com.example.app",
            appName = "Example",
            appVersion = "1.0",
            dariVersion = "2.2.1",
            deviceModel = "Pixel",
            sdkInt = 37,
        )
        val MESSAGE = WireMessageSummary(
            id = 1,
            handlerName = "getToken",
            direction = "WEB_TO_APP",
            status = "SUCCESS",
            requestTimestamp = 1,
            requestSizeBytes = 0,
            responseSizeBytes = 0,
        )
        const val SECRET = "token-123"
        const val MASKED = "***"
    }
}
