pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "dari"
include(":sample")
include(":dari-core")
include(":dari-inspector-protocol")
include(":dari-runtime")
include(":dari")
include(":dari-desktop")
include(":dari-desktop-app")
include(":dari-noop")
