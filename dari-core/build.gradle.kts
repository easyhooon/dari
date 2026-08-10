import org.gradle.plugins.signing.Sign
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    android {
        namespace = "com.easyhooon.dari.core"
        compileSdk = 36
        minSdk = 26

        withJava()
        withHostTestBuilder {}
    }

    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = "io.github.easyhooon",
        artifactId = "dari-core",
        version = libs.versions.dari.get(),
    )

    pom {
        name.set("Dari Core")
        description.set("Shared types for Dari WebView bridge message inspector")
        inceptionYear.set("2025")
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

    publishToMavenCentral()
    signAllPublications()
}
