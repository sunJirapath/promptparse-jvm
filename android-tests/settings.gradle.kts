pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
    versionCatalogs {
        // One place for versions: the root catalog.
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "android-tests"

// Use the library from this checkout instead of Maven Central.
includeBuild("..") {
    dependencySubstitution {
        substitute(module("io.github.sunjirapath:promptparse")).using(project(":core"))
    }
}
