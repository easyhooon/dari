import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

tasks.register<Copy>("installGitHooks") {
    from("scripts/pre-commit")
    into(".git/hooks")
    filePermissions { unix("rwxr-xr-x") }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("installGitHooks")
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
        }

        extensions.configure<KtlintExtension> {
            version.set(rootProject.libs.versions.ktlintSource.get())
            android.set(true)
            verbose.set(true)
        }
    }
}
