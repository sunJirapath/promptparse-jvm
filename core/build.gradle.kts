import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        allWarningsAsErrors = true
    }
}

// Fail at compile time on APIs newer than JDK 11, even though the build runs on a newer JDK.
tasks.compileKotlin {
    compilerOptions.freeCompilerArgs.add("-Xjdk-release=11")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.koverVerify)
}

// Configured but never invoked by `build`; nothing is published from this repository yet.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "promptparse", version.toString())

    pom {
        name = "promptparse-jvm"
        description = "Parse, generate and validate Thai PromptPay / EMVCo merchant-presented QR payloads."
        inceptionYear = "2026"
        url = "https://github.com/sunJirapath/promptparse-jvm"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "sunJirapath"
                name = "Sun"
                url = "https://github.com/sunJirapath"
            }
        }
        scm {
            url = "https://github.com/sunJirapath/promptparse-jvm"
            connection = "scm:git:git://github.com/sunJirapath/promptparse-jvm.git"
            developerConnection = "scm:git:ssh://git@github.com/sunJirapath/promptparse-jvm.git"
        }
    }
}
