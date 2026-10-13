plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("core/src/**/*.kt")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target(
            "*.gradle.kts",
            "core/*.gradle.kts",
            "examples/**/*.gradle.kts",
            "android-tests/*.gradle.kts",
            "artifact-tests/*.gradle.kts",
        )
        ktlint(ktlintVersion)
    }
    java {
        target("examples/**/*.java", "android-tests/**/*.java", "artifact-tests/**/*.java")
        googleJavaFormat(libs.versions.googleJavaFormat.get())
    }
}

apiValidation {
    // The Java example is a consumer of the library, not part of its public API.
    ignoredProjects.add("java")
}
