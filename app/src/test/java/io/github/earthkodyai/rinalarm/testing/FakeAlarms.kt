package io.github.earthkodyai.rinalarm.testing

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmWriter
import io.github.earthkodyai.rinalarm.data.AlarmRepository
import io.github.earthkodyai.rinalarm.setup.DeviceStatus
import io.github.earthkodyai.rinalarm.setup.DeviceStatusSource
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The alarm table for ViewModel tests: read through [AlarmRepository], written through [AlarmWriter]. A test alarm
 * is set at [testTime]; the real rule for choosing that time is AlarmEngine's (tested in AlarmEngineTest).
 */
class FakeAlarms(initial: List<Alarm> = emptyList(), private val testTime: LocalTime = LocalTime.of(6, 2)) :
  AlarmRepository, AlarmWriter {
  private val state = MutableStateFlow(initial.associateBy { it.id })
  private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

  /** Every call to [save], in order. */
  val saves = mutableListOf<Alarm>()

  /** When set, writes wait for it; lets a test tap Save twice while the first write is still running. */
  var gate: CompletableDeferred<Unit>? = null

  /** When set, writes throw it. */
  var failure: Exception? = null

  override val alarms: Flow<List<Alarm>> = state.map { it.values.filterNot(Alarm::isTest).sortedBy(Alarm::time) }

  override val testAlarm: Flow<Alarm?> = state.map { rows -> rows.values.lastOrNull { it.isTest && it.enabled } }

  /** Plays the part of AlarmManager delivering the test ring: the engine deletes the row. */
  fun ringTest() {
    state.value = state.value.filterValues { !it.isTest }
  }

  override suspend fun get(id: Long): Alarm? = state.value[id]

  override suspend fun save(alarm: Alarm): Long {
    gate?.await()
    failure?.let { throw it }
    saves += alarm
    val id = if (alarm.id == 0L) nextId++ else alarm.id
    state.value += id to alarm.copy(id = id)
    return id
  }

  override suspend fun setEnabled(alarmId: Long, enabled: Boolean) {
    val alarm = state.value[alarmId] ?: return
    state.value += alarmId to alarm.copy(enabled = enabled)
  }

  override suspend fun delete(alarmId: Long) {
    gate?.await()
    failure?.let { throw it }
    state.value -= alarmId
  }

  /** Returns [Instant.EPOCH]: the ViewModels read the ring time from [testAlarm], not from this. */
  override suspend fun scheduleTest(label: String): Instant {
    failure?.let { throw it }
    ringTest()
    val id = nextId++
    state.value += id to Alarm(id = id, time = testTime, label = label, ring = RingOptions(maxSnoozes = 0), isTest = true)
    return Instant.EPOCH
  }

  override suspend fun cancelTest() = ringTest()
}

/** A phone whose settings a test can change between reads. */
class FakeDeviceStatus(var status: DeviceStatus = ALL_GOOD) : DeviceStatusSource {
  override fun read(): DeviceStatus = status

  companion object {
    val ALL_GOOD =
      DeviceStatus(
        sdk = 35,
        notificationsAllowed = true,
        fullScreenAllowed = true,
        exactAlarmsAllowed = true,
        batteryUnrestricted = true,
        powerSaveOn = false,
        alarmVolume = 10,
        alarmVolumeMax = 15,
        dndSilencesAlarms = false,
        xiaomiFamily = false,
        device = "Google Pixel 9",
        androidRelease = "15",
        appVersion = "0.1.0",
      )
  }
}

class FixedTimeSource(private val now: Instant, private val zone: ZoneId) : TimeSource {
  override fun now(): Instant = now

  override fun zone(): ZoneId = zone

  override val minuteTicks: Flow<Unit> = flowOf(Unit)
}
