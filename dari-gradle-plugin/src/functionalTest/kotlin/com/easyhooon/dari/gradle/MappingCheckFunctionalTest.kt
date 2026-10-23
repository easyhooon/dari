package com.easyhooon.dari.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

class MappingCheckFunctionalTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var project: File

    @Before
    fun setup() {
        val sdk = androidSdk()
        assumeTrue("No Android SDK: set ANDROID_HOME or sdk.dir in local.properties", sdk != null)
        project = temp.newFolder("app")
        project.resolve("local.properties").writeText("sdk.dir=${sdk!!.invariantSeparatorsPath}\n")
        project.resolve("gradle.properties").writeText("android.useAndroidX=true\n")
        project.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { google(); mavenCentral() } }
            rootProject.name = "app"
            """.trimIndent(),
        )
        project.resolve("src/main/AndroidManifest.xml").apply { parentFile.mkdirs() }.writeText("<manifest />\n")
        project.resolve("src/main/java/com/example/app/Bridge.java").apply { parentFile.mkdirs() }.writeText(
            """
            package com.example.app;

            public class Bridge {
                public void onRequest(String handlerName, String requestId) {
                }

                public static class Inner {
                    public void sendResponse(String requestId) {
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `assembleDebug runs the check, which passes and is then up to date`() {
        writeBuild(
            """
            webToAppRequest("com.example.app.Bridge", "onRequest") { handlerName = arg(0) }
            webToAppResponse("com.example.app.Bridge.Inner", "sendResponse") { requestId = arg(0) }
            """,
        )

        val first = runner("assembleDebug").build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":dariCheckDebugMappings")?.outcome)

        val second = runner("assembleDebug").build()
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":dariCheckDebugMappings")?.outcome)
        assertEquals(null, second.task(":dariCheckReleaseMappings"))
    }

    @Test
    fun `a misspelled class or method fails the build and names each mapping`() {
        writeBuild(
            """
            webToAppRequest("com.example.app.Brige", "onRequest") { handlerName = arg(0) }
            webToAppResponse("com.example.app.Bridge\${'$'}Inner", "sendRespons") { requestId = arg(0) }
            """,
        )

        val result = runner("assembleDebug").buildAndFail()
        // The script escapes `$`; the message shows the name as the mapping wrote it.

        assertEquals(TaskOutcome.FAILED, result.task(":dariCheckDebugMappings")?.outcome)
        result.assertOutput("Dari: 2 mapping(s) match no code")
        result.assertOutput("""webToAppRequest("com.example.app.Brige", "onRequest") }: no class com.example.app.Brige in this module.""")
        result.assertOutput("Did you mean com.example.app.Bridge?")
        result.assertOutput("com.example.app.Bridge${'$'}Inner has no method 'sendRespons' with a body. Did you mean sendResponse?")
    }

    @Test
    fun `failOnUnmatchedMapping = false only warns`() {
        writeBuild(
            """
            failOnUnmatchedMapping = false
            webToAppRequest("com.example.app.Bridge", "onRequst") { handlerName = arg(0) }
            """,
        )

        val result = runner("assembleDebug").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":dariCheckDebugMappings")?.outcome)
        result.assertOutput("Dari: dari { webToAppRequest(\"com.example.app.Bridge\", \"onRequst\") }: com.example.app.Bridge has no method")
    }

    private fun writeBuild(mappings: String) {
        project.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("io.github.easyhooon.dari")
            }

            android {
                namespace = "com.example.app"
                compileSdk = 36
                defaultConfig { minSdk = 26 }
            }

            dari {
            ${mappings.trimIndent().prependIndent("    ")}
            }
            """.trimIndent(),
        )
    }

    private fun runner(vararg args: String): GradleRunner = GradleRunner.create()
        .withProjectDir(project)
        .withPluginClasspath()
        .withArguments(*args, "--stacktrace")

    private fun BuildResult.assertOutput(expected: String) = assertTrue(output, expected in output)

    private fun androidSdk(): File? {
        val fromEnv = listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").firstNotNullOfOrNull { System.getenv(it) }
        val fromProperties = System.getProperty("dari.localProperties")?.let(::File)?.takeIf { it.isFile }?.let { file ->
            Properties().apply { file.inputStream().use(::load) }.getProperty("sdk.dir")
        }
        return (fromEnv ?: fromProperties)?.let(::File)?.takeIf { it.isDirectory }
    }
}
