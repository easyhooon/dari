import org.gradle.plugins.signing.Sign
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

tasks.withType<Sign>().configureEach {
    onlyIf("publication signing is enabled") {
        !providers.gradleProperty("skipPublicationSigning").isPresent
    }
}

kotlin {
    // Baseline lives in api/; refresh with updateLegacyAbi after an intended API change.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    android {
        namespace = "com.easyhooon.dari.kmp"
        compileSdk = 36
        minSdk = 26

        withHostTestBuilder {}
    }

    // Desktop targets of a Compose Multiplatform app compile, and record nothing.
    jvm()

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        // Declares DariObjC's selectors only; nothing from the Swift SDK is linked.
        target.compilations.getByName("main").cinterops.create("dariObjC")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":dari-core"))
        }
        androidMain.dependencies {
            // The app picks the runtime: debugImplementation(dari), releaseImplementation(dari-noop).
            compileOnly(project(":dari-noop"))
        }
        getByName("androidHostTest").dependencies {
            implementation(project(":dari-noop"))
            implementation(kotlin("test"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Isolated repo for the external consumer check in compat/android-consumer.
publishing {
    repositories {
        maven {
            name = "compat"
            url = uri(rootProject.layout.buildDirectory.dir("compat-repo"))
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = "io.github.easyhooon",
        artifactId = "dari-kmp",
        version = libs.versions.dari.get(),
    )

    pom {
        name.set("Dari KMP")
        description.set("Kotlin Multiplatform entry point for Dari: Android runtime and the iOS Swift SDK")
        inceptionYear.set("2026")
        url.set("https://github.com/easyhooon/dari")

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("easyhooon")
                name.set("Lee jihun")
                email.set("mraz3068@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/easyhooon/dari")
            connection.set("scm:git:git://github.com/easyhooon/dari.git")
            developerConnection.set("scm:git:ssh://git@github.com/easyhooon/dari.git")
        }
    }

    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
}
