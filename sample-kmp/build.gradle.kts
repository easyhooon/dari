plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// iOS-only shared framework for the dari-kmp PoC; sample-ios links it when built with the overrides in its README.
kotlin {
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
