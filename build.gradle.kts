import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

tasks.register<Copy>("installGitHooks") {
    from("scripts/pre-commit")
    into(".git/hooks")
    filePermissions { unix("rwxr-xr-x") }
}

tasks.register<Exec>("checkDariApiParity") {
    description = "Checks that dari and dari-noop expose the same Dari API."
    dependsOn(":dari:compileDebugKotlin", ":dari-noop:compileDebugKotlin")
    commandLine("bash", "scripts/check-dari-api-parity.sh")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("installGitHooks")
}

// The plugin is an included build, so root lint, tests and clean don't reach it on their own.
val pluginBuild = gradle.includedBuild("dari-gradle-plugin")
listOf("detekt", "ktlintCheck", "ktlintFormat").forEach { name ->
    tasks.matching { it.name == name }.configureEach { dependsOn(pluginBuild.task(":$name")) }
}
tasks.register("clean") { dependsOn(pluginBuild.task(":clean")) }
tasks.register("testGradlePlugin") {
    description = "Runs the Dari Gradle plugin's unit tests."
    dependsOn(pluginBuild.task(":test"))
}

allprojects {
    apply {
        plugin(rootProject.libs.plugins.detekt.get().pluginId)
        plugin(rootProject.libs.plugins.ktlint.get().pluginId)
    }

    afterEvaluate {
        extensions.configure<DetektExtension> {
            parallel = true
            buildUponDefaultConfig = true
            toolVersion = libs.versions.detekt.get()
            config.setFrom(files("$rootDir/detekt-config.yml"))
            // The default source only covers src/main and src/test, which misses KMP source sets.
            if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                source.setFrom(file("src").listFiles().orEmpty().map { it.resolve("kotlin") }.filter { it.isDirectory })
            }
        }

        extensions.configure<KtlintExtension> {
            version.set(rootProject.libs.versions.ktlintSource.get())
            android.set(true)
            verbose.set(true)
        }
    }
}
