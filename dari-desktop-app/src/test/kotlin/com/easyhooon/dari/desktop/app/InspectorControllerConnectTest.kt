package com.easyhooon.dari.desktop.app

import com.easyhooon.dari.desktop.app.client.InspectorConnection
import com.easyhooon.dari.inspector.protocol.AppInfo
import com.easyhooon.dari.inspector.protocol.ClientHello
import com.easyhooon.dari.inspector.protocol.FrameReader
import com.easyhooon.dari.inspector.protocol.FrameWriter
import com.easyhooon.dari.inspector.protocol.InspectorFrame
import com.easyhooon.dari.inspector.protocol.InspectorOperations
import com.easyhooon.dari.inspector.protocol.InspectorProtocol
import com.easyhooon.dari.inspector.protocol.ListOutput
import com.easyhooon.dari.inspector.protocol.ServerHello
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A connect job cancelled while open() blocks (frozen app) must neither touch state nor leak its connection. */
class InspectorControllerConnectTest {
    private val dispatcher: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private val app = FakeApp()

    // The first open() stands in for a frozen app: it blocks until released, then fails or succeeds.
    private val firstOpenStarted = CountDownLatch(1)
    private val releaseFirstOpen = CountDownLatch(1)
    private val opens = AtomicInteger()

    @AfterTest
    fun tearDown() {
        releaseFirstOpen.countDown()
        scope.cancel()
        app.close()
        dispatcher.close()
    }

    private fun controller(first: () -> InspectorConnection) = InspectorController(
        scope,
        adb = null,
        clientVersion = "test",
        preferredSerial = "serial",
        openConnection = { _, _ ->
            if (opens.getAndIncrement() == 0) {
                firstOpenStarted.countDown()
                releaseFirstOpen.await()
                first()
            } else {
                app.connect()
            }
        },
    ).apply { applicationId = APP.applicationId }

    // Suspends rather than blocks: the connect job needs this test's thread to start.
    private suspend fun awaitFirstOpen() = withTimeout(5_000) { while (firstOpenStarted.count > 0) delay(10) }

    private suspend fun InspectorController.awaitState(predicate: (ConnectionState) -> Boolean) =
        withTimeout(5_000) { while (!predicate(state)) delay(10) }

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

    /** Answers like a foreground app: hello, settings.update, an empty list, and an observe stream that stays open. */
    private class FakeApp : Closeable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())

        /** Connections closed by the desktop (forward removals in the real app). */
        val closed = AtomicInteger()

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

        private fun serve(client: Socket) = client.use {
            val reader = FrameReader(it.getInputStream())
            val writer = FrameWriter(it.getOutputStream())
            reader.read(ClientHello.serializer()) ?: return
            writer.write(ServerHello.serializer(), ServerHello(accepted = true, protocolVersion = InspectorProtocol.VERSION, app = APP))
            while (true) {
                val frame = reader.read(InspectorFrame.serializer()) ?: return
                if (frame !is InspectorFrame.Invoke || frame.op == InspectorOperations.OBSERVE) continue
                val output = when (frame.op) {
                    InspectorOperations.LIST -> InspectorProtocol.json.encodeToJsonElement(ListOutput.serializer(), ListOutput(emptyList()))
                    else -> frame.input
                }
                writer.write(InspectorFrame.serializer(), InspectorFrame.Result(frame.requestId, output))
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
    }
}
