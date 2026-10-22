package com.easyhooon.dari.desktop.app.adb

import com.easyhooon.dari.inspector.protocol.InspectorProtocol
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** [name] is the device name set in Android settings (e.g. "Jihun's S25"), when there is one. */
data class AdbDevice(val serial: String, val model: String, val name: String? = null) {
    val title: String get() = name ?: model
}

/** Thin wrapper around the `adb` binary; every call blocks, so run it off the UI thread. */
class Adb internal constructor(private val runner: (List<String>) -> String) {

    constructor(executable: String) : this({ args -> runProcess(executable, args) })

    fun devices(): List<AdbDevice> {
        val attached = run("devices", "-l").lineSequence()
            .drop(1)
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size >= 2 && it[1] == "device" }
            .toList()
        // A detached serial can come back as another device, e.g. a different AVD on emulator-5554.
        details.keys.retainAll(attached.map { it[0] }.toSet())
        return attached.map { parts ->
            val model = parts.firstOrNull { it.startsWith("model:") }?.removePrefix("model:")?.replace('_', ' ')
            val info = details[parts[0]] ?: readDetails(parts[0]).also {
                // Retry next poll when getprop failed, e.g. right after attach.
                if (it.model != null) details[parts[0]] = it
            }
            AdbDevice(serial = parts[0], model = info.model ?: model ?: parts[0], name = info.name)
        }
    }

    // Read once per attach: name and model don't change while the device stays attached.
    private val details = ConcurrentHashMap<String, DeviceDetails>()

    private data class DeviceDetails(val name: String?, val model: String?)

    private fun readDetails(serial: String): DeviceDetails {
        fun read(command: String) = runCatching { run("-s", serial, "shell", command).trim() }.getOrNull()
            ?.takeIf { it.isNotEmpty() && it != "null" }
        val model = read("getprop ro.product.model")
        return DeviceDetails(name = read("settings get global device_name")?.takeIf { it != model }, model = model)
    }

    /** Application ids with a listening Dari socket. Empty when the device hides `/proc/net/unix`. */
    fun dariApps(serial: String): List<String> = runCatching { run("-s", serial, "shell", "cat /proc/net/unix") }
        .getOrDefault("")
        .let(::parseDariSockets)

    /** Forwards a free local port to the app's abstract socket and returns that port. */
    fun forward(serial: String, applicationId: String): Int {
        val socketName = InspectorProtocol.socketName(applicationId)
        val output = run("-s", serial, "forward", "tcp:0", "localabstract:$socketName")
        return parseForwardPort(output) ?: throw IOException("unexpected adb forward output: ${output.trim()}")
    }

    fun removeForward(serial: String, port: Int) {
        runCatching { run("-s", serial, "forward", "--remove", "tcp:$port") }
    }

    /** Drops every forward to a Dari socket, e.g. ones left behind when the app was killed. */
    fun removeDariForwards() {
        val forwards = runCatching { run("forward", "--list") }.getOrDefault("")
        parseDariForwards(forwards).forEach { (serial, local) ->
            runCatching { run("-s", serial, "forward", "--remove", local) }
        }
    }

    private fun run(vararg args: String): String = runner(args.toList())

    companion object {
        private const val TIMEOUT_SECONDS = 10L

        private fun runProcess(executable: String, args: List<String>): String {
            val process = ProcessBuilder(listOf(executable) + args).redirectErrorStream(true).start()
            // Drain output on another thread: reading it here would block until adb exits, so a hung adb
            // (e.g. an unauthorized device) would never reach the timeout below.
            val output = StringBuilder()
            val reader = thread(isDaemon = true) {
                runCatching { output.append(process.inputStream.bufferedReader(Charsets.UTF_8).readText()) }
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IOException("adb ${args.joinToString(" ")} timed out")
            }
            reader.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))
            val text = output.toString()
            if (process.exitValue() != 0) throw IOException(text.trim().ifEmpty { "adb exited with ${process.exitValue()}" })
            return text
        }

        // Homebrew's `android-platform-tools`; apps opened from Finder don't inherit the shell's PATH.
        // macOS only: on Windows `/opt/...` maps to `C:\opt\...`, which any user can create.
        internal val HOMEBREW_BIN_DIRS =
            if (System.getProperty("os.name").startsWith("Mac")) listOf("/opt/homebrew/bin", "/usr/local/bin") else emptyList()

        /** `ANDROID_HOME`/`ANDROID_SDK_ROOT`, the default SDK locations, `PATH`, then Homebrew. */
        fun locate(env: Map<String, String> = System.getenv()): String? {
            val exe = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
            val home = System.getProperty("user.home")
            val sdkRoots = listOfNotNull(
                env["ANDROID_HOME"],
                env["ANDROID_SDK_ROOT"],
                "$home/Library/Android/sdk",
                "$home/Android/Sdk",
                env["LOCALAPPDATA"]?.let { "$it/Android/Sdk" },
            )
            val candidates = sdkRoots.map { File(it, "platform-tools/$exe") } +
                env["PATH"].orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }.map { File(it, exe) } +
                HOMEBREW_BIN_DIRS.map { File(it, exe) }
            return candidates.firstOrNull { it.canExecute() }?.absolutePath
        }

        // stderr is merged in, so daemon notices (e.g. "* daemon not running; starting now") can precede the port.
        internal fun parseForwardPort(output: String): Int? = output.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toIntOrNull()

        // `adb forward --list` lines: `<serial> tcp:<port> localabstract:dari_<applicationId>`.
        fun parseDariForwards(list: String): List<Pair<String, String>> = list.lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size == 3 && it[2].startsWith("localabstract:" + InspectorProtocol.SOCKET_NAME_PREFIX) }
            .map { it[0] to it[1] }
            .toList()

        // Abstract sockets appear as `@dari_<applicationId>` in the last column.
        fun parseDariSockets(procNetUnix: String): List<String> = procNetUnix.lineSequence()
            .mapNotNull { line -> line.trim().split(Regex("\\s+")).lastOrNull() }
            .filter { it.startsWith("@" + InspectorProtocol.SOCKET_NAME_PREFIX) }
            .map { it.removePrefix("@" + InspectorProtocol.SOCKET_NAME_PREFIX) }
            .distinct()
            .sorted()
            .toList()
    }
}
