plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.rinalarm.spike.s3"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.rinalarm.spike.s3"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Spike only: sign release with the debug key so it installs over adb.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // The Vosk model is unzipped into ../model by hand (git-ignored) and bundled as assets,
    // the way a fallback engine would have to ship.
    sourceSets["main"].assets.srcDirs("../model")
    // ./gradlew testDebugUnitTest -Ps3.results=<abs path to results jsonl> re-scores a replay on the PC.
    testOptions.unitTests.all { it.systemProperty("s3.results", (project.findProperty("s3.results") ?: "").toString()) }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vosk.android)
    implementation(libs.jna) { artifact { type = "aar" } }
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
