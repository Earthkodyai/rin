package io.github.earthkodyai.rinalarm.alarm.log

import io.github.earthkodyai.rinalarm.alarm.log.ReliabilityRun.Reason
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityRunTest {
  private val zone = ZoneId.of("Asia/Bangkok")
  private val today = LocalDate.of(2026, 10, 14)

  /** A ring scheduled [daysAgo] days before [today] at [at], firing [lateMs] late with sound after 150 ms. */
  private fun ring(
    daysAgo: Long,
    lateMs: Long? = 50,
    at: LocalTime = LocalTime.of(6, 30),
    toSoundMs: Long? = 150,
    outcome: RingOutcome = RingOutcome.DISMISSED,
    isTest: Boolean = false,
    alarmId: Long = 1,
  ): RingSummary {
    val scheduled = today.minusDays(daysAgo).atTime(at).atZone(zone).toInstant()
    val fired = scheduled.plusMillis(lateMs ?: 0)
    return RingSummary(
      alarmId = alarmId,
      scheduledAt = if (lateMs == null && outcome != RingOutcome.MISSED) null else scheduled,
      firedAt = fired,
      late = lateMs?.let(Duration::ofMillis),
      toSound = toSoundMs?.let(Duration::ofMillis),
      outcome = outcome,
      isTest = isTest,
    )
  }

  private fun run(vararg rings: RingSummary) = ReliabilityRun.from(rings.toList(), today, zone)

  @Test
  fun emptyLog_isZeroWithNothingToBlame() {
    assertEquals(ReliabilityRun(0, null), run())
  }

  @Test
  fun fourteenGoodDays_pass() {
    val result = run(*Array(14) { ring(daysAgo = it.toLong()) })

    assertEquals(14, result.nights)
    assertTrue(result.passed)
    assertNull(result.breaker)
  }

  @Test
  fun todayWithoutARingYet_countsUpToYesterday() {
    val result = run(ring(3), ring(2), ring(1))

    assertEquals(ReliabilityRun(3, null), result)
    assertFalse(result.passed)
  }

  @Test
  fun aDayWithoutAnAlarm_resetsTheCount() {
    val result = run(ring(5), ring(4), ring(2), ring(1), ring(0))

    assertEquals(3, result.nights)
    assertEquals(ReliabilityRun.Breaker(today.minusDays(3), Reason.NO_ALARM, null), result.breaker)
  }

  @Test
  fun aLateRing_resetsTheCount_evenIfAnotherRingThatDayWasFine() {
    val result = run(ring(2), ring(1, lateMs = 61_000, alarmId = 2), ring(1, at = LocalTime.of(7, 0)), ring(0))

    assertEquals(1, result.nights)
    assertEquals(ReliabilityRun.Breaker(today.minusDays(1), Reason.LATE, 2), result.breaker)
  }

  @Test
  fun exactlyOneMinuteLate_stillCounts() {
    assertEquals(1, run(ring(0, lateMs = 60_000)).nights)
  }

  @Test
  fun missedSilentFailedOrUntimedRings_eachReset() {
    assertEquals(Reason.MISSED, run(ring(0, outcome = RingOutcome.MISSED, toSoundMs = null)).breaker?.reason)
    assertEquals(Reason.NO_SOUND, run(ring(0, toSoundMs = null, outcome = RingOutcome.UNKNOWN)).breaker?.reason)
    assertEquals(Reason.FAILED, run(ring(0, outcome = RingOutcome.FAILED)).breaker?.reason)
    assertEquals(Reason.NO_TIME, run(ring(0, lateMs = null)).breaker?.reason)
  }

  @Test
  fun anOverlappedRing_needsNoToneOfItsOwn() {
    assertEquals(1, run(ring(0), ring(0, toSoundMs = null, outcome = RingOutcome.OVERLAPPED, alarmId = 2)).nights)
  }

  @Test
  fun testRings_neitherCountNorBreak() {
    assertEquals(1, run(ring(1, isTest = true), ring(0, outcome = RingOutcome.FAILED, isTest = true), ring(0)).nights)
    // Only test rings yesterday: the day has no real ring.
    assertEquals(
      ReliabilityRun.Breaker(today.minusDays(1), Reason.NO_ALARM, null),
      run(ring(2), ring(1, isTest = true), ring(0)).breaker,
    )
  }

  @Test
  fun snoozedWakeUp_countsEveryRingOfTheDay() {
    val result = run(ring(0, outcome = RingOutcome.SNOOZED), ring(0, at = LocalTime.of(6, 35), lateMs = 90_000))

    assertEquals(Reason.LATE, result.breaker?.reason)
  }

  @Test
  fun aRingBelongsToTheDayItWasScheduledFor_inTheLocalZone() {
    // 23:59 local yesterday is still yesterday, even though it fires after midnight.
    val lateNight = ring(1, at = LocalTime.of(23, 59, 30), lateMs = 40_000)
    assertEquals(ReliabilityRun(1, null), run(lateNight))
  }
}
