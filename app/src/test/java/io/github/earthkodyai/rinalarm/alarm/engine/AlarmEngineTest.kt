package io.github.earthkodyai.rinalarm.alarm.engine

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.ARMED
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.DUPLICATE_FIRE
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.EARLY_FIRE
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.FIRED
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.FIRE_UNKNOWN
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.MISSED
import io.github.earthkodyai.rinalarm.alarm.log.RingLog
import io.github.earthkodyai.rinalarm.alarm.ring.RingRequest
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.data.db.AlarmDao
import io.github.earthkodyai.rinalarm.data.db.AlarmEntity
import io.github.earthkodyai.rinalarm.data.db.PendingRingDao
import io.github.earthkodyai.rinalarm.data.db.PendingRingEntity
import io.github.earthkodyai.rinalarm.data.db.toPendingRing
import io.github.earthkodyai.rinalarm.mission.Difficulty
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The engine against in-memory fakes of Room, AlarmManager and the ring service. Bangkok, Mon 28 Sep 2026. */
class AlarmEngineTest {
  private val zone = ZoneId.of("Asia/Bangkok")
  private val clock = MutableTime(at("2026-09-28T06:00"), zone)
  private val alarmDao = FakeAlarmDao()
  private val pendingDao = FakePendingRingDao()
  private val systemAlarms = FakeSystemAlarms()
  private val rings = mutableListOf<RingRequest>()
  private val missed = mutableListOf<Pair<Long, Instant>>()
  private val log = RecordingRingLog()
  private var ringerWorks = true
  private val engine =
    AlarmEngine(
      alarmDao,
      pendingDao,
      systemAlarms,
      ringer = { rings += it; ringerWorks },
      missedNotifier = { alarm, scheduledAt -> missed += alarm.id to scheduledAt },
      deviceState = { "snapshot" },
      log = log,
      time = clock,
    )

  private val daily = Alarm(time = LocalTime.of(7, 0), repeatDays = RepeatDays.EVERY_DAY, label = "Work")
  private val once = Alarm(time = LocalTime.of(7, 0))

  // --- save / delete ---

  @Test
  fun save_armsTheNextTrigger() = runTest {
    val id = engine.save(daily)
    assertEquals(PendingRing(id, at("2026-09-28T07:00")), systemAlarms.armed[id to false])
    assertEquals(listOf(PendingRing(id, at("2026-09-28T07:00"))), pendingDao.rings())
  }

  @Test
  fun save_switchedOff_disarmsAndCancelsAPendingSnooze() = runTest {
    val id = engine.save(daily)
    fireAt(id, "2026-09-28T07:00")
    engine.snooze(id, snoozeCount = 0)

    engine.save(daily.copy(id = id, enabled = false))

    assertTrue(systemAlarms.armed.isEmpty())
    assertTrue(pendingDao.rings().isEmpty())
  }

  @Test
  fun delete_removesTheAlarmAndItsRegistrations() = runTest {
    val id = engine.save(daily)
    engine.delete(id)
    assertTrue(systemAlarms.armed.isEmpty())
    assertNull(alarmDao.getById(id))
  }

  @Test
  fun setEnabled_off_disarmsBothSlots_andOnArmsAgain() = runTest {
    val id = engine.save(daily)
    fireAt(id, "2026-09-28T07:00")
    engine.snooze(id, snoozeCount = 0)

    engine.setEnabled(id, false)
    assertTrue(systemAlarms.armed.isEmpty())
    assertTrue(pendingDao.rings().isEmpty())
    assertFalse(alarmDao.getById(id)!!.enabled)

    engine.setEnabled(id, true)
    assertEquals(mapOf((id to false) to PendingRing(id, at("2026-09-29T07:00"))), systemAlarms.armed.toMap())
  }

