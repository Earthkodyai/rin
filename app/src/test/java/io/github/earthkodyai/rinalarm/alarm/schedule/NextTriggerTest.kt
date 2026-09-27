package io.github.earthkodyai.rinalarm.alarm.schedule

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dates used below (2026): Sun 27 Sep, Mon 28 Sep, Fri 2 Oct, Sun 4 Oct. US DST starts Sun 8 Mar and ends Sun 1 Nov,
 * UK DST starts Sun 29 Mar, Lord Howe Island (half-hour DST) starts Sun 4 Oct.
 */
class NextTriggerTest {

  // --- One-shot alarms ---

  @Test
  fun oneShot_laterToday_ringsToday() {
    assertEquals("2026-09-28T07:00+07:00", next(now = "2026-09-28T06:00", time = "07:00"))
  }

  @Test
  fun oneShot_alreadyPassedToday_ringsTomorrow() {
    assertEquals("2026-09-29T07:00+07:00", next(now = "2026-09-28T08:00", time = "07:00"))
  }

  @Test
  fun exactlyAtTriggerTime_returnsTheNextOccurrence_soARingNeverRepeats() {
    assertEquals("2026-09-29T07:00+07:00", next(now = "2026-09-28T07:00", time = "07:00"))
    assertEquals("2026-09-29T07:00+07:00", next(now = "2026-09-28T07:00:30", time = "07:00"))
  }

  @Test
  fun oneMillisecondBefore_ringsToday() {
    assertEquals("2026-09-28T07:00+07:00", next(now = "2026-09-28T06:59:59.999", time = "07:00"))
  }

  // --- Midnight ---

  @Test
  fun midnightAlarm_setLateEvening_ringsAtTheStartOfTomorrow() {
    assertEquals("2026-09-29T00:00+07:00", next(now = "2026-09-28T23:59", time = "00:00"))
  }

  @Test
  fun lateEveningAlarm_setJustAfterMidnight_ringsTheSameDay() {
    assertEquals("2026-09-28T23:59+07:00", next(now = "2026-09-28T00:00", time = "23:59"))
  }

  @Test
  fun repeatDay_isTheDayTheAlarmRingsOn_notTheDayItWasSet() {
    // Sunday 23:30, Monday-only alarm at 00:15: rings in 45 minutes, not a week later.
    assertEquals(
      "2026-09-28T00:15+07:00",
      next(now = "2026-09-27T23:30", time = "00:15", days = RepeatDays.of(MONDAY)),
    )
  }

  // --- Repeating alarms ---

  @Test
  fun weekdays_afterFridaysAlarm_skipsTheWeekend() {
    assertEquals(
      "2026-10-05T07:00+07:00",
      next(now = "2026-10-02T08:00", time = "07:00", days = RepeatDays.WEEKDAYS),
    )
  }

  @Test
  fun repeatDay_laterToday_ringsToday() {
    assertEquals(
      "2026-10-02T07:00+07:00",
      next(now = "2026-10-02T06:00", time = "07:00", days = RepeatDays.of(FRIDAY)),
    )
  }

  @Test
  fun singleRepeatDay_alreadyPassedToday_ringsExactlyOneWeekLater() {
    assertEquals(
      "2026-10-09T07:00+07:00",
      next(now = "2026-10-02T07:00", time = "07:00", days = RepeatDays.of(FRIDAY)),
    )
  }

  @Test
  fun repeatDays_areJudgedInTheLocalZone_notUtc() {
    // 01:00 Monday in Bangkok is still Sunday in UTC.
    assertEquals(
      "2026-09-28T06:00+07:00",
      next(now = "2026-09-28T01:00", time = "06:00", days = RepeatDays.of(MONDAY)),
    )
    assertEquals(
      "2026-10-04T06:00+07:00",
      next(now = "2026-09-28T01:00", time = "06:00", days = RepeatDays.of(SUNDAY)),
    )
  }

  @Test
  fun everyDay_behavesLikeADailyAlarm() {
    assertEquals(
      "2026-09-29T07:00+07:00",
      next(now = "2026-09-28T08:00", time = "07:00", days = RepeatDays.EVERY_DAY),
    )
  }

  // --- Timezone changes ---

