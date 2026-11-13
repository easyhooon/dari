import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

kotlin {
    android {
        namespace = "com.easyhooon.dari.kmp"
        compileSdk = 36
        minSdk = 26
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        // Declares DariObjC's selectors only; nothing from the Swift SDK is linked.
        target.compilations.getByName("main").cinterops.create("dariObjC")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":dari-core"))
        }
        androidMain.dependencies {
            implementation(project(":dari-runtime"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