  @Test
  fun setEnabled_readsTheStoredAlarm_notAStaleCopy() = runTest {
    val id = engine.save(daily)
    engine.save(daily.copy(id = id, time = LocalTime.of(8, 0)))

    engine.setEnabled(id, false)
    engine.setEnabled(id, true)

    assertEquals(LocalTime.of(8, 0), alarmDao.getById(id)!!.let { LocalTime.of(it.hour, it.minute) })
    assertEquals(PendingRing(id, at("2026-09-28T08:00")), systemAlarms.armed[id to false])
  }

  @Test
  fun setEnabled_toTheSameValue_leavesAPendingSnoozeAlone() = runTest {
    val id = engine.save(daily)
    fireAt(id, "2026-09-28T07:00")
    val snooze = engine.snooze(id, snoozeCount = 0)

    engine.setEnabled(id, true)

    assertEquals(snooze, systemAlarms.armed[id to true])
  }

  @Test
  fun setEnabled_forADeletedAlarm_doesNothing() = runTest {
    engine.setEnabled(42, true)
    assertTrue(systemAlarms.armed.isEmpty())
    assertNull(alarmDao.getById(42))
  }

  // --- firing ---

  @Test
  fun fire_ringsWithTheStoredTime_andArmsTomorrowBeforeRinging() = runTest {
    val id = engine.save(daily)
    fireAt(id, "2026-09-28T07:00:00.850")

    val request = rings.single()
    assertEquals(id, request.alarmId)
    assertEquals(at("2026-09-28T07:00"), request.scheduledAt)
    assertEquals(3, request.snoozesLeft)
    assertFalse(request.late)
    assertEquals(PendingRing(id, at("2026-09-29T07:00")), systemAlarms.armed[id to false])
    assertEquals(listOf(FIRED), log.types(FIRED))
    assertEquals(at("2026-09-28T07:00"), log.events.first { it.type == FIRED }.scheduledAt)
  }

  @Test
  fun fire_carriesTheLevelAndScoldSwitch_andASwitchFlippedMidRingReachesTheSnooze() = runTest {
    val id = engine.save(daily.copy(difficulty = Difficulty.HARD))
    fireAt(id, "2026-09-28T07:00")
    assertEquals(Difficulty.HARD, rings.last().difficulty)
    assertTrue(rings.last().scold)

    val armed = systemAlarms.armed.toMap()
    engine.setScold(id, false)
    assertEquals("the switch re-arms nothing", armed, systemAlarms.armed.toMap())
    val snooze = checkNotNull(engine.snooze(id, 0))
    clock.now = snooze.triggerAt
    deliver(id, snooze = true)
    assertFalse(rings.last().scold)
  }

  @Test
  fun fire_oneShotAlarm_switchesItselfOff() = runTest {
    val id = engine.save(once)
    fireAt(id, "2026-09-28T07:00")

    assertEquals(1, rings.size)
    assertFalse(alarmDao.getById(id)!!.enabled)
    assertTrue(systemAlarms.armed.isEmpty())
  }

  @Test
  fun fire_moreThanAMinuteLate_isMarkedLate() = runTest {
    val id = engine.save(daily)
    fireAt(id, "2026-09-28T07:01:01")
    assertTrue(rings.single().late)
  }

  @Test
  fun fire_forADeletedAlarm_doesNotRing() = runTest {
    engine.onFire(42, snooze = false)
    assertTrue(rings.isEmpty())
    assertEquals(listOf(FIRE_UNKNOWN), log.types(FIRE_UNKNOWN))
  }

  @Test
  fun fire_moreThanAMinuteEarly_isAStaleRegistration_rearmedNotRung() = runTest {
    val id = engine.save(daily)
    fireAt(id, "2026-09-28T06:58:59")
    assertTrue(rings.isEmpty())
    assertEquals(listOf(EARLY_FIRE), log.types(EARLY_FIRE))
    assertEquals(PendingRing(id, at("2026-09-28T07:00")), systemAlarms.armed[id to false])
  }

