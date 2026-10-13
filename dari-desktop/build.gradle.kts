plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "com.easyhooon.dari.desktop"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "DARI_VERSION", "\"${libs.versions.dari.get()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        optIn.add("com.easyhooon.dari.runtime.InternalDariApi")
    }
}

dependencies {
    api(project(":dari-runtime"))
    implementation(project(":dari-inspector-protocol"))

    implementation(libs.androidx.startup)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        groupId = "io.github.easyhooon",
        artifactId = "dari-desktop",
        version = libs.versions.dari.get(),
    )

    pom {
        name.set("Dari Desktop")
        description.set("Streams Dari bridge messages to the Dari desktop inspector over adb")
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
