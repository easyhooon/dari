package com.easyhooon.dari.desktop.app.update

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration

/**
 * Reads Dari's GitHub releases without the GitHub API or a login, like Necto's updater: the
 * `/releases/latest` redirect names the newest tag, and assets are fetched only from GitHub hosts.
 */
object ReleaseSource {
    val pageUrl: URI = URI("https://github.com/easyhooon/dari/releases")
    val downloadPageUrl: URI = URI("https://easyhooon.github.io/dari/download")

    private const val MAX_INSTALLER_BYTES = 400L * 1024 * 1024
    private const val MAX_CHECKSUM_BYTES = 1024L
    private const val MAX_REDIRECTS = 5
    private val ALLOWED_HOSTS = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    class Failure(message: String) : IOException(message)

    fun latestVersion(): SemanticVersion {
        val request = HttpRequest.newBuilder(URI("$pageUrl/latest"))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(15))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        val location = response.headers().firstValue("location").orElse(null)
            ?: throw Failure("The update check failed (HTTP ${response.statusCode()}).")
        return parseTagFromLocation(location) ?: throw Failure("The latest release has no version tag.")
    }

    /** `…/releases/tag/2.1.0` → 2.1.0 */
    fun parseTagFromLocation(location: String): SemanticVersion? =
        SemanticVersion.parse(location.trimEnd('/').substringAfterLast('/'))

    fun dmgUrl(version: SemanticVersion, arch: String): URI = URI("$pageUrl/download/$version/Dari-$version-$arch.dmg")

    fun tarballUrl(version: SemanticVersion, arch: String): URI =
        URI("$pageUrl/download/$version/Dari-$version-linux-$arch.tar.gz")

    fun download(url: URI, target: Path) = download(url, target, MAX_INSTALLER_BYTES)

    fun downloadChecksum(url: URI, target: Path) = download(url, target, MAX_CHECKSUM_BYTES)

    fun verifyChecksum(file: Path, checksumFile: Path) {
        val expected = Files.readString(checksumFile).trim().substringBefore(' ').lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (expected.isEmpty() || expected != actual) {
            throw Failure("The download did not match its published hash. Nothing was installed.")
        }
    }

    fun isAllowed(url: URI): Boolean =
        url.scheme == "https" && url.userInfo == null && (url.port == -1 || url.port == HTTPS_PORT) &&
            url.host?.lowercase() in ALLOWED_HOSTS

    private fun download(start: URI, target: Path, maxBytes: Long) {
        var url = start
        repeat(MAX_REDIRECTS) {
            if (!isAllowed(url)) throw Failure("Refusing to download from ${url.host}.")
            val request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(5)).build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            when (response.statusCode()) {
                in REDIRECT_CODES -> {
                    response.body().close()
                    val location = response.headers().firstValue("location").orElse(null)
                        ?: throw Failure("The update download failed (redirect without a location).")
                    url = url.resolve(location)
                }
                HTTP_OK -> {
                    val length = response.headers().firstValueAsLong("content-length").orElse(-1)
                    if (length > maxBytes) throw Failure("The update download exceeded its size limit.")
                    response.body().use { input ->
                        Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { out ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > maxBytes) throw Failure("The update download exceeded its size limit.")
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    return
                }
                else -> {
                    response.body().close()
                    throw Failure("The update download failed (HTTP ${response.statusCode()}).")
                }
            }
        }
        throw Failure("The update download redirected too many times.")
    }

    private const val BUFFER_SIZE = 64 * 1024
    private const val HTTP_OK = 200
    private const val HTTPS_PORT = 443
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
}
