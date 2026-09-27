package io.github.earthkodyai.rinalarm.alarm.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.engine.PendingRing
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.data.db.RinDatabase
import io.github.earthkodyai.rinalarm.data.db.RingEventEntity
import io.github.earthkodyai.rinalarm.data.db.toEntity
import io.github.earthkodyai.rinalarm.testing.FencedSystemAlarms
import io.github.earthkodyai.rinalarm.testing.RecordingRinger
import io.github.earthkodyai.rinalarm.testing.TEST_ALARM_ID_FLOOR
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The receivers on a real device: real broadcasts and the real AlarmManager into the real engine, with the ring
 * service faked and storage in memory (testing/TestModules.kt). Uses runBlocking, not runTest: the work happens on
 * other threads, and runTest's virtual clock would expire the timeouts at once.
 */
@HiltAndroidTest
class AlarmReceiversTest {
  @get:Rule val hilt = HiltAndroidRule(this)

  @Inject lateinit var database: RinDatabase
  @Inject lateinit var systemAlarms: FencedSystemAlarms
  @Inject lateinit var ringer: RecordingRinger

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val zone = ZoneId.systemDefault()

  @Before fun setUp() = hilt.inject()

  @After
  fun tearDown() {
    systemAlarms.disarmAll()
    database.close()
  }

  private suspend fun insertDaily(id: Long): Alarm {
    val alarm = Alarm(id = id, time = LocalTime.of(6, 30), repeatDays = RepeatDays.EVERY_DAY, label = "receiver test")
    database.alarmDao().upsert(alarm.toEntity())
    return alarm
  }

  private fun fireIntent(alarmId: Long?, action: String = AlarmFireReceiver.ACTION_FIRE): Intent =
    Intent(context, AlarmFireReceiver::class.java).setAction(action).apply {
      if (alarmId != null) putExtra(AlarmFireReceiver.EXTRA_ALARM_ID, alarmId)
    }

  private suspend fun awaitLog(timeoutMs: Long = 10_000, done: (List<RingEventEntity>) -> Boolean): List<RingEventEntity> =
    withTimeout(timeoutMs) { database.ringEventDao().observeRecent(500).first(done) }

  private fun List<RingEventEntity>.ofType(type: RingEventType) = filter { it.type == type.name }

  /**
   * A trigger just past AlarmManager's minimum lead time: alarms due sooner than 5 s are pushed to 5 s (MIN_FUTURITY),
   * which made a +3 s ring look 2 s late on the first run.
   */
  private fun soon(): Instant = Instant.now().plusSeconds(6)

  @Test
  fun fireBroadcast_ringsAndArmsTheNextDay() = runBlocking {
    val alarm = insertDaily(TEST_ALARM_ID_FLOOR + 1)
    val scheduledAt = Instant.now().minusMillis(200)
    database.pendingRingDao().upsert(PendingRing(alarm.id, scheduledAt).toEntity())

    context.sendBroadcast(fireIntent(alarm.id))

    val request = withTimeout(10_000) { ringer.requests.first { it.isNotEmpty() } }.single()
    assertEquals(alarm.id, request.alarmId)
    assertEquals(scheduledAt.toEpochMilli(), request.scheduledAt?.toEpochMilli())
    val fired = awaitLog { it.ofType(RingEventType.FIRED).isNotEmpty() }.ofType(RingEventType.FIRED).single()
    assertEquals(alarm.id, fired.alarmId)
    // The engine armed tomorrow's ring before handing over to the ring service.
    val next = database.pendingRingDao().get(alarm.id, snooze = false)
    assertNotNull(next)
    assertEquals(alarm.nextTrigger(Instant.now(), zone)?.toEpochMilli(), next!!.triggerAtMillis)
  }

  @Test
  fun fireBroadcast_forADeletedAlarm_doesNotRing() = runBlocking {
    context.sendBroadcast(fireIntent(TEST_ALARM_ID_FLOOR + 2))

    val unknown = awaitLog { it.ofType(RingEventType.FIRE_UNKNOWN).isNotEmpty() }.ofType(RingEventType.FIRE_UNKNOWN)
    assertEquals("deleted", unknown.single().detail)
    assertTrue(ringer.requests.value.isEmpty())
  }

