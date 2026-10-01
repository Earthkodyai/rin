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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picture behind an editor tile (the user, 2026-10-02): what the game is, or what the theme sounds like. AI-made
 * pastel anime pictures (tools/tiles), `tiles/<id>.webp` in the APK; a tile without one stays plain.
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

  fun path(id: String) = "tiles/$id.webp"
}

/** The tile's picture, decoded off the main thread; null while it loads or when the build has none. */
@Composable
fun rememberTileArt(id: String): ImageBitmap? {
  val context = LocalContext.current
  val image by
    produceState<ImageBitmap?>(null, id) {
      value =
        withContext(Dispatchers.IO) {
          runCatching { context.assets.open(TileArt.path(id)).use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
        }
    }
  return image
}
