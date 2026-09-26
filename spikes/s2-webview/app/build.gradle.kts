plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.rinalarm.spike.s2"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.rinalarm.spike.s2"
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
    // web/ builds into build/web (npm run build); the model is dropped into ../model by hand.
    sourceSets["main"].assets.srcDirs("build/web", "../model")
    // Stored uncompressed in the APK, as the real app would, so the load isn't paying for inflate.
    androidResources { noCompress += listOf("vrm") }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.webkit)
}
