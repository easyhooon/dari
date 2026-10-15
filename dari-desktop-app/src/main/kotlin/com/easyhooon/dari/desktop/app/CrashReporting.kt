package com.easyhooon.dari.desktop.app

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import java.util.Properties

/**
 * Crash reports, on by default and turned off in Settings. Only uncaught exceptions are sent, reduced to
 * their types, stack traces and OS/arch/Java tags, since Dari shows bridge payloads that may hold end-user data.
 */
object CrashReporting {
    /** Baked in at build time (-PdariSentryDsn / DARI_SENTRY_DSN); blank in local and fork builds. */
    private val dsn: String? by lazy {
        javaClass.getResourceAsStream("/dari-crash-reporting.properties")
            ?.use { stream -> Properties().apply { load(stream) }.getProperty("dsn") }
    }

    @Volatile
    var isRunning = false
        private set

    val isAvailable: Boolean get() = !dsn.isNullOrBlank()

    /** Starts or stops reporting to match the setting; returns whether it is now running. */
    fun update(optedIn: Boolean, release: String): Boolean = update(optedIn, release, dsn)

    @Synchronized
    internal fun update(optedIn: Boolean, release: String, dsn: String?): Boolean {
        val run = optedIn && !dsn.isNullOrBlank()
        if (run == isRunning) return run
        runCatching { if (run) SentryReporter.start(dsn!!, release) else SentryReporter.stop() }
            .onSuccess { isRunning = run }
            .onFailure { AppLog.error("Could not ${if (run) "start" else "stop"} crash reporting", it) }
        return isRunning
    }

    /** Called from the uncaught-exception handler; never throws. */
    fun capture(thread: Thread, error: Throwable) {
        if (isRunning) runCatching { SentryReporter.capture(thread, error) }
    }
}

/** Every Sentry call lives here, so the SDK loads only once reporting is on. */
internal object SentryReporter {
    private const val FLUSH_TIMEOUT_MILLIS = 2_000L

    fun start(dsn: String, release: String) = Sentry.init { configure(it, dsn, release) }

    fun stop() = Sentry.close()

    fun capture(thread: Thread, error: Throwable) {
        // Marked unhandled, as Sentry's own handler would, so it counts as a crash.
        val mechanism = Mechanism().apply {
            type = "UncaughtExceptionHandler"
            isHandled = false
        }
        Sentry.captureException(ExceptionMechanismException(mechanism, error, thread))
        // The app may end right after the handler returns.
        Sentry.flush(FLUSH_TIMEOUT_MILLIS)
    }

    fun configure(options: SentryOptions, dsn: String, release: String) = options.apply {
        this.dsn = dsn
        this.release = release
        // Ignore sentry.properties and SENTRY_* variables on the user's machine.
        isEnableExternalConfiguration = false
        // AppLog's handler captures crashes; a second handler would wrap it again on every re-enable.
        isEnableUncaughtExceptionHandler = false
        isSendDefaultPii = false
        // Host names often contain the user's name.
        isAttachServerName = false
        isEnableAutoSessionTracking = false
        isSendClientReports = false
        maxBreadcrumbs = 0
        setBeforeBreadcrumb { _, _ -> null }
        setBeforeSend { event, _ -> scrub(event) }
        addInAppInclude("com.easyhooon.dari")
        IssueReport.environmentTags().forEach(::setTag)
    }

    /**
     * Keeps exception types, stack traces and tags. Messages, breadcrumbs, extras and contexts may
     * carry bridge payloads, so they are dropped. Fails closed: an event that cannot be scrubbed is not sent.
     */
    fun scrub(event: SentryEvent, strip: (SentryEvent) -> Unit = ::strip): SentryEvent? =
        runCatching { event.also(strip) }.getOrNull()

    private fun strip(event: SentryEvent) {
        event.apply {
            message = null
            breadcrumbs = null
            extras = null
            user = null
            request = null
            serverName = null
            threads = null
            setModules(null)
            unknown = null
            contexts.keys().toList().forEach(contexts::remove)
            exceptions?.forEach { exception ->
                exception.value = null
                exception.mechanism?.apply {
                    description = null
                    data = null
                    meta = null
                }
            }
        }
    }
}