  @Test
  fun fireBroadcast_withAnotherActionOrNoId_isIgnored() = runBlocking {
    context.sendBroadcast(fireIntent(TEST_ALARM_ID_FLOOR + 3, action = "io.github.earthkodyai.rinalarm.action.OTHER"))
    context.sendBroadcast(fireIntent(alarmId = null))
    // Broadcasts to one receiver arrive in order, so once this marker is logged the two above were handled.
    context.sendBroadcast(fireIntent(TEST_ALARM_ID_FLOOR + 4))

    val rows = awaitLog { it.ofType(RingEventType.FIRE_UNKNOWN).isNotEmpty() }
    assertEquals(listOf(TEST_ALARM_ID_FLOOR + 4), rows.mapNotNull { it.alarmId })
  }

  @Test
  fun alarmManager_deliversAnArmedRingToTheReceiver() = runBlocking {
    val alarm = insertDaily(TEST_ALARM_ID_FLOOR + 5)
    val ring = PendingRing(alarm.id, soon())
    database.pendingRingDao().upsert(ring.toEntity())

    assertTrue("exact alarms not allowed", systemAlarms.arm(ring))

    val request = withTimeout(30_000) { ringer.requests.first { it.isNotEmpty() } }.single()
    assertEquals(alarm.id, request.alarmId)
    val fired = awaitLog { it.ofType(RingEventType.FIRED).isNotEmpty() }.ofType(RingEventType.FIRED).single()
    // setAlarmClock is exact: S1 and real rings fired under a second late. The margin covers a busy CI emulator.
    val late = Duration.ofMillis(fired.atMillis - ring.triggerAt.toEpochMilli())
    assertTrue("fired $late late", !late.isNegative && late < Duration.ofSeconds(3))
  }

  @Test
  fun disarm_cancelsTheRegistration() = runBlocking {
    val alarm = insertDaily(TEST_ALARM_ID_FLOOR + 6)
    val ring = PendingRing(alarm.id, soon())
    database.pendingRingDao().upsert(ring.toEntity())
    systemAlarms.arm(ring)

    systemAlarms.disarm(alarm.id, snooze = false)
    delay(10_000)

    assertTrue(ringer.requests.value.isEmpty())
    assertTrue(database.ringEventDao().getAll().ofType(RingEventType.FIRED).isEmpty())
  }

  @Test
  fun rescheduleReceiver_reconcilesOnEverySystemAction() = runBlocking {
    // No pending row yet: as after a reboot wiped AlarmManager and the app never ran.
    val alarm = insertDaily(TEST_ALARM_ID_FLOOR + 7)
    val receiver = RescheduleReceiver()

    // The system actions are protected broadcasts an app cannot send, so onReceive is called directly.
    for (action in RescheduleReceiver.ACTIONS) receiver.onReceive(context, Intent(action))
    receiver.onReceive(context, Intent("io.github.earthkodyai.rinalarm.action.OTHER"))

    val rows = awaitLog { it.ofType(RingEventType.RECONCILE).size >= RescheduleReceiver.ACTIONS.size }
    delay(500) // would let a reconcile for the unknown action land too
    val reasons = database.ringEventDao().getAll().ofType(RingEventType.RECONCILE).map { it.detail.substringBefore(' ') }
    assertEquals(RescheduleReceiver.ACTIONS.map { it.substringAfterLast('.') }.sorted(), reasons.sorted())
    // The first reconcile armed the alarm; the rest found it in place.
    assertEquals(1, rows.ofType(RingEventType.ARMED).count { it.alarmId == alarm.id })
    assertEquals(
      alarm.nextTrigger(Instant.now(), zone)?.toEpochMilli(),
      database.pendingRingDao().get(alarm.id, snooze = false)?.triggerAtMillis,
    )
  }
}
