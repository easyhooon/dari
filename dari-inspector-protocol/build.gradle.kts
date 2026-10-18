import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.maven.publish)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

tasks.test {
    // Shared with dari-web; see fixtures/messages/README.md.
    val fixtures = rootProject.layout.projectDirectory.dir("fixtures/messages")
    inputs.dir(fixtures).withPropertyName("sharedFixtures").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("dari.fixtures.dir", fixtures.asFile.absolutePath)
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        groupId = "io.github.easyhooon",
        artifactId = "dari-inspector-protocol",
        version = libs.versions.dari.get(),
    )

    pom {
        name.set("Dari Inspector Protocol")
        description.set("Wire protocol between Dari and the desktop inspector")
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

    // Publish as soon as Central validates the upload, without a manual step in the Central Portal.
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
}
