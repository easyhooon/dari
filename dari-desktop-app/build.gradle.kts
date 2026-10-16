import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

// Failure-path tests write to AppLog; keep that out of the developer's real dari.log.
tasks.test {
    val logHome = temporaryDir.absolutePath
    systemProperty("user.home", logHome)
    environment("XDG_STATE_HOME", logHome)
    environment("LOCALAPPDATA", logHome)
}

// Skiko's macOS runtime jars carry both the arm64 and x64 dylibs. Compose extracts only the host
// one and leaves the other inside the packaged jar (~9 MB compressed), so drop it before packaging.
abstract class StripForeignSkikoDylib : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val jar = inputArtifact.get().asFile
        val prefix = "skiko-awt-runtime-macos-"
        if (!jar.name.startsWith(prefix)) {
            outputs.file(jar)
            return
        }
        // Keep the file name: Compose finds the jar to unpack by its skiko-awt-runtime-<os>-<arch> prefix.
        val keep = "libskiko-macos-" + jar.name.removePrefix(prefix).substringBefore('-') + "."
        ZipInputStream(jar.inputStream().buffered()).use { input ->
            ZipOutputStream(outputs.file(jar.name).outputStream().buffered()).use { output ->
                generateSequence { input.nextEntry }
                    .filter { !it.name.startsWith("libskiko-macos-") || it.name.startsWith(keep) }
                    .forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name))
                        input.copyTo(output)
                        output.closeEntry()
                    }
            }
        }
    }
}

val skikoStripped = Attribute.of("dari.skikoStripped", Boolean::class.javaObjectType)
dependencies {
    attributesSchema { attribute(skikoStripped) }
    artifactTypes.getByName("jar").attributes.attribute(skikoStripped, false)
    registerTransform(StripForeignSkikoDylib::class) {
        from.attribute(skikoStripped, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(skikoStripped, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
    }
}
configurations.runtimeClasspath { attributes.attribute(skikoStripped, true) }

// Crash reports need a DSN: -PdariSentryDsn or DARI_SENTRY_DSN. Without one (local, fork builds) they stay off.
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

        // Release tasks (createReleaseDistributable, packageRelease*) shrink the jars with ProGuard.
        // Obfuscation stays off so log and issue-report stack traces remain readable.
        buildTypes.release.proguard {
            obfuscate.set(false)
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Dari"
            // -PdariDesktopVersion=1.9.0 packages an older version to test the in-app updater.
            packageVersion = (findProperty("dariDesktopVersion") as String?) ?: libs.versions.dariDesktop.get()
            description = "Desktop inspector for Dari WebView bridge messages"
            vendor = "easyhooon"
            copyright = "© 2026 easyhooon. Apache License 2.0."
            // jpackage bundles a trimmed JRE. From `suggestRuntimeModules`, plus jdk.crypto.ec for TLS
            // to GitHub; without java.net.http the updater crashes the packaged app.
            modules("java.instrument", "java.net.http", "java.prefs", "jdk.unsupported", "jdk.crypto.ec")

            macOS {
                bundleID = "io.github.easyhooon.dari.desktop"
                appCategory = "public.app-category.developer-tools"
                // Raised for the ULMO DMG in 2.2.0; kept although the ULFO DMG opens from 10.11.
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
