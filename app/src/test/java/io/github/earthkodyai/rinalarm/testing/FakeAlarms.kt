package io.github.earthkodyai.rinalarm.testing

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmWriter
import io.github.earthkodyai.rinalarm.data.AlarmRepository
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The alarm table for ViewModel tests: read through [AlarmRepository], written through [AlarmWriter]. */
class FakeAlarms(initial: List<Alarm> = emptyList()) : AlarmRepository, AlarmWriter {
  private val state = MutableStateFlow(initial.associateBy { it.id })
  private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

  /** Every call to [save], in order. */
  val saves = mutableListOf<Alarm>()

  /** When set, writes wait for it; lets a test tap Save twice while the first write is still running. */
  var gate: CompletableDeferred<Unit>? = null

  /** When set, writes throw it. */
  var failure: Exception? = null

  override val alarms: Flow<List<Alarm>> = state.map { it.values.sortedBy(Alarm::time) }

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
}

class FixedTimeSource(private val now: Instant, private val zone: ZoneId) : TimeSource {
  override fun now(): Instant = now

  override fun zone(): ZoneId = zone

  override val minuteTicks: Flow<Unit> = flowOf(Unit)
}
