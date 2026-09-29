import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
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
        // Phones (64- and 32-bit ARM) and CI's x86_64 emulator. Vosk and JNA (task 3.5) also ship x86, armeabi and
        // mips builds, about 10 MB of APK that no supported device runs.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
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

/** Runs Node tools from Gradle. `npm ci` only runs when the lockfile is newer than the last install. */
abstract class NodeTask : DefaultTask() {
  @get:Inject protected abstract val exec: ExecOperations

  @get:Internal protected val npm: List<String>
    get() = if (System.getProperty("os.name").startsWith("Windows")) listOf("cmd", "/c", "npm") else listOf("npm")

  protected fun npmCiIfStale(dir: File) {
    val installed = File(dir, "node_modules/.package-lock.json")
    if (installed.exists() && installed.lastModified() >= File(dir, "package-lock.json").lastModified()) return
    exec.exec {
      workingDir(dir)
      commandLine(npm + listOf("ci", "--no-audit", "--no-fund"))
    }
  }
}

/**
 * Builds web/character (Vite + three-vrm) into generated assets under character/, so every APK, CI's included, ships
 * the page that matches its sources. Needs Node and npm on PATH.
 */
abstract class CharacterWebBuild : NodeTask() {
  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sources: ConfigurableFileCollection

  @get:Internal abstract val webDir: DirectoryProperty

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @TaskAction
  fun build() {
    val dir = webDir.get().asFile
    npmCiIfStale(dir)
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
abstract class RinModelBuild : NodeTask() {
  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val model: RegularFileProperty

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val tool: ConfigurableFileCollection

  @get:Input abstract val mode: Property<String>

  @get:Internal abstract val toolDir: DirectoryProperty

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @get:OutputFile abstract val report: RegularFileProperty

  @TaskAction
  fun build() {
    val dir = toolDir.get().asFile
    npmCiIfStale(dir)
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

/**
 * Renders the still images (task 2.5): one transparent WebP per mood at character/stills/, drawn by the built page
 * itself in a headless Chrome or Edge (tools/character/render-stills.mjs), so a still matches the live strip. The app
 * shows them while Rin loads and whenever the 3D page cannot run. Like the model, they never enter the repo.
 */
abstract class CharacterStills : NodeTask() {
  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val model: RegularFileProperty

  @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val page: DirectoryProperty

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val tool: ConfigurableFileCollection

  @get:Internal abstract val toolDir: DirectoryProperty

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @TaskAction
  fun render() {
    val dir = toolDir.get().asFile
    npmCiIfStale(dir)
    val out = File(outputDir.get().asFile, "character/stills")
    out.deleteRecursively() // a mood removed from moods.json must not leave its old still behind
    exec.exec {
      workingDir(dir)
      commandLine("node", "render-stills.mjs", page.get().asFile.absolutePath, model.get().asFile.absolutePath, out.absolutePath)
    }
  }
}

fun registerStills(name: String, vrm: Provider<RegularFile>) =
  tasks.register<CharacterStills>(name) {
    val tools = rootProject.layout.projectDirectory.dir("tools/character")
    model.set(vrm)
    page.set(characterWeb.flatMap { it.outputDir.dir("character") })
    toolDir.set(tools)
    tool.from(tools.asFileTree.matching { include("render-stills.mjs", "package-lock.json") })
    outputDir.set(layout.buildDirectory.dir("generated/$name"))
  }

// Rin's stills go in every build that carries her model. Without it, debug builds on this PC render the VRoid sample's
// (git-ignored, like the sample); CI and clones have neither and show the silhouette.
val rinStills = rinModel?.let { task -> registerStills("renderRinStills", task.flatMap { it.outputDir.file("character/model/rin.vrm") }) }
val devModel = layout.projectDirectory.file("src/debug/assets/character/model/dev.vrm")
val devStills = if (rinModel == null && devModel.asFile.isFile) registerStills("renderDevStills", provider { devModel }) else null

/**
 * Checks that her arms stay out of her body through every gesture, on the build's model (tools/character/
 * check-gestures.mjs, task 2.5): fails above 10 mm, and leaves front/side/above sheets in build/reports/gesture-check.
 * Not part of assemble. For a new model: run with -PwriteBody to re-measure web/character/src/body.json, rebuild,
 * then run it again.
 */
abstract class GestureCheck : NodeTask() {
  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val model: RegularFileProperty

  @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val page: DirectoryProperty

  @get:Internal abstract val toolDir: DirectoryProperty

  @get:Internal abstract val reportDir: DirectoryProperty

  @get:Internal abstract val bodyFile: RegularFileProperty

  @get:Input abstract val writeBody: Property<Boolean>

  @TaskAction
  fun check() {
    val dir = toolDir.get().asFile
    npmCiIfStale(dir)
    val body = if (writeBody.get()) listOf("--write-body", bodyFile.get().asFile.absolutePath) else emptyList()
    exec.exec {
      workingDir(dir)
      commandLine(
        listOf("node", "check-gestures.mjs", page.get().asFile.absolutePath, model.get().asFile.absolutePath) +
          listOf("--out", reportDir.get().asFile.absolutePath) + body,
      )
    }
  }
}

(rinModel?.flatMap { it.outputDir.file("character/model/rin.vrm") } ?: devModel.takeIf { it.asFile.isFile }?.let { provider { it } })
  ?.let { vrm ->
    tasks.register<GestureCheck>("checkGestures") {
      model.set(vrm)
      page.set(characterWeb.flatMap { it.outputDir.dir("character") })
      toolDir.set(rootProject.layout.projectDirectory.dir("tools/character"))
      reportDir.set(layout.buildDirectory.dir("reports/gesture-check"))
      bodyFile.set(rootProject.layout.projectDirectory.file("web/character/src/body.json"))
      writeBody.set(providers.gradleProperty("writeBody").isPresent)
      outputs.upToDateWhen { false } // a check: always runs when asked
    }
  }

/**
 * Fetches Vosk's small en-US model (Apache-2.0, ~41 MB zip) for Repeat after Rin (task 3.5, S3's pick O10) and unpacks
 * it into generated assets at vosk/, so every build, CI's included, can listen offline. The zip is checked against its
 * SHA-256 and cached under .gradle/vosk (git-ignored), so it downloads once per checkout.
 */
abstract class VoskModelFetch : DefaultTask() {
  @get:Input abstract val url: Property<String>

  @get:Input abstract val sha256: Property<String>

  @get:Internal abstract val cacheDir: DirectoryProperty

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @TaskAction
  fun fetch() {
    val zip = File(cacheDir.get().asFile, url.get().substringAfterLast('/'))
    if (!zip.isFile || digest(zip) != sha256.get()) {
      zip.parentFile.mkdirs()
      val part = File(zip.path + ".part")
      URI(url.get()).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
      val got = digest(part)
      if (got != sha256.get()) {
        part.delete()
        throw GradleException("Vosk model checksum mismatch: got $got, want ${sha256.get()}")
      }
      part.renameTo(zip) || throw GradleException("could not move ${part.name} into place")
    }
    val out = File(outputDir.get().asFile, "vosk")
    out.deleteRecursively()
    ZipFile(zip).use { z ->
      z.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
        // Drop the zip's top folder (vosk-model-small-en-us-0.15/) and its README.
        val path = entry.name.substringAfter('/')
        if (path.isEmpty() || path == "README") return@forEach
        val target = File(out, path)
        require(target.canonicalPath.startsWith(out.canonicalPath)) { "zip entry outside the model: ${entry.name}" }
        target.parentFile.mkdirs()
        z.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
      }
    }
  }

  private fun digest(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(1 shl 16)
      while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        md.update(buffer, 0, n)
      }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }
}

val voskModel =
  tasks.register<VoskModelFetch>("fetchVoskModel") {
    url.set("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip")
    sha256.set("30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498")
    cacheDir.set(rootProject.layout.projectDirectory.dir(".gradle/vosk"))
    outputDir.set(layout.buildDirectory.dir("generated/voskModel"))
  }

// MoodContractTest reads the page's mood table, so a change there must rerun the unit tests.
tasks.withType<Test>().configureEach {
  inputs.file(rootProject.layout.projectDirectory.file("web/character/src/moods.json"))
    .withPathSensitivity(PathSensitivity.RELATIVE)
    .withPropertyName("pageMoods")
  // RepeatRescoreTest (task 3.5): -Prepeat.results=<lab replay jsonl>[,...] re-judges a replay; skipped without it.
  val repeatResults = providers.gradleProperty("repeat.results").orElse("")
  inputs.property("repeatResults", repeatResults)
  systemProperty("repeat.results", repeatResults.get())
}

androidComponents {
  onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(characterWeb, CharacterWebBuild::outputDir)
    variant.sources.assets?.addGeneratedSourceDirectory(voskModel, VoskModelFetch::outputDir)
    rinModel?.let { variant.sources.assets?.addGeneratedSourceDirectory(it, RinModelBuild::outputDir) }
    rinStills?.let { variant.sources.assets?.addGeneratedSourceDirectory(it, CharacterStills::outputDir) }
    if (variant.buildType == "debug") {
      devStills?.let { variant.sources.assets?.addGeneratedSourceDirectory(it, CharacterStills::outputDir) }
    }
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

  // QR mission (task 3.2): CameraX preview + analysis, ML Kit's bundled scanner (in the APK, so it works offline
  // from the first launch; the Play Services variant downloads its model later), and qrcodegen to draw the sticker.
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.compose)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.qrcodegen)

  // Repeat after Rin (task 3.5): Vosk, offline speech recognition limited to each sentence's words (S3, O10). Its
  // native library comes through JNA's Android build (the aar, as vosk-android's own pom asks).
  implementation(libs.vosk.android)
  implementation(libs.jna) { artifact { type = "aar" } }

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
