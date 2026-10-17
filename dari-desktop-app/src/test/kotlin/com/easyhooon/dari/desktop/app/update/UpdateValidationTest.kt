package com.easyhooon.dari.desktop.app.update

import com.easyhooon.dari.desktop.app.update.UpdateValidation.BundleInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateValidationTest {
    private val id = "io.github.easyhooon.dari.desktop"
    private val installed = BundleInfo(id, "2.0.0")
    private val expected = SemanticVersion(2, 1, 0)

    @Test
    fun `accepts the expected newer Dari`() {
        UpdateValidation.validateMetadata(installed, BundleInfo(id, "2.1.0"), expected)
    }

    @Test
    fun `accepts metadata for the 2_1_0 to 2_1_1 update`() {
        UpdateValidation.validateMetadata(
            BundleInfo(id, "2.1.0"),
            BundleInfo(id, "2.1.1"),
            SemanticVersion(2, 1, 1),
        )
    }

    @Test
    fun `rejects another app, another version, or a downgrade`() {
        listOf(
            BundleInfo("com.example.other", "2.1.0"),
            BundleInfo(null, "2.1.0"),
            BundleInfo(id, "2.2.0"),
            BundleInfo(id, null),
        ).forEach { candidate ->
            assertFailsWith<UpdateValidation.Failure>(candidate.toString()) {
                UpdateValidation.validateMetadata(installed, candidate, expected)
            }
        }
        assertFailsWith<UpdateValidation.Failure> {
            UpdateValidation.validateMetadata(BundleInfo(id, "2.1.0"), BundleInfo(id, "2.1.0"), expected)
        }
        assertFailsWith<UpdateValidation.Failure> {
            UpdateValidation.validateMetadata(BundleInfo(null, "2.0.0"), BundleInfo(null, "2.1.0"), expected)
        }
    }

    @Test
    fun `reads the main class and version from a jpackage cfg`() {
        val cfg = """
            [Application]
            app.classpath=${'$'}APPDIR/dari.jar
            app.mainclass=com.easyhooon.dari.desktop.app.MainKt

            [JavaOptions]
            java-options=-Djpackage.app-version=2.4.0
        """.trimIndent()
        assertEquals(
            BundleInfo("com.easyhooon.dari.desktop.app.MainKt", "2.4.0"),
            UpdateValidation.parseLinuxConfig(cfg),
        )
        assertEquals(BundleInfo(null, null), UpdateValidation.parseLinuxConfig(""))
    }

    @Test
    fun `verifies a Linux image by its cfg and launcher`() {
        val root = Files.createTempDirectory("dari-validate")
        try {
            val installed = linuxImage(root.resolve("installed"), "2.0.0")
            UpdateValidation.verifyLinux(linuxImage(root.resolve("new"), "2.1.0"), installed, expected)
            assertFailsWith<UpdateValidation.Failure> {
                UpdateValidation.verifyLinux(linuxImage(root.resolve("old"), "2.0.0"), installed, expected)
            }
            val noLauncher = linuxImage(root.resolve("broken"), "2.1.0").also { Files.delete(it.resolve("bin/Dari")) }
            assertFailsWith<UpdateValidation.Failure> { UpdateValidation.verifyLinux(noLauncher, installed, expected) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun linuxImage(folder: Path, version: String): Path {
        Files.createDirectories(folder.resolve("lib/app"))
        Files.createDirectories(folder.resolve("bin"))
        Files.writeString(
            folder.resolve("lib/app/Dari.cfg"),
            "[Application]\napp.mainclass=$MAIN_CLASS\n[JavaOptions]\njava-options=-Djpackage.app-version=$version\n",
        )
        Files.writeString(folder.resolve("bin/Dari"), "#!/bin/sh\n").toFile().setExecutable(true)
        return folder
    }

    private companion object {
        const val MAIN_CLASS = "com.easyhooon.dari.desktop.app.MainKt"
    }
}
