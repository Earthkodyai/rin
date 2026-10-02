package io.github.earthkodyai.rinalarm.alarm.engine

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType
import io.github.earthkodyai.rinalarm.alarm.log.RingLog
import io.github.earthkodyai.rinalarm.alarm.ring.RingPolicy
import io.github.earthkodyai.rinalarm.alarm.ring.RingRequest
import io.github.earthkodyai.rinalarm.data.db.AlarmDao
import io.github.earthkodyai.rinalarm.data.db.PendingRingDao
import io.github.earthkodyai.rinalarm.data.db.toAlarm
import io.github.earthkodyai.rinalarm.data.db.toEntity
import io.github.earthkodyai.rinalarm.data.db.toPendingRing
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The only writer of pending rings and of AlarmManager registrations. Receivers, the ring service and the UI
 * (through [AlarmWriter]) call in here; the rules themselves live in [RingPlanner]. One mutex serialises everything,
 * so a reconcile and a fire arriving together cannot interleave.
 *
 * Nothing here touches the network, WebView or AI (hard rule: the ring path is native-only).
 */
@Singleton
class AlarmEngine
@Inject
constructor(
  private val alarmDao: AlarmDao,
  private val pendingDao: PendingRingDao,
  private val systemAlarms: SystemAlarms,
  private val ringer: Ringer,
  private val missedNotifier: MissedAlarmNotifier,
  private val deviceState: DeviceStateProbe,
  private val log: RingLog,
  private val time: TimeSource,
) : AlarmWriter {
  private val mutex = Mutex()

  /** When each slot last rang in this process, to drop a second delivery of the same ring. */
  private val lastFired = mutableMapOf<Pair<Long, Boolean>, Instant>()

  /** See [RingPlanner.reconcile]. [reason] goes into the log (broadcast name or "app_open"). */
  suspend fun reconcile(reason: String) =
    mutex.withLock {
      val now = time.now()
      val zone = time.zone()
      val alarms = alarmDao.getAll().map { it.toAlarm() }
      val pending = pendingDao.getAll().map { it.toPendingRing() }
      log.record(RingEventType.RECONCILE, detail = "$reason alarms=${alarms.size} pending=${pending.size}")
      for (step in RingPlanner.reconcile(alarms, pending, now, zone)) {
        when (step) {
          is RingPlanner.Step.Arm ->
            arm(step.ring, previous = pending.find { it.alarmId == step.ring.alarmId && it.isSnooze == step.ring.isSnooze })
          is RingPlanner.Step.Disarm -> {
            disarm(step.alarmId, step.snooze)
            log.record(RingEventType.DISARMED, step.alarmId, detail = "snooze=${step.snooze}")
          }
          is RingPlanner.Step.Missed -> missed(step.alarm, step.ring, now, zone)
        }
      }
    }

  /** AlarmManager delivered the ring for ([alarmId], [snooze]). */
  suspend fun onFire(alarmId: Long, snooze: Boolean) =
    mutex.withLock {
      val now = time.now()
      val zone = time.zone()
      val slot = alarmId to snooze
      val alarm = alarmDao.getById(alarmId)?.toAlarm()
      if (alarm == null || (!snooze && !alarm.enabled)) {
        systemAlarms.disarm(alarmId, snooze)
        pendingDao.delete(alarmId, snooze)
        log.record(RingEventType.FIRE_UNKNOWN, alarmId, detail = if (alarm == null) "deleted" else "off")
        return@withLock
      }
      val ring = pendingDao.get(alarmId, snooze)?.toPendingRing()
      if (ring == null) {
        val last = lastFired[slot]
        if (last != null && Duration.between(last, now) < RingPolicy.AUTO_STOP) {
          log.record(RingEventType.DUPLICATE_FIRE, alarmId, detail = "snooze=$snooze")
          return@withLock
        }
        // The alarm exists but its pending row is gone. Ringing at a slightly odd time beats not ringing.
      } else if (ring.triggerAt.isAfter(now.plus(EARLY_TOLERANCE))) {
        log.record(RingEventType.EARLY_FIRE, alarmId, ring.triggerAt)
        arm(ring, previous = ring)
        return@withLock
      }

      lastFired[slot] = now
      if (ring != null) pendingDao.delete(alarmId, snooze)
      val scheduledAt = ring?.triggerAt
      val snoozeCount = ring?.snoozeCount ?: if (snooze) alarm.ring.maxSnoozes else 0
      log.record(
        RingEventType.FIRED,
        alarmId,
        scheduledAt,
        deviceState.snapshot() +
          (if (ring == null) " no_pending" else "") +
          " snoozeCount=$snoozeCount" +
          (if (alarm.isTest) " $TEST_MARK" else ""),
      )
      // Arm the next day before ringing, so a crash while ringing cannot cost tomorrow's alarm.
      if (!snooze) moveOn(alarm, now, zone)
      val request =
        RingRequest(
          alarmId = alarm.id,
          time = alarm.time,
          label = alarm.label,
          scheduledAt = scheduledAt,
          snoozeCount = snoozeCount,
          snoozesLeft = RingPlanner.snoozesLeft(alarm, snoozeCount),
          late = scheduledAt != null && Duration.between(scheduledAt, now) > LATE_THRESHOLD,
          options = alarm.ring,
          mission = alarm.mission,
          isTest = alarm.isTest,
          difficulty = alarm.difficulty,
          scold = alarm.scold,
        )
      if (!ringer.start(request)) log.record(RingEventType.FGS_FAIL, alarmId, scheduledAt)
    }

  /**
   * Arms a snooze for a ring that already had [snoozeCount] snoozes. Returns it, or null when the cap is reached or
   * the alarm was deleted meanwhile.
   */
  suspend fun snooze(alarmId: Long, snoozeCount: Int): PendingRing? =
    mutex.withLock {
      val alarm = alarmDao.getById(alarmId)?.toAlarm() ?: return@withLock null
      val next = RingPlanner.snooze(alarm, snoozeCount, time.now()) ?: return@withLock null
      arm(next, previous = null)
      next
    }

  override suspend fun save(alarm: Alarm): Long = mutex.withLock { saveLocked(alarm) }

  override suspend fun setEnabled(alarmId: Long, enabled: Boolean) =
    mutex.withLock {
      // Read inside the lock: the caller's copy may be stale (a one-shot can switch itself off meanwhile).
      val alarm = alarmDao.getById(alarmId)?.toAlarm() ?: return@withLock
      if (alarm.enabled != enabled) saveLocked(alarm.copy(enabled = enabled))
    }

  override suspend fun delete(alarmId: Long) = mutex.withLock { deleteLocked(alarmId) }

  override suspend fun setScold(alarmId: Long, scold: Boolean) = mutex.withLock { alarmDao.setScold(alarmId, scold) }

  override suspend fun scheduleTest(label: String): Instant =
    mutex.withLock {
      deleteTestsLocked()
      val now = time.now()
      val zone = time.zone()
      // No snoozes: the test ends on its first Dismiss, and a snooze would outlive the deleted row. No mission
      // either: it tests the ring path, and a game would get in the way.
      val test =
        Alarm(
          time = RingPlanner.testRingTime(now, zone),
          label = label,
          ring = RingOptions(maxSnoozes = 0),
          isTest = true,
          mission = MissionChoice.None,
        )
      val id = saveLocked(test)
      checkNotNull(test.copy(id = id).nextTrigger(now, zone))
    }

  override suspend fun cancelTest() = mutex.withLock { deleteTestsLocked() }

  private suspend fun deleteLocked(alarmId: Long) {
    disarm(alarmId, snooze = true)
    disarm(alarmId, snooze = false)
    alarmDao.delete(alarmId)
  }

  private suspend fun deleteTestsLocked() {
    for (row in alarmDao.getAll()) if (row.isTest) deleteLocked(row.id)
  }

  /** Saves [alarm] and re-arms it from scratch; an edit also cancels a pending snooze. Returns the alarm's id. */
  private suspend fun saveLocked(alarm: Alarm): Long {
    val rowId = alarmDao.upsert(alarm.toEntity())
    val id = if (alarm.id == 0L) rowId else alarm.id
    disarm(id, snooze = true)
    disarm(id, snooze = false)
    alarm.copy(id = id).nextTrigger(time.now(), time.zone())?.let { arm(PendingRing(id, it), previous = null) }
    return id
  }

  private suspend fun arm(ring: PendingRing, previous: PendingRing?) {
    pendingDao.upsert(ring.toEntity())
    val exact =
      runCatching { systemAlarms.arm(ring) }
        .getOrElse {
          log.record(RingEventType.ARM_FAILED, ring.alarmId, ring.triggerAt, it.toString())
          return
        }
    when {
      !exact -> log.record(RingEventType.ARMED_INEXACT, ring.alarmId, ring.triggerAt, "snoozeCount=${ring.snoozeCount}")
      previous?.triggerAt != ring.triggerAt ->
        log.record(RingEventType.ARMED, ring.alarmId, ring.triggerAt, "snoozeCount=${ring.snoozeCount}")
    }
  }

  private suspend fun disarm(alarmId: Long, snooze: Boolean) {
    systemAlarms.disarm(alarmId, snooze)
    pendingDao.delete(alarmId, snooze)
  }

  private suspend fun missed(alarm: Alarm, ring: PendingRing, now: Instant, zone: ZoneId) {
    disarm(alarm.id, ring.isSnooze)
    log.record(
      RingEventType.MISSED,
      alarm.id,
      ring.triggerAt,
      "snoozeCount=${ring.snoozeCount}" + if (alarm.isTest) " $TEST_MARK" else "",
    )
    if (!alarm.isTest) missedNotifier.notifyMissed(alarm, ring.triggerAt)
    if (!ring.isSnooze) moveOn(alarm, now, zone)
  }

  /** After a regular ring fired or was missed: arm the next day, switch a one-shot alarm off, or drop a test. */
  private suspend fun moveOn(alarm: Alarm, now: Instant, zone: ZoneId) {
    if (alarm.isTest) return alarmDao.delete(alarm.id)
    val next = RingPlanner.afterRegularRing(alarm, now, zone)
    if (next != null) arm(next, previous = null) else alarmDao.upsert(alarm.copy(enabled = false).toEntity())
  }

  companion object {
    /** Tags FIRED and MISSED rows of the test alarm, which the reliability numbers leave out. */
    const val TEST_MARK = "test=true"

    /** setAlarmClock is exact; anything earlier than this is a stale registration, not a real ring. */
    private val EARLY_TOLERANCE: Duration = Duration.ofMinutes(1)

    /** Rings later than this are marked late on screen. S1 measured at most 850 ms for on-time rings. */
    private val LATE_THRESHOLD: Duration = Duration.ofMinutes(1)
  }
}
