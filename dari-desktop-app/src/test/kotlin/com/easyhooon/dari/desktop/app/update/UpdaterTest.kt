package com.easyhooon.dari.desktop.app.update

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `a Linux tarball folder the user owns can update itself`() {
        val image = Files.createDirectories(Files.createTempDirectory("dari-linux").resolve("Dari"))
        assertNull(Updater.installBlocker(Updater.InstallTarget.LinuxImage(image)))
    }

    @Test
    fun `the deb install, build outputs and read-only folders on Linux are updated by hand`() {
        val deb = assertNotNull(Updater.linuxInstallBlocker(Paths.get("/opt/dari")))
        assertTrue("apt install" in deb)
        assertNotNull(Updater.linuxInstallBlocker(Paths.get("/repo/dari-desktop-app/build/compose/binaries/main/app/Dari")))
        assertNotNull(Updater.linuxInstallBlocker(Paths.get("/nonexistent-dari-folder/Dari")))
    }

    @Test
    fun `a Windows copy installed under LOCALAPPDATA can update itself`() {
        val localAppData = Files.createTempDirectory("dari-local")
        assertNull(Updater.windowsInstallBlocker(localAppData.resolve("Dari"), localAppData.toString()))
    }

    @Test
    fun `a Windows copy outside LOCALAPPDATA or a build output is updated by hand`() {
        val localAppData = Files.createTempDirectory("dari-local").toString()
        val elsewhere = assertNotNull(Updater.windowsInstallBlocker(Paths.get("/Program Files/Dari"), localAppData))
        assertTrue(".exe" in elsewhere)
        assertNotNull(Updater.windowsInstallBlocker(Paths.get("/Program Files/Dari"), null))
        val build = Paths.get(localAppData, "repo", "build", "compose", "binaries", "main", "app", "Dari")
        assertEquals("Development builds don't update themselves.", Updater.windowsInstallBlocker(build, localAppData))
    }
}
