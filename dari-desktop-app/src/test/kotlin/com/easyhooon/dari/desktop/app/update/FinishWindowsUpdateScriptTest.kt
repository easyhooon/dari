package com.easyhooon.dari.desktop.app.update

import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs on Windows only, which CI covers with windows-latest. */
class FinishWindowsUpdateScriptTest {
    private val root: Path = Files.createTempDirectory("dari-finish-windows")
    private val target: Path = root.resolve("Dari")
    private val workspace: Path = root.resolve(".dari-update-test")
    private val script: Path = root.resolve("dari-finish-update.ps1").also { path ->
        javaClass.getResourceAsStream("/dari-finish-update.ps1")!!.use { Files.copy(it, path) }
    }
    private val calls: Path = root.resolve("calls")

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `installs the staged MSI, relaunches Dari and removes the workspace`() {
        stage()
        assertEquals(0, finish(msiexecExit = 0))
        val lines = Files.readAllLines(calls).map { it.trim() }
        assertTrue(lines[0].startsWith("msiexec /i \"${workspace.resolve("Dari.msi")}\" /qn /norestart"), lines[0])
        assertEquals("launch", lines[1])
        assertFalse(Files.exists(workspace))
    }

    @Test
    fun `relaunches the installed version and keeps the log when the MSI fails`() {
        stage()
        assertEquals(1, finish(msiexecExit = 1603))
        assertEquals(listOf("launch"), Files.readAllLines(calls).drop(1).map { it.trim() })
        assertTrue(Files.exists(workspace.resolve("Dari.msi")))
    }

    @Test
    fun `refuses a workspace it did not create`() {
        stage()
        val other = Files.createDirectories(root.resolve("Documents"))
        Files.writeString(other.resolve("Dari.msi"), "msi")
        assertEquals(2, finish(msiexecExit = 0, workspace = other))
        assertFalse(Files.exists(calls))
    }

    private fun stage() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        Files.createDirectories(target)
        Files.createDirectories(workspace)
        Files.writeString(workspace.resolve("Dari.msi"), "msi")
    }

    private fun fake(name: String, body: String): Path = root.resolve(name).also { Files.writeString(it, "@echo off\r\n$body\r\n") }

    // pid 999999 is not running, so the script doesn't wait.
    private fun finish(msiexecExit: Int, workspace: Path = this.workspace): Int {
        val msiexec = fake("msiexec.cmd", "echo msiexec %*>> \"$calls\"\r\nexit /b $msiexecExit")
        val launcher = fake("launch.cmd", "echo launch>> \"$calls\"")
        val process = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString(),
            "-AppPid", "999999", "-Target", target.toString(), "-Workspace", workspace.toString(),
        ).redirectErrorStream(true)
            .apply {
                environment()["DARI_UPDATE_MSIEXEC"] = msiexec.toString()
                environment()["DARI_UPDATE_OPEN"] = launcher.toString()
            }
            .start()
        process.inputStream.readAllBytes()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        // Start-Process returns before launch.cmd has written its line.
        Thread.sleep(1000)
        return process.exitValue()
    }
}