  @Test
  fun sameMoment_differentZone_givesTheWallClockTimeInThatZone() {
    // 05:30 in Bangkok is 07:30 in Tokyo: a 07:00 alarm is still ahead in Bangkok but already past in Tokyo.
    val now = LocalDateTime.parse("2026-09-28T05:30").atZone(ZoneId.of(BANGKOK)).toInstant()
    val time = LocalTime.of(7, 0)
    assertEquals(
      "2026-09-28T07:00+07:00",
      NextTrigger.after(now, time, RepeatDays.NONE, ZoneId.of(BANGKOK)).offsetIn(BANGKOK),
    )
    assertEquals(
      "2026-09-29T07:00+09:00",
      NextTrigger.after(now, time, RepeatDays.NONE, ZoneId.of(TOKYO)).offsetIn(TOKYO),
    )
  }

  // --- DST gap: the set time does not exist that day ---

  @Test
  fun dstGap_ringsWhenTheGapEnds() {
    // New York jumps from 02:00 to 03:00; 02:30 never shows on the clock.
    assertEquals("2026-03-08T03:00-04:00", next(now = "2026-03-08T01:00", time = "02:30", zone = NEW_YORK))
  }

  @Test
  fun dstGap_daysAfter_ringAtTheNormalTime() {
    assertEquals(
      "2026-03-09T02:30-04:00",
      next(now = "2026-03-08T03:30", time = "02:30", days = RepeatDays.EVERY_DAY, zone = NEW_YORK),
    )
  }

  @Test
  fun dstGap_atTheBoundaryItself_ringsAtTheBoundary() {
    // London jumps from 01:00 to 02:00; an alarm set for exactly 01:00 is inside the gap.
    assertEquals("2026-03-29T02:00+01:00", next(now = "2026-03-28T22:00", time = "01:00", zone = LONDON))
  }

  @Test
  fun dstGap_ofHalfAnHour() {
    // Lord Howe Island jumps from 02:00 to 02:30.
    assertEquals("2026-10-04T02:30+11:00", next(now = "2026-10-04T01:00", time = "02:15", zone = LORD_HOWE))
  }

  @Test
  fun timesJustOutsideTheGap_areUnchanged() {
    assertEquals("2026-03-08T01:59-05:00", next(now = "2026-03-08T01:00", time = "01:59", zone = NEW_YORK))
    assertEquals("2026-03-08T03:00-04:00", next(now = "2026-03-08T01:00", time = "03:00", zone = NEW_YORK))
  }

  // --- DST overlap: the set time happens twice ---

  @Test
  fun dstOverlap_ringsAtTheFirstOccurrence() {
    // New York repeats 01:00-02:00, first at -04:00 then at -05:00.
    assertEquals("2026-11-01T01:30-04:00", next(now = "2026-11-01T00:00", time = "01:30", zone = NEW_YORK))
  }

  @Test
  fun dstOverlap_rescheduleAfterTheFirstRing_doesNotRingAgainAnHourLater() {
    val justAfterFirstRing = LocalDateTime.parse("2026-11-01T01:30:05").atZone(ZoneId.of(NEW_YORK)).toInstant()
    val next = NextTrigger.after(justAfterFirstRing, LocalTime.of(1, 30), RepeatDays.EVERY_DAY, ZoneId.of(NEW_YORK))
    assertEquals("2026-11-02T01:30-05:00", next.offsetIn(NEW_YORK))
  }

  @Test
  fun dstOverlap_alarmCreatedDuringTheRepeatedHour_waitsForTomorrow() {
    // Documented trade-off: at 01:10 on the second pass (-05:00) the first 01:20 (-04:00) is already gone.
    val secondPass =
      LocalDateTime.parse("2026-11-01T01:10").atZone(ZoneId.of(NEW_YORK)).withLaterOffsetAtOverlap().toInstant()
    val next = NextTrigger.after(secondPass, LocalTime.of(1, 20), RepeatDays.NONE, ZoneId.of(NEW_YORK))
    assertEquals("2026-11-02T01:20-05:00", next.offsetIn(NEW_YORK))
  }

  // --- Helpers ---

  private fun next(now: String, time: String, days: RepeatDays = RepeatDays.NONE, zone: String = BANGKOK): String {
    val zoneId = ZoneId.of(zone)
    val nowInstant = LocalDateTime.parse(now).atZone(zoneId).toInstant()
    return NextTrigger.after(nowInstant, LocalTime.parse(time), days, zoneId).offsetIn(zone)
  }

  private fun Instant.offsetIn(zone: String): String = atZone(ZoneId.of(zone)).toOffsetDateTime().toString()

  private companion object {
    const val BANGKOK = "Asia/Bangkok"
    const val TOKYO = "Asia/Tokyo"
    const val NEW_YORK = "America/New_York"
    const val LONDON = "Europe/London"
    const val LORD_HOWE = "Australia/Lord_Howe"
  }
}
