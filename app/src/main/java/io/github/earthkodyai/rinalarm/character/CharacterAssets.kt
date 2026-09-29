package io.github.earthkodyai.rinalarm.character

import android.content.Context

/** How much of Rin the page shows; the page frames her from `frame=` (web/character/src/main.ts frame()). */
enum class Framing(val wire: String, internal val stillDir: String) {
  /** Head and shoulders: the strip above the alarm list. */
  STRIP("strip", ""),
  /** Head to toe: the ring screen (task 3.1), where clap and pout show. */
  FULL("full", "full/"),
}

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
   * Gradle, tools/character/render-stills.mjs), in [framing]. Empty in builds without a model, which show the
   * silhouette.
   */
  fun stills(context: Context, framing: Framing = Framing.STRIP): Map<Mood, String> =
    pickStills(list(context, "$STILL_DIR/${framing.stillDir}".trimEnd('/')), framing)

  fun pickStills(available: Collection<String>, framing: Framing = Framing.STRIP): Map<Mood, String> =
    Mood.entries
      .mapNotNull { mood ->
        "${mood.wire}.webp".takeIf { it in available }?.let { mood to "$STILL_DIR/${framing.stillDir}$it" }
      }
      .toMap()

  /**
   * Rin's hand for the colour pads (task 3.3), rendered with the stills: seen from above, fingertip at the bottom
   * centre. Null in builds without a model, which draw a plain hand.
   */
  fun hand(context: Context): String? = "$STILL_DIR/$HAND".takeIf { HAND in list(context, STILL_DIR) }

  private const val HAND = "hand.webp"

  private fun list(context: Context, dir: String): List<String> =
    runCatching { context.assets.list(dir)?.toList() }.getOrNull().orEmpty()
}
