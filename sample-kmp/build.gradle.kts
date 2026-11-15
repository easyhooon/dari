plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

// Shared module of the dari-kmp sample: `sample` depends on it on Android, and sample-ios links its
// framework when built with the overrides in its README.
kotlin {
    android {
        namespace = "com.easyhooon.dari.sample.kmp"
        compileSdk = 36
        minSdk = 26
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "DariKmpSample"
            isStatic = true
            // Exported to check whether dari-core's DariInterceptor clashes with the Swift SDK's in Swift.
            export(project(":dari-core"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":dari-core"))
            implementation(project(":dari-kmp"))
        }
    }
}
