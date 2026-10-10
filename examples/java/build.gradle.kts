plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
}

dependencies {
    implementation(project(":core"))
}

// The example asserts its own output, so running it as part of `check` proves Java interop.
val runExample by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the Java example; fails the build if any assertion in it fails."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.sunjirapath.promptparse.example.Example"
}

tasks.check {
    dependsOn(runExample)
}
