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
    implementation(libs.sentry)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

// Opt-in crash reports need a DSN: -PdariSentryDsn or DARI_SENTRY_DSN. Without one (local, fork builds) they stay off.
val generateCrashReportingConfig by tasks.registering(WriteProperties::class) {
    destinationFile = layout.buildDirectory.file("generated/crash-reporting/dari-crash-reporting.properties")
    property(
        "dsn",
        providers.gradleProperty("dariSentryDsn").orElse(providers.environmentVariable("DARI_SENTRY_DSN")).orElse(""),
    )
}
sourceSets.main {
    resources.srcDir(generateCrashReportingConfig.map { it.destinationFile.get().asFile.parentFile })
}

compose.desktop {
    application {
        mainClass = "com.easyhooon.dari.desktop.app.MainKt"
        // Package with the toolchain JDK; Compose rejects Homebrew JDKs for jpackage.
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
            .get().metadata.installationPath.asFile.absolutePath
        // Dock name for `run`; packaged apps take it from packageName. The flag exists only on macOS JVMs.
        if (System.getProperty("os.name").startsWith("Mac")) jvmArgs += "-Xdock:name=Dari"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Dari"
            // -PdariDesktopVersion=1.9.0 packages an older version to test the in-app updater.
            packageVersion = (findProperty("dariDesktopVersion") as String?) ?: libs.versions.dari.get()
            description = "Desktop inspector for Dari WebView bridge messages"
            vendor = "easyhooon"
            copyright = "© 2026 easyhooon. Apache License 2.0."
            // jpackage bundles a trimmed JRE. From `suggestRuntimeModules`, plus jdk.crypto.ec for TLS
            // to GitHub; without java.net.http the updater crashes the packaged app.
            modules("java.instrument", "java.net.http", "java.prefs", "jdk.unsupported", "jdk.crypto.ec")

            macOS {
                bundleID = "io.github.easyhooon.dari.desktop"
                appCategory = "public.app-category.developer-tools"
                // The DMG is ULMO (LZMA), which macOS can mount only from 10.15.
                minimumSystemVersion = "10.15"
                iconFile.set(project.file("icons/dari.icns"))
                // Compose also re-signs the runtime after jpackage; retain JDK 17's JVM entitlements.
                entitlementsFile.set(project.file("packaging/macos-entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("packaging/macos-entitlements.plist"))
                signing {
                    sign.set(providers.environmentVariable("DARI_MACOS_SIGN").map { it == "true" }.orElse(false))
                    identity.set(providers.environmentVariable("MACOS_SIGNING_IDENTITY"))
                    keychain.set(providers.environmentVariable("MACOS_SIGNING_KEYCHAIN"))
                }
            }
            windows {
                iconFile.set(project.file("icons/dari.ico"))
                menuGroup = "Dari"
                // Must never change: Windows uses it to upgrade an installed Dari in place.
                upgradeUuid = "94cfb91e-3282-4beb-a9a1-af3b4a2c6ca5"
                perUserInstall = true
            }
            linux {
                iconFile.set(project.file("icons/dari.png"))
                packageName = "dari"
                debMaintainer = "mraz3068@gmail.com"
                menuGroup = "Development"
            }
        }
    }
}