  @Test
  fun fire_withoutAPendingRow_stillRings_ratherThanMissTheAlarm() = runTest {
    val id = engine.save(daily)
    pendingDao.delete(id, snooze = false)
    fireAt(id, "2026-09-28T07:00")
    assertEquals(1, rings.size)
    assertNull(rings.single().scheduledAt)
  }

  @Test
  fun secondDeliveryOfTheSameSnooze_isDropped() = runTest {
    val id = engine.save(daily)
    fireAt(id, "2026-09-28T07:00")
    engine.snooze(id, snoozeCount = 0)
    clock.now = at("2026-09-28T07:05")
    deliver(id, snooze = true)
    clock.now = at("2026-09-28T07:05:02")
    deliver(id, snooze = true)
    assertEquals(2, rings.size)
    assertEquals(listOf(DUPLICATE_FIRE), log.types(DUPLICATE_FIRE))
  }

  @Test
  fun ringerRefused_isLogged() = runTest {
    ringerWorks = false
    val id = engine.save(daily)
    fireAt(id, "2026-09-28T07:00")
    assertEquals(listOf(RingEventType.FGS_FAIL), log.types(RingEventType.FGS_FAIL))
  }

  // --- snooze ---

  @Test
  fun snooze_armsFiveMinutesFromTheTap_andTheSnoozedRingKnowsItsCount() = runTest {
    val id = engine.save(daily)
    fireAt(id, "2026-09-28T07:00")
    clock.now = at("2026-09-28T07:00:20")

    val snooze = engine.snooze(id, snoozeCount = 0)

    assertEquals(PendingRing(id, at("2026-09-28T07:05:20"), 1), snooze)
    assertEquals(snooze, systemAlarms.armed[id to true])
    // Tomorrow's regular ring is untouched.
    assertEquals(PendingRing(id, at("2026-09-29T07:00")), systemAlarms.armed[id to false])

    clock.now = at("2026-09-28T07:05:20")
    deliver(id, snooze = true)
    assertEquals(2, rings.last().snoozesLeft)
    assertEquals(PendingRing(id, at("2026-09-29T07:00")), systemAlarms.armed[id to false])
  }

  @Test
  fun snooze_ofAOneShotAlarm_stillRings() = runTest {
    val id = engine.save(once)
    fireAt(id, "2026-09-28T07:00")
    engine.snooze(id, snoozeCount = 0)
    clock.now = at("2026-09-28T07:05")
    deliver(id, snooze = true)
    assertEquals(2, rings.size)
  }

  @Test
  fun snooze_pastTheCap_isRefused() = runTest {
    val id = engine.save(daily)
    assertNull(engine.snooze(id, snoozeCount = 3))
    assertNull(systemAlarms.armed[id to true])
  }

  // --- reconcile ---

  @Test
  fun reconcile_afterReboot_rearmsFromStorage() = runTest {
    val id = engine.save(daily)
    systemAlarms.armed.clear() // a reboot wipes AlarmManager
    engine.reconcile("LOCKED_BOOT_COMPLETED")
    assertEquals(PendingRing(id, at("2026-09-28T07:00")), systemAlarms.armed[id to false])
  }

  @Test
  fun reconcile_ringDueTenMinutesAgo_isArmedInThePast_soItRingsLateThroughTheNormalPath() = runTest {
    val id = engine.save(daily)
    systemAlarms.armed.clear()
    clock.now = at("2026-09-28T07:10")
    engine.reconcile("BOOT_COMPLETED")
    assertEquals(PendingRing(id, at("2026-09-28T07:00")), systemAlarms.armed[id to false])

    deliver(id, snooze = false)
    assertTrue(rings.single().late)
  }

