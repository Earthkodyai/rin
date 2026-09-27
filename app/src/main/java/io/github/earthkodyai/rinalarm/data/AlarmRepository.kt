package io.github.earthkodyai.rinalarm.data

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.data.db.AlarmDao
import io.github.earthkodyai.rinalarm.data.db.toAlarm
import io.github.earthkodyai.rinalarm.data.db.toEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AlarmRepository {
  /** All alarms, sorted by time of day. */
  val alarms: Flow<List<Alarm>>

  /** Inserts when [Alarm.id] is 0, otherwise updates. Returns the alarm's id. */
  suspend fun save(alarm: Alarm): Long

  suspend fun delete(id: Long)
}

class RoomAlarmRepository @Inject constructor(private val dao: AlarmDao) : AlarmRepository {
  override val alarms: Flow<List<Alarm>> = dao.observeAll().map { rows -> rows.map { it.toAlarm() } }

  override suspend fun save(alarm: Alarm): Long {
    val rowId = dao.upsert(alarm.toEntity())
    return if (alarm.id == 0L) rowId else alarm.id
  }

  override suspend fun delete(id: Long) = dao.delete(id)
}
