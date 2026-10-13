package com.easyhooon.dari.desktop.app.adb

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdbTest {

    @Test
    fun `parses dari abstract sockets from proc net unix`() {
        val proc = """
            Num       RefCount Protocol Flags    Type St Inode Path
            0000000000000000: 00000002 00000000 00010000 0001 01 123456 @dari_com.example.app
            0000000000000000: 00000002 00000000 00010000 0001 01 123457 @jdwp-control
            0000000000000000: 00000003 00000000 00000000 0001 03 123458
            0000000000000000: 00000002 00000000 00010000 0001 01 123459 @dari_com.example.app
            0000000000000000: 00000002 00000000 00010000 0001 01 123460 @dari_com.another
        """.trimIndent()

        assertEquals(listOf("com.another", "com.example.app"), Adb.parseDariSockets(proc))
    }

    @Test
    fun `locates adb under ANDROID_HOME`() {
        val sdk = Files.createTempDirectory("sdk").toFile()
        val exe = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
        val adb = File(sdk, "platform-tools/$exe").apply {
            parentFile.mkdirs()
            writeText("")
            setExecutable(true)
        }

        assertEquals(adb.absolutePath, Adb.locate(mapOf("ANDROID_HOME" to sdk.absolutePath)))
        sdk.deleteRecursively()
    }

    @Test
    fun `returns null when adb is nowhere`() {
        val empty = Files.createTempDirectory("empty").toFile()
        val located = Adb.locate(mapOf("ANDROID_HOME" to empty.absolutePath, "PATH" to empty.absolutePath))
        // The default SDK location may exist on a developer machine; only assert when it doesn't.
        if (!File(System.getProperty("user.home"), "Library/Android/sdk/platform-tools/adb").exists() &&
            !File(System.getProperty("user.home"), "Android/Sdk/platform-tools/adb").exists()
        ) {
            assertNull(located)
        }
        empty.deleteRecursively()
    }
}

class AdbForwardListTest {
    @Test
    fun `picks only forwards to dari sockets`() {
        val list = """
            R3CY70P3F4H tcp:56778 localabstract:dari_com.example.app
            R3CY70P3F4H tcp:8081 tcp:8081
            emulator-5554 tcp:61157 localabstract:dari_com.other
        """.trimIndent()

        assertEquals(
            listOf("R3CY70P3F4H" to "tcp:56778", "emulator-5554" to "tcp:61157"),
            Adb.parseDariForwards(list),
        )
    }
}
