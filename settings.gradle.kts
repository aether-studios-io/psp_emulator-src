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
        // LibretroDroid (GPL-3.0) is published only through JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Swordfish90") }
        }
    }
}

rootProject.name = "gameemulator"
include(":app")
