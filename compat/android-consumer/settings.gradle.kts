// Standalone build: resolves dari-core only through its published coordinate.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val dariRepo = providers.gradleProperty("dariRepo")
    .orElse(file("../../build/compat-repo").toURI().toString())

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Never fall back to Maven Central for Dari artifacts.
        exclusiveContent {
            forRepository { maven(dariRepo) }
            filter { includeGroup("io.github.easyhooon") }
        }
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "dari-android-consumer"
