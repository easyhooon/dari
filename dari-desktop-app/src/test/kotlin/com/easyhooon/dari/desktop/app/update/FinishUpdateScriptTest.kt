package com.easyhooon.dari.desktop.app.update

import org.junit.Assume.assumeFalse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinishUpdateScriptTest {
    private val root: Path = Files.createTempDirectory("dari-finish")
    private val target: Path = root.resolve("Dari.app")
    private val workspace: Path = root.resolve(".dari-update-test")
    private val script: Path = root.resolve("dari-finish-update.sh").also { path ->
        javaClass.getResourceAsStream("/dari-finish-update.sh")!!.use { Files.copy(it, path) }
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `swaps the staged contents in and removes the workspace`() {
        stage()
        assertEquals(0, finish(open = "/usr/bin/true"))
        assertEquals("new", Files.readString(target.resolve("Contents/version")))
        assertFalse(Files.exists(workspace))
    }

    @Test
    fun `restores the previous contents when the new app cannot open`() {
        stage()
        assertEquals(1, finish(open = "/usr/bin/false"))
        assertEquals("old", Files.readString(target.resolve("Contents/version")))
        assertEquals("new", Files.readString(workspace.resolve("Contents/version")))
    }

    @Test
    fun `retries the relaunch while LaunchServices still sees the old process`() {
        stage()
        // Fails twice with -600-like errors, then succeeds; records its arguments.
        val calls = root.resolve("open-calls")
        val flakyOpen = root.resolve("flaky-open").also {
            Files.writeString(
                it,
                """
                #!/bin/sh
                echo "${'$'}*" >> "$calls"
                [ "${'$'}(wc -l < "$calls")" -gt 2 ]
                """.trimIndent(),
            )
            it.toFile().setExecutable(true)
        }
        assertEquals(0, finish(open = flakyOpen.toString()))
        assertEquals("new", Files.readString(target.resolve("Contents/version")))
        val opens = Files.readAllLines(calls)
        assertEquals(3, opens.size)
        assertTrue(opens.all { it == "-n $target" })
    }

    @Test
    fun `refuses a workspace it did not create`() {
        stage()
        val other = root.resolve("Documents").also { Files.createDirectories(it.resolve("Contents")) }
        assertEquals(2, finish(open = "/usr/bin/true", workspace = other))
        assertEquals("old", Files.readString(target.resolve("Contents/version")))
        assertTrue(Files.exists(other.resolve("Contents")))
    }

    private fun stage() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        Files.createDirectories(target.resolve("Contents"))
        Files.writeString(target.resolve("Contents/version"), "old")
        Files.createDirectories(workspace.resolve("Contents"))
        Files.writeString(workspace.resolve("Contents/version"), "new")
    }

    // pid 999999 is not running, so the script doesn't wait.
    private fun finish(open: String, workspace: Path = this.workspace): Int {
        val process = ProcessBuilder("/bin/sh", script.toString(), "999999", target.toString(), workspace.toString())
            .redirectErrorStream(true)
            .apply {
                environment()["DARI_UPDATE_OPEN"] = open
                environment()["DARI_UPDATE_RETRY_DELAY"] = "0"
            }
            .start()
        process.inputStream.readAllBytes()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        return process.exitValue()
    }
}
