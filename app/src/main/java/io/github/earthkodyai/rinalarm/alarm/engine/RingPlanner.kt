package io.github.earthkodyai.rinalarm.alarm.engine

import io.github.earthkodyai.rinalarm.alarm.Alarm
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A ring registered with AlarmManager: the regular next ring of an alarm, or a snooze.
 *
 * @property snoozeCount snoozes already taken before this ring; 0 for a regular ring.
 */
data class PendingRing(val alarmId: Long, val triggerAt: Instant, val snoozeCount: Int = 0) {
  val isSnooze: Boolean
    get() = snoozeCount > 0
}

/**
 * The scheduling rules, as pure functions over stored alarms and pending rings (unit-tested in RingPlannerTest).
 * AlarmEngine applies the steps to Room and AlarmManager.
 */
object RingPlanner {
  /**
   * How late a ring may still sound after the phone was off or asleep through its time. It matches the 15-minute
   * auto-stop: a ring found later than that would already have stopped by itself.
   */
  val LATE_WINDOW: Duration = Duration.ofMinutes(15)

  sealed interface Step {
    /** Store [ring] and register it with AlarmManager, replacing any earlier registration for the same slot. */
    data class Arm(val ring: PendingRing) : Step

    /** Remove the slot from AlarmManager and storage. */
    data class Disarm(val alarmId: Long, val snooze: Boolean) : Step

    /** [ring] passed more than [LATE_WINDOW] ago: log it, tell the user, and move the alarm on. */
    data class Missed(val alarm: Alarm, val ring: PendingRing) : Step
  }

  /**
   * Brings the stored pending rings in line with the alarms after anything that may have broken them: boot, app
   * update, clock or timezone change, exact-alarm permission change, app start.
   *
   * - A pending ring whose alarm was deleted is disarmed. A regular ring of a switched-off alarm is disarmed; a
   *   snooze survives, because a one-shot alarm switches itself off when it fires but its snooze must still ring.
   * - Due within [LATE_WINDOW]: re-armed at its original time. AlarmManager fires a past time at once, so the late
   *   ring goes through the normal fire path and the log shows how late it was.
   * - Due longer ago: [Step.Missed].
   * - Still ahead: a snooze keeps its instant (it is a duration from the tap). A regular ring is recomputed from the
   *   wall clock, because after a timezone change 07:00 means 07:00 in the new zone.
   * - An enabled alarm with no regular ring gets one.
   */
  fun reconcile(alarms: List<Alarm>, pending: List<PendingRing>, now: Instant, zone: ZoneId): List<Step> {
    val byId = alarms.associateBy { it.id }
    val steps = mutableListOf<Step>()
    val covered = mutableSetOf<Long>()
    for (ring in pending) {
      val alarm = byId[ring.alarmId]
      if (alarm == null || (!ring.isSnooze && !alarm.enabled)) {
        steps += Step.Disarm(ring.alarmId, ring.isSnooze)
        continue
      }
      if (!ring.isSnooze) covered += alarm.id
      steps +=
        when {
          ring.triggerAt.isAfter(now) ->
            if (ring.isSnooze) Step.Arm(ring) else Step.Arm(ring.copy(triggerAt = nextRegular(alarm, now, zone)))
          Duration.between(ring.triggerAt, now) <= LATE_WINDOW -> Step.Arm(ring)
          else -> Step.Missed(alarm, ring)
        }
    }
    for (alarm in alarms) {
      if (alarm.enabled && alarm.id !in covered) steps += Step.Arm(PendingRing(alarm.id, nextRegular(alarm, now, zone)))
    }
    return steps
  }

  /**
   * What happens to an alarm once its regular ring has fired or been missed: a repeating alarm arms its next day, a
   * one-shot alarm switches off. Returns the next ring, or null for a one-shot.
   */
  fun afterRegularRing(alarm: Alarm, now: Instant, zone: ZoneId): PendingRing? =
    if (alarm.repeatDays.isOneShot) null else PendingRing(alarm.id, nextRegular(alarm, now, zone))

  /** The snooze for a ring that already had [snoozeCount] snoozes, or null once the alarm's cap is reached. */
  fun snooze(alarm: Alarm, snoozeCount: Int, now: Instant): PendingRing? =
    if (snoozeCount >= alarm.ring.maxSnoozes) {
      null
    } else {
      PendingRing(alarm.id, now.plus(Duration.ofMinutes(alarm.ring.snoozeMinutes.toLong())), snoozeCount + 1)
    }

  /** Snoozes still allowed after a ring that already had [snoozeCount]. */
  fun snoozesLeft(alarm: Alarm, snoozeCount: Int): Int = (alarm.ring.maxSnoozes - snoozeCount).coerceAtLeast(0)

  private fun nextRegular(alarm: Alarm, now: Instant, zone: ZoneId): Instant =
    alarm.copy(enabled = true).nextTrigger(now, zone)!!
}
