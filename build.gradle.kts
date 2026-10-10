plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
    // The Java example is a consumer of the library, not part of its public API.
    ignoredProjects.add("java")
}
