import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

android {
    namespace = "io.github.earthkodyai.rinalarm"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.earthkodyai.rinalarm"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Swaps in HiltTestApplication so receiver tests can replace storage and the ring outputs (androidTest/HiltTestRunner.kt).
        testInstrumentationRunner = "io.github.earthkodyai.rinalarm.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    lint {
        warningsAsErrors = true
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

/**
 * Builds web/character (Vite + three-vrm) into generated assets under character/, so every APK, CI's included, ships
 * the page that matches its sources. Needs Node and npm on PATH. Runs `npm ci` only when the lockfile changed.
 */
abstract class CharacterWebBuild @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sources: ConfigurableFileCollection

  @get:Internal abstract val webDir: DirectoryProperty

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @TaskAction
  fun build() {
    val npm = if (System.getProperty("os.name").startsWith("Windows")) listOf("cmd", "/c", "npm") else listOf("npm")
    val dir = webDir.get().asFile
    val installed = File(dir, "node_modules/.package-lock.json")
    if (!installed.exists() || installed.lastModified() < File(dir, "package-lock.json").lastModified()) {
      exec.exec {
        workingDir(dir)
        commandLine(npm + listOf("ci", "--no-audit", "--no-fund"))
      }
    }
    val out = File(outputDir.get().asFile, "character").absolutePath
    exec.exec {
      workingDir(dir)
      commandLine(npm + listOf("run", "build", "--", "--outDir", out))
    }
  }
}

val characterWeb =
  tasks.register<CharacterWebBuild>("buildCharacterWeb") {
    val web = rootProject.layout.projectDirectory.dir("web/character")
    webDir.set(web)
    sources.from(
      web.dir("src"),
      web.file("index.html"),
      web.file("package.json"),
      web.file("package-lock.json"),
      web.file("vite.config.ts"),
      web.file("tsconfig.json"),
    )
  }

androidComponents {
  onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(characterWeb, CharacterWebBuild::outputDir)
  }
}

room {
    // Exported schemas are committed so every future migration can be tested against them.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  // Declared (not just inherited from AndroidX) so the app and kotlinx-coroutines-test share one version; a mismatch
  // made every instrumented test fail with NoSuchMethodError (runBlockingK) on the first device run.
  implementation(libs.kotlinx.coroutines.android)
  // Same reason: room-testing's schema bundles need serialization >= 1.8.1, and the app would otherwise pull 1.7.3
  // (AbstractMethodError in MigrationTest on device).
  implementation(platform(libs.kotlinx.serialization.bom))

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // DI
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  androidTestImplementation(libs.hilt.android.testing)
  kspAndroidTest(libs.hilt.compiler)
  implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

  // Storage (device-protected, see data/StorageModule.kt)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.datastore.preferences)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.kotlinx.coroutines.test)

  // Character layer (task 2.1): WebViewAssetLoader and the origin-scoped message listener; JSON for the bridge
  implementation(libs.androidx.webkit)
  implementation(libs.kotlinx.serialization.json)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
