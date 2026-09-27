package io.github.earthkodyai.rinalarm.data

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.data.db.AlarmDao
import io.github.earthkodyai.rinalarm.data.db.toAlarm
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Read-only view of the alarms. Writes go through [io.github.earthkodyai.rinalarm.alarm.engine.AlarmWriter], which
 * keeps AlarmManager in step with the table.
 */
interface AlarmRepository {
  /** The user's alarms, sorted by time of day. The test alarm is not among them. */
  val alarms: Flow<List<Alarm>>

  /** The Diagnostics test alarm while it waits to ring, else null. */
  val testAlarm: Flow<Alarm?>

  suspend fun get(id: Long): Alarm?
}

class RoomAlarmRepository @Inject constructor(private val dao: AlarmDao) : AlarmRepository {
  override val alarms: Flow<List<Alarm>> = dao.observeAll().map { rows -> rows.map { it.toAlarm() } }

  override val testAlarm: Flow<Alarm?> = dao.observeTest().map { it?.toAlarm() }

  override suspend fun get(id: Long): Alarm? = dao.getById(id)?.toAlarm()
}