  @Test
  fun reconcile_ringDueAnHourAgo_isMissed_notifiesAndMovesOn() = runTest {
    val id = engine.save(daily)
    systemAlarms.armed.clear()
    clock.now = at("2026-09-28T08:00")
    engine.reconcile("BOOT_COMPLETED")

    assertTrue(rings.isEmpty())
    assertEquals(listOf(id to at("2026-09-28T07:00")), missed)
    assertEquals(listOf(MISSED), log.types(MISSED))
    assertEquals(PendingRing(id, at("2026-09-29T07:00")), systemAlarms.armed[id to false])
  }

  @Test
  fun reconcile_missedOneShot_switchesOff() = runTest {
    val id = engine.save(once)
    clock.now = at("2026-09-28T09:00")
    engine.reconcile("BOOT_COMPLETED")
    assertFalse(alarmDao.getById(id)!!.enabled)
    assertTrue(systemAlarms.armed.isEmpty())
  }

  @Test
  fun reconcile_logsArmedOnlyWhenATimeChanged() = runTest {
    engine.save(daily)
    val armedBefore = log.types(ARMED).size
    engine.reconcile("app_open")
    engine.reconcile("app_open")
    assertEquals(armedBefore, log.types(ARMED).size)
  }

  // --- test alarm ---

  @Test
  fun scheduleTest_armsAHiddenOneShot_atTheFirstWholeMinuteAMinuteAway() = runTest {
    clock.now = at("2026-09-28T06:00:30")

    val ringsAt = engine.scheduleTest("Test alarm")

    assertEquals(at("2026-09-28T06:02"), ringsAt)
    val row = alarmDao.getAll().single()
    assertTrue(row.isTest)
    assertEquals(0, row.maxSnoozes)
    assertEquals(PendingRing(row.id, ringsAt), systemAlarms.armed[row.id to false])
  }

  @Test
  fun scheduleTest_again_replacesTheEarlierTest() = runTest {
    val first = engine.scheduleTest("Test alarm")
    clock.now = at("2026-09-28T06:00:40")
    engine.scheduleTest("Test alarm")

    val row = alarmDao.getAll().single()
    assertEquals(1, systemAlarms.armed.size)
    assertEquals(first.plusSeconds(60), systemAlarms.armed[row.id to false]?.triggerAt)
  }

  @Test
  fun testAlarm_ringsThroughTheRealPath_isTaggedInTheLog_andDeletedAfterwards() = runTest {
    val userAlarm = engine.save(daily)
    val ringsAt = engine.scheduleTest("Test alarm")
    val testId = alarmDao.getAll().single { it.isTest }.id

    clock.now = ringsAt
    deliver(testId, snooze = false)

    assertEquals(listOf(testId), rings.map { it.alarmId })
    assertEquals(0, rings.single().snoozesLeft)
    // Marked, so the ring leaves a waiting rest or sick day for the real morning.
    assertTrue(rings.single().isTest)
    assertTrue(log.events.single { it.type == FIRED }.detail.endsWith(AlarmEngine.TEST_MARK))
    assertEquals(listOf(userAlarm), alarmDao.getAll().map { it.id })
    assertTrue(pendingDao.rings().none { it.alarmId == testId })
  }

  @Test
  fun testAlarm_foundMissed_isDeletedWithoutAMissedNotification() = runTest {
    val ringsAt = engine.scheduleTest("Test alarm")

    clock.now = ringsAt.plusSeconds(3600)
    engine.reconcile("app_open")

    assertTrue(alarmDao.getAll().isEmpty())
    assertTrue(missed.isEmpty())
    assertTrue(log.events.single { it.type == MISSED }.detail.endsWith(AlarmEngine.TEST_MARK))
  }

  @Test
  fun cancelTest_removesOnlyTheTest() = runTest {
    val userAlarm = engine.save(daily)
    engine.scheduleTest("Test alarm")

    engine.cancelTest()

    assertEquals(listOf(userAlarm), alarmDao.getAll().map { it.id })
    assertEquals(setOf(userAlarm to false), systemAlarms.armed.keys)
  }

