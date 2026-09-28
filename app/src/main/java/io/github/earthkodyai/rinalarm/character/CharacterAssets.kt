package io.github.earthkodyai.rinalarm.character

import android.content.Context

/** Where the character page and its model live in the APK (web/character is built into assets/character/). */
object CharacterAssets {
  const val ORIGIN = "https://appassets.androidplatform.net"
  const val PAGE = "$ORIGIN/assets/character/index.html"
  private const val MODEL_DIR = "character/model"
  private const val STILL_DIR = "character/stills"

  /**
   * Model files in order of preference: Rin's own model (built from `rin.model` in local.properties, task 2.3), then
   * the debug-only VRoid sample (app/src/debug/assets, git-ignored). A build with neither shows the still image.
   */
  private val PREFERRED = listOf("rin.vrm", "dev.vrm")

  /** The model path relative to the page, or null when this build carries no model. */
  fun pickModel(available: Collection<String>): String? =
    PREFERRED.firstOrNull { it in available }?.let { "model/$it" }

  fun model(context: Context): String? = pickModel(list(context, MODEL_DIR))

  /**
   * The still image for each mood this build carries, as asset paths (task 2.5: rendered from the build's model by
   * Gradle, tools/character/render-stills.mjs). Empty in builds without a model, which show the silhouette.
   */
  fun stills(context: Context): Map<Mood, String> = pickStills(list(context, STILL_DIR))

  fun pickStills(available: Collection<String>): Map<Mood, String> =
    Mood.entries.mapNotNull { mood -> "${mood.wire}.webp".takeIf { it in available }?.let { mood to "$STILL_DIR/$it" } }
      .toMap()

  private fun list(context: Context, dir: String): List<String> =
    runCatching { context.assets.list(dir)?.toList() }.getOrNull().orEmpty()
}
