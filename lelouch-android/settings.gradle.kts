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

rootProject.name = "lelouch-android"

include(":app")
include(":core:designsystem")
include(":core:model")
include(":core:database")
include(":core:network")
include(":core:domain")
include(":feature-presentation-mobile")
include(":feature-presentation-tv")
