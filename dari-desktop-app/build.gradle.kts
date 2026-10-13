import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":dari-inspector-protocol"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.easyhooon.dari.desktop.app.MainKt"
        // Dock name for `run`; packaged apps take it from packageName. The flag exists only on macOS JVMs.
        if (System.getProperty("os.name").startsWith("Mac")) jvmArgs += "-Xdock:name=Dari"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Dari"
            packageVersion = libs.versions.dari.get()
            description = "Desktop inspector for Dari WebView bridge messages"
            vendor = "easyhooon"

            macOS {
                bundleID = "io.github.easyhooon.dari.desktop"
                iconFile.set(project.file("icons/dari.icns"))
            }
            linux {
                iconFile.set(project.file("icons/dari.png"))
            }
        }
    }
}
