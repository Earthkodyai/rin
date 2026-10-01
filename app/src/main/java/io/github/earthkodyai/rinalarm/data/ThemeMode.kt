package io.github.earthkodyai.rinalarm.data

import java.time.LocalTime

/**
 * The app's own day or night look (UX phase, the user's pick, 2026-10-01): it follows the time of day unless the user
 * fixes it in Settings, and never the phone's dark mode.
 */
enum class ThemeMode(val stored: String) {
  AUTO("auto"),
  DAY("day"),
  NIGHT("night");

  /** Whether the night look shows at [time]. */
  fun isNight(time: LocalTime): Boolean =
    when (this) {
      AUTO -> time.hour >= NIGHT_FROM_HOUR || time.hour < DAY_FROM_HOUR
      DAY -> false
      NIGHT -> true
    }

  companion object {
    /** Auto: the night look from 19:00, the day look from 06:00 (bedtime set-ups and dawn rings look like night). */
    const val NIGHT_FROM_HOUR = 19
    const val DAY_FROM_HOUR = 6

    fun fromStored(stored: String?): ThemeMode = entries.firstOrNull { it.stored == stored } ?: AUTO
  }
}
