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

class FinishLinuxUpdateScriptTest {
    private val root: Path = Files.createTempDirectory("dari-finish-linux")
    private val target: Path = root.resolve("Dari")
    private val workspace: Path = root.resolve(".dari-update-test")
    private val script: Path = root.resolve("dari-finish-update-linux.sh").also { path ->
        javaClass.getResourceAsStream("/dari-finish-update-linux.sh")!!.use { Files.copy(it, path) }
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `swaps the staged folder in, launches it and removes the workspace`() {
        stage()
        val launches = root.resolve("launches")
        val recordingLauncher = root.resolve("launcher").also {
            Files.writeString(it, "#!/bin/sh\necho \"${'$'}1\" >> \"$launches\"\n")
            it.toFile().setExecutable(true)
        }
        assertEquals(0, finish(launcher = recordingLauncher.toString()))
        assertEquals("new", Files.readString(target.resolve("version")))
        assertFalse(Files.exists(workspace))
        assertEquals(listOf("$target/bin/Dari"), awaitLines(launches))
    }

    @Test
    fun `restores the previous folder when the new app fails to start`() {
        stage()
        assertEquals(1, finish(launcher = "/usr/bin/false"))
        assertEquals("old", Files.readString(target.resolve("version")))
        assertEquals("new", Files.readString(workspace.resolve("Dari/version")))
    }

    @Test
    fun `refuses a workspace it did not create`() {
        stage()
        val other = root.resolve("Documents").also { image(it.resolve("Dari"), "other") }
        assertEquals(2, finish(launcher = "/usr/bin/true", workspace = other))
        assertEquals("old", Files.readString(target.resolve("version")))
        assertTrue(Files.exists(other.resolve("Dari")))
    }

    private fun stage() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        image(target, "old")
        image(workspace.resolve("Dari"), "new")
    }

    private fun image(folder: Path, version: String) {
        Files.createDirectories(folder.resolve("bin"))
        Files.writeString(folder.resolve("version"), version)
        Files.writeString(folder.resolve("bin/Dari"), "#!/bin/sh\n").toFile().setExecutable(true)
    }

    // The script counts a launcher still running after the settle delay as launched and exits, so the
    // recording launcher may write after finish() returns.
    private fun awaitLines(file: Path): List<String> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!Files.exists(file) && System.nanoTime() < deadline) Thread.sleep(20)
        return Files.readAllLines(file)
    }

    // pid 999999 is not running, so the script doesn't wait.
    private fun finish(launcher: String, workspace: Path = this.workspace): Int {
        val process = ProcessBuilder("/bin/sh", script.toString(), "999999", target.toString(), workspace.toString())
            .redirectErrorStream(true)
            .apply {
                environment()["DARI_UPDATE_OPEN"] = launcher
                environment()["DARI_UPDATE_SETTLE_DELAY"] = "0.5"
            }
            .start()
        process.inputStream.readAllBytes()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        return process.exitValue()
    }
}
