// Runs the library's main behavior on a real Android runtime (ART) via instrumented tests.
// A separate Gradle build (see settings.gradle.kts) because it needs an Android SDK and an emulator or device:
//   ./gradlew -p android-tests connectedDebugAndroidTest
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.sunjirapath.promptparse.androidtests"
    compileSdk = 36

    defaultConfig {
        // The lowest API level the library is verified on (Animal Sniffer checks against the same level).
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("io.github.sunjirapath.promptparse:promptparse")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit4)
}
