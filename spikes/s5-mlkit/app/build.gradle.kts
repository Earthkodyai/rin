plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.rinalarm.spike.s5"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.rinalarm.spike.s5"
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
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    // Bundled base model (~5.7 MB): works offline from first launch, no Play Services download.
    implementation(libs.mlkit.image.labeling)
    testImplementation(libs.junit)
}
