package com.easyhooon.dari.desktop.app.update

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks that a downloaded bundle is Dari, is the version we asked for, and is newer than the
 * installed one. The release repository is the trust source; an ad-hoc signature only proves integrity.
 */
object UpdateValidation {

    data class BundleInfo(val identifier: String?, val version: String?)

    class Failure(message: String) : IOException(message)

    const val LINUX_LAUNCHER = "Dari"

    /** `upgradeUuid` in build.gradle.kts; every Dari MSI carries it. */
    const val WINDOWS_UPGRADE_CODE = "94cfb91e-3282-4beb-a9a1-af3b4a2c6ca5"

    fun verify(candidate: Path, installed: Path, expected: SemanticVersion) {
        validateMetadata(info(installed), info(candidate), expected)
        val result = Processes.run("/usr/bin/codesign", "--verify", "--deep", "--strict", "--all-architectures", candidate.toString())
        if (result.exitCode != 0) throw Failure("The update's code signature is invalid. Nothing was installed.")
    }

    /** Same checks for a Linux app image, using the main class and version from its jpackage `.cfg`. */
    fun verifyLinux(candidate: Path, installed: Path, expected: SemanticVersion) {
        validateMetadata(linuxInfo(installed), linuxInfo(candidate), expected)
        if (!Files.isExecutable(candidate.resolve("bin/$LINUX_LAUNCHER"))) {
            throw Failure("The download is not the expected Dari update. Nothing was installed.")
        }
    }

    fun parseLinuxConfig(cfg: String): BundleInfo {
        val lines = cfg.lines().map { it.trim() }
        return BundleInfo(
            identifier = lines.firstOrNull { it.startsWith("app.mainclass=") }?.substringAfter('='),
            version = lines.firstOrNull { it.startsWith("java-options=-Djpackage.app-version=") }?.substringAfterLast('='),
        )
    }

    private fun linuxInfo(image: Path): BundleInfo {
        val cfg = image.resolve("lib/app/$LINUX_LAUNCHER.cfg")
        return if (Files.isRegularFile(cfg)) parseLinuxConfig(Files.readString(cfg)) else BundleInfo(null, null)
    }

    /** Checks the UpgradeCode and ProductVersion printed by `dari-msi-info.ps1` against the running Dari. */
    fun verifyWindows(msiInfo: String, installedVersion: SemanticVersion, expected: SemanticVersion) {
        validateMetadata(BundleInfo(WINDOWS_UPGRADE_CODE, installedVersion.toString()), parseMsiInfo(msiInfo), expected)
    }

    fun parseMsiInfo(output: String): BundleInfo {
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return BundleInfo(
            identifier = lines.getOrNull(0)?.removePrefix("{")?.removeSuffix("}")?.lowercase(),
            version = lines.getOrNull(1),
        )
    }

    fun validateMetadata(installed: BundleInfo, candidate: BundleInfo, expected: SemanticVersion) {
        val identifier = installed.identifier
        val current = installed.version?.let(SemanticVersion::parse)
        if (identifier.isNullOrEmpty() || candidate.identifier != identifier ||
            candidate.version != expected.toString() || current == null || expected <= current
        ) {
            throw Failure("The download is not the expected Dari update. Nothing was installed.")
        }
    }

    private fun info(app: Path): BundleInfo {
        val plist = app.resolve("Contents/Info.plist").toString()
        fun read(key: String) = Processes.run("/usr/bin/plutil", "-extract", key, "raw", "-o", "-", plist)
            .takeIf { it.exitCode == 0 }?.output?.trim()
        return BundleInfo(read("CFBundleIdentifier"), read("CFBundleShortVersionString"))
    }
}
