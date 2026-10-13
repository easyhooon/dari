package com.easyhooon.dari.desktop.app.update

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdaterTest {
    @Test
    fun `a copy in a writable folder can update itself`() {
        val folder = Files.createTempDirectory("dari-apps")
        assertNull(Updater.installBlocker(folder.resolve("Dari.app")))
    }

    @Test
    fun `a translocated or disk image copy is asked to move to Applications`() {
        listOf(
            "/private/var/folders/x/T/AppTranslocation/0000/d/Dari.app",
            "/Volumes/Dari 2.1.2/Dari.app",
        ).forEach { path ->
            val blocker = assertNotNull(Updater.installBlocker(Paths.get(path)), path)
            assertTrue("Applications folder" in blocker, path)
        }
    }

    @Test
    fun `build outputs and read-only folders cannot update themselves`() {
        assertNotNull(Updater.installBlocker(Paths.get("/repo/dari-desktop-app/build/compose/binaries/main/app/Dari.app")))
        assertNotNull(Updater.installBlocker(Paths.get("/nonexistent-dari-folder/Dari.app")))
    }
}
