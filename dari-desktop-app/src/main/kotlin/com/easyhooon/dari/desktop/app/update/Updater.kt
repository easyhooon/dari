package com.easyhooon.dari.desktop.app.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.easyhooon.dari.desktop.app.AppLog
import com.easyhooon.dari.desktop.app.ExternalOpener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.swing.JOptionPane
import kotlin.time.Duration.Companion.hours

/**
 * Checks GitHub releases and updates the installed Dari.app (macOS), per-user MSI install (Windows) or
 * tarball folder (Linux) in place. Modeled on Necto's updater: stage the new copy next to the app, quit,
 * and let a helper swap it in.
 */
class Updater(
    private val scope: CoroutineScope,
    appVersion: String,
    private val installTarget: InstallTarget? = currentInstallTarget(),
) {
    sealed interface InstallTarget {
        val path: Path

        data class MacBundle(override val path: Path) : InstallTarget

        /** The `Dari` folder extracted from the Linux tarball, holding `bin/Dari`. */
        data class LinuxImage(override val path: Path) : InstallTarget

        /** The per-user MSI install folder, `%LOCALAPPDATA%\Dari`, holding `Dari.exe`. */
        data class WindowsInstall(override val path: Path) : InstallTarget
    }

    sealed interface Phase {
        data object Idle : Phase
        data object Checking : Phase
        data object UpToDate : Phase

        /** A newer version this copy of Dari can install itself. */
        data class Available(val version: SemanticVersion) : Phase

        /** A newer version that has to be installed by hand; [blocker] says why when known. */
        data class Manual(val version: SemanticVersion, val blocker: String? = null) : Phase
        data class Working(val step: String) : Phase
        data class Failed(val message: String) : Phase
    }

    var phase: Phase by mutableStateOf(Phase.Idle)
        private set

    private val current = SemanticVersion.parse(appVersion)
    private var job: Job? = null

    /** False for `./gradlew run`, which has no version to compare. */
    val enabled: Boolean get() = current != null

    val newerVersion: SemanticVersion?
        get() = when (val p = phase) {
            is Phase.Available -> p.version
            is Phase.Manual -> p.version
            else -> null
        }

    fun startPeriodicChecks() {
        if (!enabled) return
        scope.launch {
            while (isActive) {
                check(quiet = true)
                delay(6.hours)
            }
        }
    }

    fun checkNow() {
        scope.launch { check(quiet = false) }
    }

    private suspend fun check(quiet: Boolean) {
        val current = current ?: return
        if (job?.isActive == true || phase is Phase.Working) return
        if (!quiet) phase = Phase.Checking
        phase = try {
            val latest = withContext(Dispatchers.IO) { ReleaseSource.latestVersion() }
            when {
                latest <= current -> Phase.UpToDate
                installTarget == null -> Phase.Manual(latest)
                else -> installBlocker(installTarget)?.let { Phase.Manual(latest, it) } ?: Phase.Available(latest)
            }
        } catch (e: Exception) {
            // A failed background check shouldn't put an error in the title bar.
            AppLog.error("Update check failed", e)
            if (quiet) phase else Phase.Failed(e.message ?: "The update check failed.")
        }
    }

    /** Downloads and stages the update, then calls [quit]; the helper relaunches Dari once it exits. */
    fun install(quit: () -> Unit) {
        val target = installTarget
        val version = (phase as? Phase.Available)?.version
        if (target == null || version == null) {
            installManually()
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            val workspace = target.path.resolveSibling(".dari-update-${UUID.randomUUID()}")
            try {
                withContext(Dispatchers.IO) {
                    Files.createDirectory(workspace)
                    val script = when (target) {
                        is InstallTarget.MacBundle -> copyResource("dari-finish-update.sh", workspace)
                        is InstallTarget.LinuxImage -> copyResource("dari-finish-update-linux.sh", workspace)
                        is InstallTarget.WindowsInstall -> copyResource("dari-finish-update.ps1", workspace)
                    }

                    when (target) {
                        is InstallTarget.MacBundle -> stageMac(target.path, workspace, version)
                        is InstallTarget.LinuxImage -> stageLinux(target.path, workspace, version)
                        is InstallTarget.WindowsInstall -> stageWindows(workspace, version)
                    }
                    startHelper(target, script, workspace)
                }
                quit()
            } catch (e: Exception) {
                AppLog.error("Update to $version failed", e)
                withContext(Dispatchers.IO) { runCatching { workspace.toFile().deleteRecursively() } }
                phase = Phase.Failed(e.message ?: "The update failed. Nothing was installed.")
            }
        }
    }

    private fun copyResource(name: String, workspace: Path): Path {
        val file = workspace.resolve(name)
        Updater::class.java.getResourceAsStream("/$name")!!.use { Files.copy(it, file, StandardCopyOption.REPLACE_EXISTING) }
        return file
    }

    /** The helper outlives Dari and waits for this pid before touching the install. */
    private fun startHelper(target: InstallTarget, script: Path, workspace: Path) {
        val pid = ProcessHandle.current().pid().toString()
        val builder = if (target is InstallTarget.WindowsInstall) {
            // Log outside the workspace: Windows can't delete a folder holding an open file. Run from the
            // workspace's parent so the helper doesn't hold the install folder open while the MSI replaces it.
            ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                "-File", script.toString(), "-AppPid", pid, "-Target", target.path.toString(), "-Workspace", workspace.toString(),
            ).directory(workspace.parent.toFile())
                .redirectOutput(File(AppLog.directory, "update.log"))
        } else {
            // nohup keeps the helper alive after Dari exits.
            ProcessBuilder("/usr/bin/nohup", "/bin/sh", script.toString(), pid, target.path.toString(), workspace.toString())
                .redirectOutput(workspace.resolve("install.log").toFile())
                .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
        }
        // The jpackage launcher sets this for itself; a relaunched Dari that inherits it skips the app's
        // main class and only prints java's usage.
        builder.environment().remove("_JPACKAGE_LAUNCHER")
        builder.redirectErrorStream(true).start()
    }

    /** Leaves the new `Contents` in [workspace] for `dari-finish-update.sh`. */
    private fun stageMac(target: Path, workspace: Path, version: SemanticVersion) {
        phase = Phase.Working("Downloading $version…")
        val dmg = workspace.resolve("Dari.dmg")
        downloadVerified(ReleaseSource.dmgUrl(version, releaseArch()), dmg)

        phase = Phase.Working("Verifying $version…")
        val mountPoint = workspace.resolve("mnt")
        Files.createDirectory(mountPoint)
        Processes.check(
            "/usr/bin/hdiutil",
            "attach",
            "-nobrowse",
            "-readonly",
            "-noautoopen",
            "-mountpoint",
            mountPoint.toString(),
            dmg.toString(),
        )
        var mounted = true
        try {
            val candidate = mountPoint.resolve(target.fileName)
            UpdateValidation.verify(candidate, target, version)

            phase = Phase.Working("Installing $version…")
            Processes.check("/usr/bin/ditto", candidate.resolve("Contents").toString(), workspace.resolve("Contents").toString())
            Processes.check("/usr/bin/hdiutil", "detach", mountPoint.toString())
            mounted = false
        } finally {
            if (mounted) runCatching { Processes.run("/usr/bin/hdiutil", "detach", "-force", mountPoint.toString()) }
        }
        Files.delete(dmg)
    }

    /** Leaves the new app folder at `workspace/Dari` for `dari-finish-update-linux.sh`. */
    private fun stageLinux(target: Path, workspace: Path, version: SemanticVersion) {
        phase = Phase.Working("Downloading $version…")
        val tarball = workspace.resolve("Dari.tar.gz")
        downloadVerified(ReleaseSource.tarballUrl(version, releaseArch()), tarball)

        phase = Phase.Working("Verifying $version…")
        // Extract into a subfolder so the archive can't overwrite the helper script.
        val extracted = workspace.resolve("extracted")
        Files.createDirectory(extracted)
        Processes.check("tar", "-xzf", tarball.toString(), "-C", extracted.toString())
        val candidate = extracted.resolve(UpdateValidation.LINUX_LAUNCHER)
        UpdateValidation.verifyLinux(candidate, target, version)

        phase = Phase.Working("Installing $version…")
        Files.move(candidate, workspace.resolve(UpdateValidation.LINUX_LAUNCHER))
        extracted.toFile().deleteRecursively()
        Files.delete(tarball)
    }

    /** Leaves the checked MSI at `workspace/Dari.msi` for `dari-finish-update.ps1`. */
    private fun stageWindows(workspace: Path, version: SemanticVersion) {
        val current = current ?: throw UpdateValidation.Failure("This build has no version to update from.")
        phase = Phase.Working("Downloading $version…")
        val msi = workspace.resolve("Dari.msi")
        downloadVerified(ReleaseSource.msiUrl(version), msi)

        phase = Phase.Working("Verifying $version…")
        val infoScript = copyResource("dari-msi-info.ps1", workspace)
        val info = Processes.run(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", infoScript.toString(), "-Msi", msi.toString(),
        )
        if (info.exitCode != 0) throw UpdateValidation.Failure("The update could not be checked. Nothing was installed.")
        UpdateValidation.verifyWindows(info.output, current, version)
        Files.delete(infoScript)
    }

    private fun downloadVerified(url: URI, file: Path) {
        val checksum = file.resolveSibling("${file.fileName}.sha256")
        ReleaseSource.download(url, file)
        ReleaseSource.downloadChecksum(URI("$url.sha256"), checksum)
        ReleaseSource.verifyChecksum(file, checksum)
    }

    /** Says why this copy can't update itself, if known, before offering the download page. */
    fun installManually() {
        val blocker = (phase as? Phase.Manual)?.blocker
        if (blocker != null) {
            val options = arrayOf("OK", "Open Download Page")
            val choice = JOptionPane.showOptionDialog(
                null,
                blocker,
                "Update Dari",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0],
            )
            if (choice != 1) return
        }
        openDownloadPage()
    }

    fun openDownloadPage() {
        ExternalOpener.browse(ReleaseSource.downloadPageUrl)
    }

    companion object {
        /**
         * Where the running packaged build lives: `…/Dari.app/Contents/MacOS/Dari` → `…/Dari.app` on
         * macOS, `…/Dari/bin/Dari` → `…/Dari` on Linux, `…\Dari\Dari.exe` → `…\Dari` on Windows. Null for
         * `./gradlew run`.
         */
        fun currentInstallTarget(): InstallTarget? {
            val launcher = System.getProperty("jpackage.app-path")?.let { Paths.get(it).toAbsolutePath() } ?: return null
            val os = System.getProperty("os.name")
            return when {
                os.startsWith("Mac") ->
                    launcher.parent?.parent?.parent?.takeIf { it.fileName.toString().endsWith(".app") }?.let(InstallTarget::MacBundle)
                os.startsWith("Linux") ->
                    launcher.parent?.parent
                        ?.takeIf { Files.isRegularFile(it.resolve("lib/app/${UpdateValidation.LINUX_LAUNCHER}.cfg")) }
                        ?.let(InstallTarget::LinuxImage)
                os.startsWith("Windows") ->
                    launcher.parent?.takeIf { Files.isRegularFile(it.resolve("app/Dari.cfg")) }?.let(InstallTarget::WindowsInstall)
                else -> null
            }
        }

        fun installBlocker(target: InstallTarget): String? = when (target) {
            is InstallTarget.MacBundle -> installBlocker(target.path)
            is InstallTarget.LinuxImage -> linuxInstallBlocker(target.path)
            is InstallTarget.WindowsInstall -> windowsInstallBlocker(target.path, System.getenv("LOCALAPPDATA"))
        }

        /**
         * Why this copy can't be replaced in place, or null when it can. A translocated or mounted-DMG
         * copy is read-only, and a Gradle build output would be overwritten by the next build anyway.
         */
        fun installBlocker(bundle: Path): String? {
            val path = bundle.toString()
            val parent = bundle.parent
            return when {
                path.contains("/AppTranslocation/") || path.startsWith("/Volumes/") ->
                    "Dari is running from the disk image or a temporary location, so it can't update itself. " +
                        "Drag Dari to the Applications folder, open it from there, and click Update again."
                path.contains("/build/compose/") -> "Development builds don't update themselves."
                parent == null || !Files.isWritable(parent) ->
                    "Dari can't replace itself in ${parent ?: path}. Move it to the Applications folder, open it from there, and click Update again."
                else -> null
            }
        }

        /** The .deb installs under /opt as root and is upgraded through apt; a tarball folder needs to be writable. */
        fun linuxInstallBlocker(image: Path): String? {
            val path = image.toString()
            val parent = image.parent
            return when {
                path.startsWith("/opt/") ->
                    "Dari was installed from the .deb package, so it can't update itself. " +
                        "Download the new .deb from the download page and install it with \"sudo apt install ./<file>.deb\"."
                path.contains("/build/compose/") -> "Development builds don't update themselves."
                // Moving a directory to another parent also rewrites its "..", so the folder itself must be writable.
                parent == null || !Files.isWritable(parent) || !Files.isWritable(image) ->
                    "Dari can't replace itself in ${parent ?: path}. Download the new tarball and extract it into a folder you own."
                else -> null
            }
        }

        /** The per-user MSI always installs under %LOCALAPPDATA%; anything else wasn't installed by it. */
        fun windowsInstallBlocker(folder: Path, localAppData: String?): String? = when {
            folder.toString().replace('\\', '/').contains("/build/compose/") -> "Development builds don't update themselves."
            localAppData == null || !folder.startsWith(Paths.get(localAppData)) ->
                "Dari wasn't installed by its installer, so it can't update itself. Download the new .exe from the download page and run it."
            !Files.isWritable(folder.parent) -> "Dari can't update itself in ${folder.parent}. Download the new .exe and run it."
            else -> null
        }

        private fun releaseArch(): String = if (System.getProperty("os.arch") == "aarch64") "arm64" else "x64"
    }
}
