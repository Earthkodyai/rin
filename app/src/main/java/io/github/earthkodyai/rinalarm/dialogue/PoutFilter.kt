package io.github.earthkodyai.rinalarm.dialogue

import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.character.Mood
import java.time.LocalDate

/** Her pouty moods, which scold off (G.1, "Pout off" before it) keeps off her face. */
val Mood.pouty: Boolean
  get() = this == Mood.POUTY || this == Mood.SULKY

/** Her pouty gestures, which scold off leaves out. */
val Gesture.pouty: Boolean
  get() = this == Gesture.POUT || this == Gesture.HUFF

/**
 * [book] without her pouty lines while [calm] (scold off, or a rest or sick day). An event pool draws again, so a head
 * tap still gets one of the others; a pool that holds only pouty lines (the snooze and scold ones) stays quiet, and
 * the games go on without waiting for her.
 */
class PoutFilter(private val book: LineBook, private val calm: () -> Boolean) : LineBook {
  override suspend fun pick(pool: String, day: LocalDate, morning: Any): Line? {
    if (!calm()) return book.pick(pool, day, morning)
    repeat(DRAWS) {
      val line = book.pick(pool, day, morning) ?: return null
      if (line.emotion?.pouty != true) return line
    }
    return null
  }

  private companion object {
    /** Enough for an event pool with one pouty line in five; a pool that is all pouty returns null anyway. */
    const val DRAWS = 4
  }
}
