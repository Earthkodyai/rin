package io.github.earthkodyai.rinalarm.alarm

import io.github.earthkodyai.rinalarm.alarm.schedule.NextTrigger
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** One alarm as the rest of the app sees it. Ring options (ramp, vibrate, snooze) arrive with the editor in 1.3. */
data class Alarm(
  val id: Long = 0,
  val time: LocalTime,
  val repeatDays: RepeatDays = RepeatDays.NONE,
  val label: String = "",
  val enabled: Boolean = true,
) {
  /** When this alarm rings next, or null while it is switched off. */
  fun nextTrigger(now: Instant, zone: ZoneId): Instant? =
    if (enabled) NextTrigger.after(now, time, repeatDays, zone) else null
}
