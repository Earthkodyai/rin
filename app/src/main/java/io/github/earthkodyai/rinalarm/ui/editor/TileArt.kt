package io.github.earthkodyai.rinalarm.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import io.github.earthkodyai.rinalarm.alarm.AlarmSound
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.theme.RinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picture behind an editor tile (the user, 2026-10-02): what the game is, or what the theme sounds like. AI-made
 * pastel anime pictures (tools/tiles), `tiles/<id>.webp` in the APK, with a night version `tiles/<id>_night.webp` for
 * the night look; a tile without a night picture shows its day one, and a tile without either stays plain.
 */
object TileArt {
  fun of(choice: MissionChoice): String =
    when (choice) {
      MissionChoice.RinPicks -> "game_rin_picks"
      MissionChoice.None -> "none"
      is MissionChoice.Only ->
        when (choice.type) {
          MissionType.PADS -> "pads"
          MissionType.CUPS -> "cups"
          MissionType.SPEECH -> "speech"
        }
    }

  /** A theme by its own id (tiles/magic.webp for the magic theme). */
  fun of(sound: AlarmSound): String =
    when (sound) {
      AlarmSound.RinPicks -> "sound_rin_picks"
      AlarmSound.Beep -> "beep"
      is AlarmSound.Theme -> sound.id
    }

  fun path(id: String, night: Boolean = false) = if (night) "tiles/${id}_night.webp" else "tiles/$id.webp"
}

/** The tile's picture for the current look, decoded off the main thread; null while it loads or when there is none. */
@Composable
fun rememberTileArt(id: String): ImageBitmap? {
  val context = LocalContext.current
  val night = RinTheme.palette.night
  val image by
    produceState<ImageBitmap?>(null, id, night) {
      value =
        withContext(Dispatchers.IO) {
          fun load(path: String) = runCatching { context.assets.open(path).use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
          (if (night) load(TileArt.path(id, night = true)) else null) ?: load(TileArt.path(id))
        }
    }
  return image
}
