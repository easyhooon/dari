package com.easyhooon.dari.desktop.app

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Version and platform details for bug reports. No device, app or message data. */
object IssueReport {
    fun environment(appVersion: String): String = listOf(
        "Dari desktop: $appVersion",
        "OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
        "Java: ${System.getProperty("java.runtime.version") ?: System.getProperty("java.version")}",
    ).joinToString("\n")

    /** Opens the feature request form; nothing about the user's setup is needed there. */
    fun featureRequestUrl(): URI = URI("https://github.com/easyhooon/dari/issues/new?template=feature_request.yml")

    /** Opens the bug report form with the environment field filled in (issue forms prefill by field id). */
    fun newIssueUrl(appVersion: String): URI {
        val environment = "${environment(appVersion)}\nLog file: ${AppLog.file.path.replace(System.getProperty("user.home"), "~")}"
        val query = "template=bug_report.yml&environment=" + URLEncoder.encode(environment, StandardCharsets.UTF_8)
        return URI("https://github.com/easyhooon/dari/issues/new?$query")
    }
}
