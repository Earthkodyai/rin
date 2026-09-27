package io.github.earthkodyai.rinalarm.alarm.engine

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.engine.RingPlanner.Step
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Bangkok (+07:00, no DST). Mon 28 Sep 2026. */
class RingPlannerTest {
  private val bangkok = ZoneId.of("Asia/Bangkok")
  private val daily = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = RepeatDays.EVERY_DAY)
  private val once = Alarm(id = 2, time = LocalTime.of(7, 0))

  // --- reconcile: fresh state ---

  @Test
  fun enabledAlarmWithoutPendingRing_isArmedAtItsNextTrigger() {
    assertEquals(
      listOf(Step.Arm(PendingRing(1, at("2026-09-28T07:00")))),
      RingPlanner.reconcile(listOf(daily), emptyList(), at("2026-09-28T06:00"), bangkok),
    )
  }

  @Test
  fun disabledAlarmWithoutPendingRing_staysUnarmed() {
    assertEquals(
      emptyList<Step>(),
      RingPlanner.reconcile(listOf(daily.copy(enabled = false)), emptyList(), at("2026-09-28T06:00"), bangkok),
    )
  }

  // --- reconcile: pending ring still ahead ---

  @Test
  fun futureRegularRing_isRearmedAtTheSameTime_whenNothingChanged() {
    val pending = PendingRing(1, at("2026-09-28T07:00"))
    assertEquals(
      listOf(Step.Arm(pending)),
      RingPlanner.reconcile(listOf(daily), listOf(pending), at("2026-09-28T06:00"), bangkok),
    )
  }

  @Test
  fun futureRegularRing_followsTheWallClock_afterATimezoneChange() {
    // Armed for 07:00 Bangkok; the phone is now in Tokyo (+09:00), where 07:00 is two hours earlier in absolute time.
    val pending = PendingRing(1, at("2026-09-28T07:00"))
    val tokyo = ZoneId.of("Asia/Tokyo")
    val now = LocalDateTime.parse("2026-09-28T05:00").atZone(tokyo).toInstant()
    assertEquals(
      listOf(Step.Arm(PendingRing(1, LocalDateTime.parse("2026-09-28T07:00").atZone(tokyo).toInstant()))),
      RingPlanner.reconcile(listOf(daily), listOf(pending), now, tokyo),
    )
  }

  @Test
  fun futureSnooze_keepsItsInstant_evenAfterATimezoneChange() {
    val snooze = PendingRing(1, at("2026-09-28T07:05"), snoozeCount = 1)
    val regular = PendingRing(1, at("2026-09-29T07:00"))
    val steps = RingPlanner.reconcile(listOf(daily), listOf(snooze, regular), at("2026-09-28T07:02"), ZoneId.of("Asia/Tokyo"))
    assertEquals(Step.Arm(snooze), steps.first())
  }

  // --- reconcile: pending ring already due ---

  @Test
  fun ringDueWithinTheLateWindow_isArmedAtItsOriginalTime_soItFiresAtOnce() {
    val pending = PendingRing(1, at("2026-09-28T07:00"))
    assertEquals(
      listOf(Step.Arm(pending)),
      RingPlanner.reconcile(listOf(daily), listOf(pending), at("2026-09-28T07:15"), bangkok),
    )
  }

  @Test
  fun ringDueLongerThanTheLateWindow_isMissed() {
    val pending = PendingRing(1, at("2026-09-28T07:00"))
    assertEquals(
      listOf(Step.Missed(daily, pending)),
      RingPlanner.reconcile(listOf(daily), listOf(pending), at("2026-09-28T07:15:00.001"), bangkok),
    )
  }

  @Test
  fun missedRegularRing_doesNotGetASecondArmStep_theEngineMovesTheAlarmOn() {
    val pending = PendingRing(2, at("2026-09-27T07:00"))
    val steps = RingPlanner.reconcile(listOf(once), listOf(pending), at("2026-09-28T09:00"), bangkok)
    assertEquals(listOf(Step.Missed(once, pending)), steps)
  }

  // --- reconcile: stale pending rings ---

  @Test
  fun pendingRingOfADeletedAlarm_isDisarmed() {
    assertEquals(
      listOf(Step.Disarm(9, snooze = true)),
      RingPlanner.reconcile(emptyList(), listOf(PendingRing(9, at("2026-09-28T07:05"), 1)), at("2026-09-28T07:00"), bangkok),
    )
  }

  @Test
  fun regularRingOfASwitchedOffAlarm_isDisarmed() {
    assertEquals(
      listOf(Step.Disarm(1, snooze = false)),
      RingPlanner.reconcile(
        listOf(daily.copy(enabled = false)),
        listOf(PendingRing(1, at("2026-09-28T07:00"))),
        at("2026-09-28T06:00"),
        bangkok,
      ),
    )
  }

  @Test
  fun snoozeOfAOneShotAlarm_survives_eventhoughTheAlarmSwitchedItselfOff() {
    val snooze = PendingRing(2, at("2026-09-28T07:05"), snoozeCount = 1)
    assertEquals(
      listOf(Step.Arm(snooze)),
      RingPlanner.reconcile(listOf(once.copy(enabled = false)), listOf(snooze), at("2026-09-28T07:01"), bangkok),
    )
  }

  // --- after a ring ---

  @Test
  fun afterRegularRing_repeatingAlarmArmsTheNextDay() {
    assertEquals(
      PendingRing(1, at("2026-09-29T07:00")),
      RingPlanner.afterRegularRing(daily, at("2026-09-28T07:00:00.850"), bangkok),
    )
  }

  @Test
  fun afterRegularRing_weekdayAlarmOnFridaySkipsToMonday() {
    val weekdays = daily.copy(repeatDays = RepeatDays.WEEKDAYS)
    assertEquals(
      PendingRing(1, at("2026-10-05T07:00")),
      RingPlanner.afterRegularRing(weekdays, at("2026-10-02T07:00:01"), bangkok),
    )
  }

  @Test
  fun afterRegularRing_oneShotAlarmHasNoNextRing() {
    assertNull(RingPlanner.afterRegularRing(once, at("2026-09-28T07:00"), bangkok))
  }

  // --- snooze ---

  @Test
  fun snooze_isFiveMinutesFromTheTap_andCountsUp() {
    assertEquals(
      PendingRing(1, at("2026-09-28T07:03:30").plusSeconds(300), snoozeCount = 1),
      RingPlanner.snooze(daily, snoozeCount = 0, now = at("2026-09-28T07:03:30")),
    )
  }

  @Test
  fun snooze_stopsAtTheCapOfThree() {
    val now = at("2026-09-28T07:15")
    assertEquals(3, RingPlanner.snooze(daily, snoozeCount = 2, now = now)?.snoozeCount)
    assertNull(RingPlanner.snooze(daily, snoozeCount = 3, now = now))
    assertEquals(0, RingPlanner.snoozesLeft(daily, 3))
    assertEquals(3, RingPlanner.snoozesLeft(daily, 0))
  }

  @Test
  fun snooze_withCapZero_isNeverAllowed() {
    val strict = daily.copy(ring = RingOptions(maxSnoozes = 0))
    assertNull(RingPlanner.snooze(strict, snoozeCount = 0, now = at("2026-09-28T07:00")))
  }

  private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(bangkok).toInstant()

  @Test
  fun testRingTime_isTheFirstWholeMinuteAtLeastAMinuteAway() {
    val zone = ZoneId.of("Asia/Bangkok")
    fun ring(local: String) = RingPlanner.testRingTime(LocalDateTime.parse(local).atZone(zone).toInstant(), zone)
    assertEquals(LocalTime.of(6, 1), ring("2026-09-28T06:00:00"))
    assertEquals(LocalTime.of(6, 2), ring("2026-09-28T06:00:00.001"))
    assertEquals(LocalTime.of(6, 2), ring("2026-09-28T06:00:59"))
    assertEquals(LocalTime.of(0, 1), ring("2026-09-28T23:59:30"))
  }
}
