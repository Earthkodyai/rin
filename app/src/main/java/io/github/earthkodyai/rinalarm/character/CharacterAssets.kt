package io.github.earthkodyai.rinalarm.character

import android.content.Context

/** Where the character page and its model live in the APK (web/character is built into assets/character/). */
object CharacterAssets {
  const val ORIGIN = "https://appassets.androidplatform.net"
  const val PAGE = "$ORIGIN/assets/character/index.html"
  private const val MODEL_DIR = "character/model"

  /**
   * Model files in order of preference: Rin's own model (task 2.3), then the debug-only VRoid sample
   * (app/src/debug/assets, git-ignored). A build with neither shows the still image.
   */
  private val PREFERRED = listOf("rin.vrm", "dev.vrm")

  /** The model path relative to the page, or null when this build carries no model. */
  fun pickModel(available: Collection<String>): String? =
    PREFERRED.firstOrNull { it in available }?.let { "model/$it" }

  fun model(context: Context): String? =
    pickModel(runCatching { context.assets.list(MODEL_DIR)?.toList() }.getOrNull().orEmpty())
}
