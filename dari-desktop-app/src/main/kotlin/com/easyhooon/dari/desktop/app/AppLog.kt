package com.easyhooon.dari.desktop.app

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A plain-text log users can attach to bug reports. It records crashes and failures only, never
 * message contents. One file, rotated to `dari.log.1` at 1 MB.
 */
object AppLog {
    private const val MAX_BYTES = 1L * 1024 * 1024
    private val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    val directory: File = logDirectory()
    val file: File get() = File(directory, "dari.log")

    fun info(message: String) = write("INFO", message)

    fun error(message: String, error: Throwable? = null) {
        val trace = error?.let { StringWriter().also { out -> it.printStackTrace(PrintWriter(out)) }.toString() }
        write("ERROR", listOfNotNull(message, trace).joinToString("\n"))
    }

    /** Logs (and, when opted in, reports) any uncaught exception before the default handling, which may end the app. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            error("Uncaught exception on ${thread.name}", error)
            CrashReporting.capture(thread, error)
            previous?.uncaughtException(thread, error) ?: error.printStackTrace()
        }
    }

    @Synchronized
    private fun write(level: String, message: String) {
        // Logging must never take the app down with it.
        runCatching {
            directory.mkdirs()
            if (file.length() > MAX_BYTES) file.renameTo(File(directory, "dari.log.1"))
            file.appendText("${LocalDateTime.now().format(timestamp)} $level $message\n")
        }
    }

    private fun logDirectory(): File {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name")
        return when {
            os.startsWith("Mac") -> File(home, "Library/Logs/Dari")
            os.startsWith("Windows") -> File(System.getenv("LOCALAPPDATA") ?: home, "Dari/logs")
            else -> File(System.getenv("XDG_STATE_HOME") ?: "$home/.local/state", "dari")
        }
    }
}