  @Test
  fun inexactFallback_isLogged() = runTest {
    systemAlarms.exact = false
    engine.save(daily)
    assertEquals(listOf(RingEventType.ARMED_INEXACT), log.types(RingEventType.ARMED_INEXACT))
  }

  // --- helpers ---

  private suspend fun fireAt(id: Long, local: String) {
    clock.now = at(local)
    deliver(id, snooze = false)
  }

  /** What AlarmManager does: a delivered alarm is no longer registered. */
  private suspend fun deliver(id: Long, snooze: Boolean) {
    systemAlarms.armed.remove(id to snooze)
    engine.onFire(id, snooze)
  }

  private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
}

private class MutableTime(var now: Instant, private val zone: ZoneId) : TimeSource {
  override fun now(): Instant = now

  override fun zone(): ZoneId = zone

  override val minuteTicks: Flow<Unit> = emptyFlow()
}

private class FakeAlarmDao : AlarmDao {
  private val rows = MutableStateFlow<Map<Long, AlarmEntity>>(emptyMap())
  private var nextId = 1L

  override fun observeAll(): Flow<List<AlarmEntity>> =
    rows.map { it.values.filterNot { e -> e.isTest }.sortedBy { e -> e.hour * 60 + e.minute } }

  override fun observeTest(): Flow<AlarmEntity?> = rows.map { it.values.lastOrNull { e -> e.isTest && e.enabled } }

  override suspend fun getEnabled(): List<AlarmEntity> = rows.value.values.filter { it.enabled }

  override suspend fun getAll(): List<AlarmEntity> = rows.value.values.toList()

  override suspend fun getById(id: Long): AlarmEntity? = rows.value[id]

  override suspend fun upsert(alarm: AlarmEntity): Long {
    if (alarm.id != 0L && alarm.id in rows.value) {
      rows.value += alarm.id to alarm
      return -1
    }
    val id = if (alarm.id == 0L) nextId++ else alarm.id
    rows.value += id to alarm.copy(id = id)
    return id
  }

  override suspend fun delete(id: Long) {
    rows.value -= id
  }

  override suspend fun setScold(id: Long, scold: Boolean) {
    val row = rows.value[id] ?: return
    rows.value += id to row.copy(scold = scold)
  }
}

private class FakePendingRingDao : PendingRingDao {
  private val rows = mutableMapOf<Pair<Long, Boolean>, PendingRingEntity>()

  fun rings(): List<PendingRing> = rows.values.map { it.toPendingRing() }

  override suspend fun getAll(): List<PendingRingEntity> = rows.values.toList()

  override suspend fun get(alarmId: Long, snooze: Boolean): PendingRingEntity? = rows[alarmId to snooze]

  override suspend fun upsert(ring: PendingRingEntity) {
    rows[ring.alarmId to ring.snooze] = ring
  }

  override suspend fun delete(alarmId: Long, snooze: Boolean) {
    rows.remove(alarmId to snooze)
  }
}

private class FakeSystemAlarms : SystemAlarms {
  val armed = mutableMapOf<Pair<Long, Boolean>, PendingRing>()
  var exact = true

  override fun arm(ring: PendingRing): Boolean {
    armed[ring.alarmId to ring.isSnooze] = ring
    return exact
  }

  override fun disarm(alarmId: Long, snooze: Boolean) {
    armed.remove(alarmId to snooze)
  }
}

private class RecordingRingLog : RingLog {
  data class Event(val type: RingEventType, val alarmId: Long?, val scheduledAt: Instant?, val detail: String)

  val events = mutableListOf<Event>()

  fun types(type: RingEventType): List<RingEventType> = events.map { it.type }.filter { it == type }

  override suspend fun record(type: RingEventType, alarmId: Long?, scheduledAt: Instant?, detail: String) {
    events += Event(type, alarmId, scheduledAt, detail)
  }
}
