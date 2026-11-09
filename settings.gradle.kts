// VerySmartTV: one Gradle project for the launcher and its two companions (DESIGN.md §5).
// Phase 0 registers the two library modules that have sources; :launcher and :traktshelf join in
// Phase 1 (carry + rename) and :livescore in Phase 4, each as `include(":name")` here plus one
// `plugins { id("verysmarttv.android.app") }` build file.
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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "verysmarttv"

include(":core")
include(":testkit")
