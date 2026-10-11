pluginManagement {
    repositories {
        // Only needed for the Android test module (-Pandroid); nothing else is fetched from here.
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
    // Build-time only: lets Gradle download the JDK 17 toolchain if it is not installed.
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
}

rootProject.name = "promptparse-jvm"

include(":core", ":examples:java")

// Instrumented tests on an Android runtime. Needs an Android SDK, so it is opt-in: ./gradlew -Pandroid ...
if (providers.gradleProperty("android").isPresent) {
    include(":android-tests")
}
