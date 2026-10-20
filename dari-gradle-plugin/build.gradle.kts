import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

group = "io.github.easyhooon"
version = libs.versions.dari.get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Builds small Android projects with Gradle TestKit; needs an Android SDK (ANDROID_HOME or ../local.properties).
val functionalTest: SourceSet by sourceSets.creating
val functionalTestAgp: Configuration by configurations.creating

dependencies {
    // The app build already has AGP and its ASM; bundling another copy would clash with that classloader.
    compileOnly(libs.android.gradle.api)
    compileOnly(libs.asm.commons)

    testImplementation(libs.android.gradle.api)
    testImplementation(libs.asm.commons)
    testImplementation(libs.junit)

    "functionalTestImplementation"(gradleTestKit())
    "functionalTestImplementation"(libs.junit)
    // The test projects apply AGP without a version, so it must load beside the plugin under test.
    functionalTestAgp(libs.android.gradle.plugin)
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(functionalTestAgp)
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Builds Android projects that apply the plugin and checks the results."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    systemProperty("dari.localProperties", rootDir.resolve("../local.properties").absolutePath)
}

tasks.check {
    dependsOn(functionalTestTask)
}

gradlePlugin {
    testSourceSets(functionalTest)
    plugins {
        register("dari") {
            id = "io.github.easyhooon.dari"
            implementationClass = "com.easyhooon.dari.gradle.DariPlugin"
            displayName = "Dari"
            description = "Captures WebView bridge messages for Dari from a one-time mapping, without per-call code"
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("../detekt-config.yml"))
}

ktlint {
    version.set(libs.versions.ktlintSource.get())
    verbose.set(true)
}

mavenPublishing {
    coordinates(
        groupId = "io.github.easyhooon",
        artifactId = "dari-gradle-plugin",
        version = libs.versions.dari.get(),
    )

    pom {
        name.set("Dari Gradle Plugin")
        description.set("Captures WebView bridge messages for Dari from a one-time mapping, without per-call code")
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
