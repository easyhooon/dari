import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "com.easyhooon.dari.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

mavenPublishing {
    coordinates(
        groupId = "io.github.easyhooon",
        artifactId = "dari-core",
        version = "1.1.0"
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

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
}
