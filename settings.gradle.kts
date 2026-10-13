pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "MyBleSense"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":domain")
include(":domain:testing")
include(":core:common")
include(":core:bluetooth")
include(":core:datastore")
include(":core:designsystem")
include(":feature:scanner")
include(":feature:device-details")
include(":feature:gatt-inspector")
include(":shared:app")
