// VerySmartTV: one Gradle project for the launcher and its two companions (DESIGN.md §5).
// :core and :testkit are libraries, :launcher and :traktshelf the two carried apps; :livescore joins in
// Phase 4 as one more `include(":name")` here plus a `plugins { id("verysmarttv.android.app") }` build file.
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
include(":launcher")
include(":traktshelf")
