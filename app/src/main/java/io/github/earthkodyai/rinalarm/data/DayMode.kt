package io.github.earthkodyai.rinalarm.data

import java.time.Duration

/** The two one-tap days on the home screen (Phase 5, D25): the next ring sounds, but with no game and no pouting. */
enum class DayModeKind(val stored: String) {
  REST("rest"),
  SICK("sick");

  companion object {
    fun fromStored(stored: String?): DayModeKind? = entries.firstOrNull { it.stored == stored }
  }
}

/**
 * A rest or sick day, tapped at [setAtMs] (wall clock). It covers the next ring, whenever that is (tap it at night for
 * the morning), and its snoozes; the ring's Dismiss (or auto stop) uses it up. A day mode nobody rang through lapses
 * after [LIFETIME]. One flag for one ring, never a record across days (D22).
 */
data class DayMode(val kind: DayModeKind, val setAtMs: Long) {
  fun activeAt(nowMs: Long): Boolean = nowMs - setAtMs in 0 until LIFETIME.toMillis()

  companion object {
    val LIFETIME: Duration = Duration.ofHours(24)
  }
}
