package com.easyhooon.dari.desktop.app.update

import java.io.IOException
import java.util.concurrent.TimeUnit

internal object Processes {
    data class Result(val exitCode: Int, val output: String)

    fun run(vararg command: String, timeoutSeconds: Long = 120): Result {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        // Drain on another thread so a chatty command can't block the timeout.
        var output = ""
        val reader = Thread { output = process.inputStream.bufferedReader().readText() }.apply { start() }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IOException("${command.first()} timed out.")
        }
        reader.join()
        return Result(process.exitValue(), output)
    }

    fun check(vararg command: String) {
        val result = run(*command)
        if (result.exitCode != 0) throw IOException("${command.first().substringAfterLast('/')} failed: ${result.output.trim()}")
    }
}
