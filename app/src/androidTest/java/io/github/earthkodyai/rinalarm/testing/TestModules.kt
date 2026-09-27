package io.github.earthkodyai.rinalarm.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmEngine
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmWriter
import io.github.earthkodyai.rinalarm.alarm.engine.AndroidSystemAlarms
import io.github.earthkodyai.rinalarm.alarm.engine.DeviceStateProbe
import io.github.earthkodyai.rinalarm.alarm.engine.MissedAlarmNotifier
import io.github.earthkodyai.rinalarm.alarm.engine.PendingRing
import io.github.earthkodyai.rinalarm.alarm.engine.Ringer
import io.github.earthkodyai.rinalarm.alarm.engine.SystemAlarms
import io.github.earthkodyai.rinalarm.alarm.log.AndroidDeviceState
import io.github.earthkodyai.rinalarm.alarm.log.RingLog
import io.github.earthkodyai.rinalarm.alarm.log.RoomRingLog
import io.github.earthkodyai.rinalarm.alarm.ring.RingRequest
import io.github.earthkodyai.rinalarm.data.db.AlarmDao
import io.github.earthkodyai.rinalarm.data.db.RinDatabase
import io.github.earthkodyai.rinalarm.di.AlarmBindsModule
import io.github.earthkodyai.rinalarm.di.StorageModule
import io.github.earthkodyai.rinalarm.setup.AndroidDeviceStatus
import io.github.earthkodyai.rinalarm.setup.DeviceStatusSource
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

// The receiver tests run on the phone that also carries the user's real alarms (and the 14-night ring log), so the
// test graph never touches the real database or rings: storage is in memory, the ring service is faked, and
// AlarmManager is real but fenced off to alarm ids from TEST_ALARM_ID_FLOOR up.

/** Alarm ids used by tests. Real ids count up from 1, so AlarmManager slots (request code, data URI) never collide. */
const val TEST_ALARM_ID_FLOOR = 900_000L

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [StorageModule::class])
object TestStorageModule {
  @Provides
  @Singleton
  fun database(@ApplicationContext context: Context): RinDatabase =
    Room.inMemoryDatabaseBuilder(context, RinDatabase::class.java).build()

  @Provides fun alarmDao(database: RinDatabase): AlarmDao = database.alarmDao()

  /** A fresh file per test component: two live DataStores on one file throw. */
  @Provides
  @Singleton
  fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
      produceFile = { File(context.cacheDir, "test-datastore/${UUID.randomUUID()}.preferences_pb") }
    )
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AlarmBindsModule::class])
abstract class TestAlarmBindsModule {
  @Binds abstract fun alarmWriter(impl: AlarmEngine): AlarmWriter

  @Binds abstract fun systemAlarms(impl: FencedSystemAlarms): SystemAlarms

  @Binds abstract fun ringer(impl: RecordingRinger): Ringer

  @Binds abstract fun missedAlarmNotifier(impl: RecordingMissedNotifier): MissedAlarmNotifier

  @Binds abstract fun deviceState(impl: AndroidDeviceState): DeviceStateProbe

  @Binds abstract fun ringLog(impl: RoomRingLog): RingLog

  @Binds abstract fun deviceStatus(impl: AndroidDeviceStatus): DeviceStatusSource
}

/**
 * The real AlarmManager, limited to test ids and remembering every slot it armed so [disarmAll] can clean up. A
 * disarm below the floor is dropped: it could only come from one of the user's own alarms firing mid-run.
 */
@Singleton
class FencedSystemAlarms @Inject constructor(private val real: AndroidSystemAlarms) : SystemAlarms {
  private val armed = mutableSetOf<Pair<Long, Boolean>>()

  override fun arm(ring: PendingRing): Boolean {
    require(ring.alarmId >= TEST_ALARM_ID_FLOOR) { "test armed a real alarm id: ${ring.alarmId}" }
    synchronized(armed) { armed += ring.alarmId to ring.isSnooze }
    return real.arm(ring)
  }

  override fun disarm(alarmId: Long, snooze: Boolean) {
    if (alarmId >= TEST_ALARM_ID_FLOOR) real.disarm(alarmId, snooze)
  }

  fun disarmAll() {
    val slots = synchronized(armed) { armed.toList().also { armed.clear() } }
    for ((alarmId, snooze) in slots) real.disarm(alarmId, snooze)
  }
}

/** Stands in for the ring service: nothing sounds, the requests are kept. */
@Singleton
class RecordingRinger @Inject constructor() : Ringer {
  val requests = MutableStateFlow<List<RingRequest>>(emptyList())

  override fun start(request: RingRequest): Boolean {
    requests.update { it + request }
    return true
  }
}

@Singleton
class RecordingMissedNotifier @Inject constructor() : MissedAlarmNotifier {
  val missed = MutableStateFlow<List<Pair<Alarm, Instant>>>(emptyList())

  override fun notifyMissed(alarm: Alarm, scheduledAt: Instant) = missed.update { it + (alarm to scheduledAt) }
}
