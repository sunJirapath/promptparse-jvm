import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
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

// `-PtestJdk=11|17|21|25` runs the tests on that JDK while the build itself keeps using JDK 17 bytecode.
val testJdk = providers.gradleProperty("testJdk").orNull?.toInt()

tasks.test {
    useJUnitPlatform()
    if (testJdk != null) {
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(testJdk) }
    }
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

val noticeFiles = listOf(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
tasks.withType<Jar>().configureEach {
    from(noticeFiles) { into("META-INF") }
}

val noticeJars = listOf("jar", "sourcesJar", "dokkaJavadocJar")
val verifyJarNotices =
    tasks.register("verifyJarNotices") {
        group = "verification"
        description = "Checks that all published JARs contain the complete license and attribution."
        dependsOn(noticeJars)
        doLast {
            for (jar in noticeJars) {
                val archive =
                    tasks
                        .named<Jar>(jar)
                        .get()
                        .archiveFile
                        .get()
                        .asFile
                ZipFile(archive).use { zip ->
                    for (notice in noticeFiles) {
                        val entry =
                            checkNotNull(zip.getEntry("META-INF/${notice.name}")) {
                                "$jar is missing ${notice.name}"
                            }
                        check(zip.getInputStream(entry).use { it.readBytes() }.contentEquals(notice.readBytes())) {
                            "$jar contains an incomplete ${notice.name}"
                        }
                    }
                }
            }
        }
    }
tasks.check { dependsOn(verifyJarNotices) }

// Release configuration. Nothing is published from a normal build; see .github/workflows/release.yml.
mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"), sourcesJar = true))
    publishToMavenCentral()
    signAllPublications()
    // groupId (GROUP), version (VERSION_NAME) and artifactId (POM_ARTIFACT_ID) come from gradle.properties.

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
