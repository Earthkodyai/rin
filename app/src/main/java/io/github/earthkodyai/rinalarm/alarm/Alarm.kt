package io.github.earthkodyai.rinalarm.alarm

import io.github.earthkodyai.rinalarm.alarm.schedule.NextTrigger
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * One alarm as the rest of the app sees it.
 *
 * @property isTest the Diagnostics "test alarm": rings through the real path, but is hidden from the alarm list,
 *   deleted once it has rung, and left out of the reliability numbers.
 */
data class Alarm(
  val id: Long = 0,
  val time: LocalTime,
  val repeatDays: RepeatDays = RepeatDays.NONE,
  val label: String = "",
  val enabled: Boolean = true,
  val ring: RingOptions = RingOptions(),
  val isTest: Boolean = false,
) {
  /** When this alarm rings next, or null while it is switched off. */
  fun nextTrigger(now: Instant, zone: ZoneId): Instant? =
    if (enabled) NextTrigger.after(now, time, repeatDays, zone) else null
}

/**
 * How an alarm rings. The defaults were chosen by the user in task 1.2; the editor (1.3) changes them per alarm.
 *
 * @property rampSeconds seconds from the quiet start to full volume; 0 = full volume at once.
 * @property maxSnoozes snoozes allowed per wake-up; once they are used up the Snooze button disappears.
 */
data class RingOptions(
  val rampSeconds: Int = DEFAULT_RAMP_SECONDS,
  val vibrate: Boolean = true,
  val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
  val maxSnoozes: Int = DEFAULT_MAX_SNOOZES,
) {
  init {
    require(rampSeconds >= 0) { "rampSeconds must be >= 0: $rampSeconds" }
    require(snoozeMinutes >= 1) { "snoozeMinutes must be >= 1: $snoozeMinutes" }
    require(maxSnoozes >= 0) { "maxSnoozes must be >= 0: $maxSnoozes" }
  }

  companion object {
    // Repeated as string literals in AlarmEntity's @ColumnInfo defaults, which Room needs at compile time.
    const val DEFAULT_RAMP_SECONDS = 30
    const val DEFAULT_SNOOZE_MINUTES = 5
    const val DEFAULT_MAX_SNOOZES = 3
  }
}
