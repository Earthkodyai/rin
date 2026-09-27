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
    // Embedder models are downloaded into ../model by hand (git-ignored), see s5.sh models.
    sourceSets["main"].assets.srcDirs("../model")
    androidResources { noCompress += "tflite" }
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
    implementation(libs.play.services.tasks)
    // Part B: on-device image embeddings of the tester's own objects.
    implementation(libs.mediapipe.tasks.vision)
    testImplementation(libs.junit)
}
