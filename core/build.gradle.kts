import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.animalsniffer)
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
    signature("${libs.android.signature.get()}@signature")
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
                minBound(90, CoverageUnit.BRANCH)
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

// Stage the real publication's JAR and POM locally; signing and release publishing are untouched.
val smokeGroup = providers.gradleProperty("GROUP").get()
val smokeArtifact = providers.gradleProperty("POM_ARTIFACT_ID").get()
val smokeVersion = providers.gradleProperty("VERSION_NAME").get()
val smokeRepository = layout.buildDirectory.dir("artifact-smoke-repository")
val prepareArtifactSmokeRepository =
    tasks.register<Sync>("prepareArtifactSmokeRepository") {
        dependsOn(tasks.jar, "generatePomFileForMavenPublication")
        into(smokeRepository)
        val artifactPath = "${smokeGroup.replace('.', '/')}/$smokeArtifact/$smokeVersion"
        from(tasks.jar.flatMap { it.archiveFile }) {
            into(artifactPath)
            rename { "$smokeArtifact-$smokeVersion.jar" }
        }
        from(layout.buildDirectory.file("publications/maven/pom-default.xml")) {
            into(artifactPath)
            rename { "$smokeArtifact-$smokeVersion.pom" }
        }
    }
val artifactSmokeTest =
    tasks.register<GradleBuild>("artifactSmokeTest") {
        group = "verification"
        description = "Compiles and runs a standalone Java consumer using only the publication's Maven JAR and POM."
        dependsOn(prepareArtifactSmokeRepository)
        dir = rootProject.file("artifact-tests")
        tasks = listOf("run")
        startParameter.isOffline = gradle.startParameter.isOffline
        startParameter.isRefreshDependencies = true
        startParameter.projectProperties =
            mapOf(
                "smokeRepository" to smokeRepository.get().asFile.absolutePath,
                "libraryGroup" to smokeGroup,
                "libraryArtifact" to smokeArtifact,
                "libraryVersion" to smokeVersion,
                "testJdk" to (testJdk ?: 17).toString(),
                "org.gradle.java.installations.fromEnv" to
                    providers.gradleProperty("org.gradle.java.installations.fromEnv").getOrElse(""),
            )
    }
tasks.check { dependsOn(artifactSmokeTest) }

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

// Fails if the main code calls a JDK/Android API that does not exist on Android API 21.
// Kotlin stdlib and annotations are not part of the Android signature, so they are skipped.
animalsniffer {
    ignore("kotlin.*", "org.jetbrains.*")
}
