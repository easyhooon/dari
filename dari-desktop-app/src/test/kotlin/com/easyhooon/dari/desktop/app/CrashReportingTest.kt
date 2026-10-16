package com.easyhooon.dari.desktop.app

import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.protocol.User
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReportingTest {
    private val sent = mutableListOf<SentryEnvelope>()
    private lateinit var options: SentryOptions

    @AfterTest
    fun tearDown() {
        CrashReporting.update(optedIn = false, release = "test", dsn = null)
        Sentry.close()
    }

    @Test
    fun `runs only when opted in and the build has a DSN`() {
        assertFalse(CrashReporting.update(optedIn = false, release = "2.2.1", dsn = DSN))
        assertFalse(CrashReporting.update(optedIn = true, release = "2.2.1", dsn = null))
        assertFalse(CrashReporting.update(optedIn = true, release = "2.2.1", dsn = " "))
        assertFalse(Sentry.isEnabled())

        assertTrue(CrashReporting.update(optedIn = true, release = "2.2.1", dsn = DSN))
        assertTrue(Sentry.isEnabled())

        assertFalse(CrashReporting.update(optedIn = false, release = "2.2.1", dsn = DSN))
        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `a failed start leaves reporting off`() {
        val failingStart: (String, String) -> Unit = { _, _ -> throw IllegalStateException("init failed") }
        assertFalse(CrashReporting.update(optedIn = true, release = "2.2.1", dsn = DSN, start = failingStart))
        assertFalse(CrashReporting.isRunning)
    }

    @Test
    fun `a failed stop leaves reporting on`() {
        assertTrue(CrashReporting.update(optedIn = true, release = "2.2.1", dsn = DSN))
        val failingStop: () -> Unit = { throw IllegalStateException("close failed") }
        assertTrue(CrashReporting.update(optedIn = false, release = "2.2.1", dsn = DSN, stop = failingStop))
        assertTrue(CrashReporting.isRunning)
    }

    @Test
    fun `capture is a no-op while off`() {
        CrashReporting.capture(Thread.currentThread(), RuntimeException("boom"))
        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `sends only exception types, stack traces and environment tags`() {
        startWithFakeTransport()
        Sentry.addBreadcrumb("PAYLOAD breadcrumb")
        Sentry.setExtra("bridge", "PAYLOAD extra")
        Sentry.setUser(User().apply { email = "PAYLOAD@example.com" })
        Sentry.configureScope { it.setContexts("bridge", "PAYLOAD context") }

        val error = RuntimeException("""PAYLOAD {"token":"secret"}""", IllegalStateException("PAYLOAD cause"))
        SentryReporter.capture(Thread.currentThread(), error)

        val json = sent.single().toJson()
        assertFalse("PAYLOAD" in json, json)
        assertFalse(InetAddress.getLocalHost().hostName in json, json)
        assertFalse(""""modules"""" in json, "bundled library versions are dropped")
        assertTrue(""""type":"RuntimeException"""" in json, json)
        assertTrue(""""type":"IllegalStateException"""" in json, json)
        assertTrue(""""handled":false""" in json, json)
        assertTrue(""""release":"2.2.1"""" in json, json)
        assertTrue("CrashReportingTest" in json, "stack trace is kept: $json")
        IssueReport.environmentTags().forEach { (key, value) -> assertTrue(""""$key":"$value"""" in json, json) }
    }

    @Test
    fun `nothing is sent when scrubbing fails`() {
        val failingStrip: (SentryEvent) -> Unit = { throw IllegalStateException("unexpected") }
        assertNull(SentryReporter.scrub(SentryEvent(), failingStrip))

        startWithFakeTransport { options ->
            options.setBeforeSend { event, _ -> SentryReporter.scrub(event, failingStrip) }
        }
        SentryReporter.capture(Thread.currentThread(), RuntimeException("PAYLOAD"))
        assertEquals(emptyList(), sent)
    }

    private fun startWithFakeTransport(customize: (SentryOptions) -> Unit = {}) {
        Sentry.init { options ->
            SentryReporter.configure(options, DSN, "2.2.1")
            options.setTransportFactory { _, _ -> RecordingTransport(sent) }
            customize(options)
            this.options = options
        }
    }

    private fun SentryEnvelope.toJson(): String =
        ByteArrayOutputStream().also { options.serializer.serialize(this, it) }.toString(Charsets.UTF_8)

    private class RecordingTransport(private val sent: MutableList<SentryEnvelope>) : ITransport {
        override fun send(envelope: SentryEnvelope, hint: Hint) {
            sent += envelope
        }

        override fun flush(timeoutMillis: Long) = Unit

        override fun getRateLimiter(): RateLimiter? = null

        override fun close(isRestarting: Boolean) = Unit

        override fun close() = Unit
    }

    private companion object {
        // Never contacted: the fake transport or the off switch stops events first.
        const val DSN = "http://key@127.0.0.1:9/1"
    }
}
