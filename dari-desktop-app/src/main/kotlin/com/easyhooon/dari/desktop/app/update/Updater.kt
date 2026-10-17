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
 * Checks GitHub releases and, on macOS, replaces the installed Dari.app in place. Modeled on
 * Necto's updater: stage the new Contents next to the app, quit, and let a helper swap them in.
 */
class Updater(
    private val scope: CoroutineScope,
    appVersion: String,
    private val appBundle: Path? = currentAppBundle(),
) {
    sealed interface Phase {
        data object Idle : Phase
        data object Checking : Phase
        data object UpToDate : Phase

        /** A newer version this copy of Dari can install itself. */
        data class Available(val version: SemanticVersion) : Phase

        /** A newer version that has to be installed by hand; [blocker] says why on macOS, null on Windows. */
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
                appBundle == null -> Phase.Manual(latest)
                else -> installBlocker(appBundle)?.let { Phase.Manual(latest, it) } ?: Phase.Available(latest)
            }
        } catch (e: Exception) {
            // A failed background check shouldn't put an error in the title bar.
            AppLog.error("Update check failed", e)
            if (quiet) phase else Phase.Failed(e.message ?: "The update check failed.")
        }
    }

    /** Downloads and stages the update, then calls [quit]; the helper relaunches Dari once it exits. */
    fun install(quit: () -> Unit) {
        val target = appBundle
        val version = (phase as? Phase.Available)?.version
        if (target == null || version == null) {
            installManually()
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            val workspace = target.resolveSibling(".dari-update-${UUID.randomUUID()}")
            val mountPoint = workspace.resolve("mnt")
            var mounted = false
            try {
                withContext(Dispatchers.IO) {
                    Files.createDirectory(workspace)
                    val script = workspace.resolve("dari-finish-update.sh")
                    Updater::class.java.getResourceAsStream("/dari-finish-update.sh")!!.use {
                        Files.copy(it, script, StandardCopyOption.REPLACE_EXISTING)
                    }

                    phase = Phase.Working("Downloading $version…")
                    val dmg = workspace.resolve("Dari.dmg")
                    val checksum = workspace.resolve("Dari.dmg.sha256")
                    val url = ReleaseSource.dmgUrl(version, macArch())
                    ReleaseSource.download(url, dmg)
                    ReleaseSource.downloadChecksum(URI("$url.sha256"), checksum)
                    ReleaseSource.verifyChecksum(dmg, checksum)

                    phase = Phase.Working("Verifying $version…")
                    Files.createDirectory(mountPoint)
                    Processes.check(
                        "/usr/bin/hdiutil", "attach", "-nobrowse", "-readonly", "-noautoopen",
                        "-mountpoint", mountPoint.toString(), dmg.toString(),
                    )
                    mounted = true
                    val candidate = mountPoint.resolve(target.fileName)
                    UpdateValidation.verify(candidate, target, version)

                    phase = Phase.Working("Installing $version…")
                    Processes.check("/usr/bin/ditto", candidate.resolve("Contents").toString(), workspace.resolve("Contents").toString())
                    Processes.check("/usr/bin/hdiutil", "detach", mountPoint.toString())
                    mounted = false
                    Files.delete(dmg)

                    // nohup keeps the helper alive after Dari exits; it waits for this pid first.
                    ProcessBuilder(
                        "/usr/bin/nohup", "/bin/sh", script.toString(),
                        ProcessHandle.current().pid().toString(), target.toString(), workspace.toString(),
                    ).redirectErrorStream(true)
                        .redirectOutput(workspace.resolve("install.log").toFile())
                        .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                        .start()
                }
                quit()
            } catch (e: Exception) {
                AppLog.error("Update to $version failed", e)
                withContext(Dispatchers.IO) {
                    if (mounted) runCatching { Processes.run("/usr/bin/hdiutil", "detach", "-force", mountPoint.toString()) }
                    runCatching { workspace.toFile().deleteRecursively() }
                }
                phase = Phase.Failed(e.message ?: "The update failed. Nothing was installed.")
            }
        }
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
        /** `…/Dari.app/Contents/MacOS/Dari` → `…/Dari.app`, for packaged macOS builds only. */
        fun currentAppBundle(): Path? {
            if (!System.getProperty("os.name").startsWith("Mac")) return null
            val launcher = System.getProperty("jpackage.app-path") ?: return null
            val bundle = Paths.get(launcher).toAbsolutePath().parent?.parent?.parent ?: return null
            return bundle.takeIf { it.fileName.toString().endsWith(".app") }
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

        private fun macArch(): String = if (System.getProperty("os.arch") == "aarch64") "arm64" else "x64"
    }
}
