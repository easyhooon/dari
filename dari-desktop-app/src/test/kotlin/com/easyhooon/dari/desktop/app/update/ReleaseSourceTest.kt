package com.easyhooon.dari.desktop.app.update

import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseSourceTest {
    @Test
    fun `reads the version from the latest-release redirect`() {
        assertEquals(
            SemanticVersion(2, 1, 0),
            ReleaseSource.parseTagFromLocation("https://github.com/easyhooon/dari/releases/tag/2.1.0"),
        )
        assertNull(ReleaseSource.parseTagFromLocation("https://github.com/easyhooon/dari/releases"))
    }

    @Test
    fun `builds the asset url the packaging script publishes`() {
        assertEquals(
            URI("https://github.com/easyhooon/dari/releases/download/2.1.0/Dari-2.1.0-arm64.dmg"),
            ReleaseSource.dmgUrl(SemanticVersion(2, 1, 0), "arm64"),
        )
    }

    @Test
    fun `downloads only over https from GitHub hosts`() {
        assertTrue(ReleaseSource.isAllowed(URI("https://github.com/easyhooon/dari/releases/download/2.1.0/a.dmg")))
        assertTrue(ReleaseSource.isAllowed(URI("https://release-assets.githubusercontent.com/github-production-release-asset/1")))
        assertFalse(ReleaseSource.isAllowed(URI("http://github.com/a.dmg")))
        assertFalse(ReleaseSource.isAllowed(URI("https://github.com.evil.example/a.dmg")))
        assertFalse(ReleaseSource.isAllowed(URI("https://user@github.com/a.dmg")))
        assertFalse(ReleaseSource.isAllowed(URI("https://github.com:8443/a.dmg")))
    }

    @Test
    fun `accepts a matching sha256 file and rejects a mismatch`() {
        val dir = Files.createTempDirectory("dari-checksum")
        val file = dir.resolve("Dari.dmg").also { Files.writeString(it, "dari") }
        val checksum = dir.resolve("Dari.dmg.sha256")
        // The `shasum -a 256` format the packaging script writes.
        Files.writeString(checksum, "${"0".repeat(64)}  Dari-2.1.0-arm64.dmg\n")
        assertFailsWith<ReleaseSource.Failure> { ReleaseSource.verifyChecksum(file, checksum) }

        Files.writeString(checksum, "${sha256("dari")}  Dari-2.1.0-arm64.dmg\n")
        ReleaseSource.verifyChecksum(file, checksum)

        Files.writeString(checksum, "")
        assertFailsWith<ReleaseSource.Failure> { ReleaseSource.verifyChecksum(file, checksum) }
        dir.toFile().deleteRecursively()
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
