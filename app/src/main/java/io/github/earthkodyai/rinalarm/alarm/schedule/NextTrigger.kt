package io.github.earthkodyai.rinalarm.alarm.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Works out when an alarm rings next. Plain java.time with no Android types, so every rule below is covered by
 * JVM unit tests (NextTriggerTest).
 *
 * An alarm is a wall-clock time in whatever zone the phone is in right now, not a fixed instant. After a timezone
 * or clock change the scheduler calls this again with the new zone.
 *
 * Rules:
 * - The result is strictly after `now`. Rescheduling at the moment an alarm rings therefore moves it to the next
 *   occurrence instead of ringing twice.
 * - One-shot alarms ring at the next occurrence of the time: today if it is still ahead, otherwise tomorrow.
 * - Repeating alarms ring on the first selected weekday, judged in the local zone, whose occurrence is still ahead.
 * - DST gap (the time does not exist that day, e.g. 02:30 when clocks jump from 02:00 to 03:00): ring when the gap
 *   ends, the first moment the wall clock is at or past the set time. The day is never skipped.
 * - DST overlap (the time happens twice): ring at the first occurrence only. Known trade-off: an alarm created
 *   during the repeated hour for a time that already passed once waits until the next day.
 */
object NextTrigger {
  fun after(now: Instant, time: LocalTime, days: RepeatDays, zone: ZoneId): Instant {
    val today = now.atZone(zone).toLocalDate()
    // Offsets 0..7: a repeat day that already passed today comes round again exactly one week later.
    for (offset in 0L..7L) {
      val date = today.plusDays(offset)
      if (!days.isOneShot && date.dayOfWeek !in days) continue
      val candidate = occurrence(date, time, zone)
      if (candidate.isAfter(now)) return candidate
    }
    error("No occurrence of $time on $days within 8 days of $now in $zone")
  }

  /** The instant the wall clock in [zone] first reaches [time] on [date]. */
  internal fun occurrence(date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
    val local = LocalDateTime.of(date, time)
    val transition = zone.rules.getTransition(local)
    return if (transition != null && transition.isGap) {
      transition.instant
    } else {
      // atZone resolves an overlap to the earlier offset, i.e. the first occurrence.
      local.atZone(zone).toInstant()
    }
  }
}
