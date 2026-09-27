package io.github.earthkodyai.rinalarm.character

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import java.time.LocalTime
import kotlinx.coroutines.delay

/**
 * Rin's moods (plan 05a). The page draws each one from web/character/src/moods.json; MoodContractTest keeps this
 * list and that table the same.
 */
enum class Mood(val wire: String) {
  SLEEPY("sleepy"),
  CHEERFUL("cheerful"),
  PROUD("proud"),
  WORRIED("worried"),
  POUTY("pouty"),
  SULKY("sulky"),
  RELIEVED("relieved");

  companion object {
    fun fromWire(wire: String): Mood? = entries.firstOrNull { it.wire == wire }
  }
}

/** Her mood on the main screen until the Phase 3 emotion engine drives it: sleepy late at night, cheerful otherwise. */
object DefaultMood {
  private val NIGHT_START: LocalTime = LocalTime.of(22, 0)
  private val NIGHT_END: LocalTime = LocalTime.of(6, 0)

  fun at(time: LocalTime): Mood = if (time >= NIGHT_START || time < NIGHT_END) Mood.SLEEPY else Mood.CHEERFUL
}

/** DefaultMood for the current local time, rechecked at every minute boundary. */
@Composable
fun rememberDefaultMood(): Mood {
  val mood by
    produceState(DefaultMood.at(LocalTime.now())) {
      while (true) {
        delay(60_000L - System.currentTimeMillis() % 60_000L)
        value = DefaultMood.at(LocalTime.now())
      }
    }
  return mood
}
