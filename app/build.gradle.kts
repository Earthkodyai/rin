import java.util.Properties
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

/**
 * Runs Rin's own model through tools/character/optimize-vrm.mjs (KTX2 textures, small thumbnail, the checks Rin needs)
 * into generated assets at character/model/rin.vrm. The model never enters the public repo: `rin.model` in
 * local.properties points at the VRoid export on this PC. Builds without it (CI, clones) ship no Rin model and show
 * the still image; debug builds on this PC may still carry the VRoid sample (app/src/debug/assets, git-ignored).
 */
abstract class RinModelBuild @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val model: RegularFileProperty

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val tool: ConfigurableFileCollection

  @get:Input abstract val mode: Property<String>

  @get:Internal abstract val toolDir: DirectoryProperty

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @get:OutputFile abstract val report: RegularFileProperty

  @TaskAction
  fun build() {
    val npm = if (System.getProperty("os.name").startsWith("Windows")) listOf("cmd", "/c", "npm") else listOf("npm")
    val dir = toolDir.get().asFile
    val installed = File(dir, "node_modules/.package-lock.json")
    if (!installed.exists() || installed.lastModified() < File(dir, "package-lock.json").lastModified()) {
      exec.exec {
        workingDir(dir)
        commandLine(npm + listOf("ci", "--no-audit", "--no-fund"))
      }
    }
    val out = File(outputDir.get().asFile, "character/model/rin.vrm")
    exec.exec {
      workingDir(dir)
      commandLine(
        "node", "optimize-vrm.mjs", model.get().asFile.absolutePath, out.absolutePath,
        "--mode", mode.get(), "--report", report.get().asFile.absolutePath,
      )
    }
  }
}

val localProperties =
  Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
  }
val rinModel =
  localProperties.getProperty("rin.model")?.let { path ->
    val file = File(path)
    require(file.isFile) { "rin.model in local.properties points at $path, which is not a file" }
    tasks.register<RinModelBuild>("optimizeRinModel") {
      val tools = rootProject.layout.projectDirectory.dir("tools/character")
      model.set(file)
      mode.set(localProperties.getProperty("rin.model.mode", "etc1s"))
      toolDir.set(tools)
      tool.from(tools.asFileTree.matching { include("*.mjs", "package-lock.json") })
      outputDir.set(layout.buildDirectory.dir("generated/rinModel"))
      report.set(layout.buildDirectory.file("reports/rin-model.json"))
    }
  }

// MoodContractTest reads the page's mood table, so a change there must rerun the unit tests.
tasks.withType<Test>().configureEach {
  inputs.file(rootProject.layout.projectDirectory.file("web/character/src/moods.json"))
    .withPathSensitivity(PathSensitivity.RELATIVE)
    .withPropertyName("pageMoods")
}

androidComponents {
  onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(characterWeb, CharacterWebBuild::outputDir)
    rinModel?.let { variant.sources.assets?.addGeneratedSourceDirectory(it, RinModelBuild::outputDir) }
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
