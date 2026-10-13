plugins {
    application
}

repositories {
    maven {
        url = uri(providers.gradleProperty("smokeRepository").get())
        content { includeGroup(providers.gradleProperty("libraryGroup").get()) }
        // Test the POM that Maven consumers use, rather than Gradle module metadata.
        metadataSources {
            mavenPom()
            ignoreGradleMetadataRedirection()
        }
    }
    mavenCentral {
        content { excludeGroup(providers.gradleProperty("libraryGroup").get()) }
    }
}

dependencies {
    implementation(
        "${providers.gradleProperty("libraryGroup").get()}:" +
            "${providers.gradleProperty("libraryArtifact").get()}:${providers.gradleProperty("libraryVersion").get()}",
    )
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}
tasks.withType<JavaCompile>().configureEach { options.release = 11 }

// Reuse the real Java example without a project dependency or included library build.
sourceSets.main { java.srcDir("../examples/java/src/main/java") }
application { mainClass = "ArtifactSmokeTest" }
tasks.named<JavaExec>("run") {
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(providers.gradleProperty("testJdk").get().toInt())
        }
}
