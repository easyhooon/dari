package com.easyhooon.dari.desktop.app

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssueReportTest {
    @Test
    fun `prefills the version and hides the home folder`() {
        val url = IssueReport.newIssueUrl("2.0.1")
        val body = URLDecoder.decode(url.rawQuery.substringAfter("environment="), StandardCharsets.UTF_8)
        assertTrue(url.toString().startsWith("https://github.com/easyhooon/dari/issues/new?template=bug_report.yml&"))
        assertTrue("Dari desktop: 2.0.1" in body)
        assertFalse(System.getProperty("user.home") in body)
    }

    @Test
    fun `feature requests open the feature form`() {
        assertTrue(IssueReport.featureRequestUrl().toString().endsWith("issues/new?template=feature_request.yml"))
    }
}
