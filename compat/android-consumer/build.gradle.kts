import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.easyhooon.dari.compat"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("io.github.easyhooon:dari-core:${libs.versions.dari.get()}")
}

// Gradle metadata must redirect the root coordinate to the Android variant.
val verifyDariCoreVariant by tasks.registering {
    val resolution = configurations.named("debugCompileClasspath").flatMap { it.incoming.resolutionResult.rootComponent }
    doLast {
        val modules = mutableSetOf<String>()
        fun walk(component: ResolvedComponentResult) {
            val id = component.moduleVersion ?: return
            if (!modules.add("${id.group}:${id.name}")) return
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { walk(it.selected) }
        }
        walk(resolution.get())
        check("io.github.easyhooon:dari-core-android" in modules) {
            "dari-core did not resolve to dari-core-android. Resolved: ${modules.filter { "easyhooon" in it }}"
        }
        println("dari-core resolved to dari-core-android")
    }
}

tasks.named("preBuild") { dependsOn(verifyDariCoreVariant) }
