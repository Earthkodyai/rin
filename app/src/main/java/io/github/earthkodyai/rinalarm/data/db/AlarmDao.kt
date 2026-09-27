package io.github.earthkodyai.rinalarm.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
  @Query("SELECT * FROM alarms ORDER BY hour, minute, id") fun observeAll(): Flow<List<AlarmEntity>>

  /** For the rescheduling receivers (1.2): read once, no Flow. */
  @Query("SELECT * FROM alarms WHERE enabled = 1") suspend fun getEnabled(): List<AlarmEntity>

  @Query("SELECT * FROM alarms WHERE id = :id") suspend fun getById(id: Long): AlarmEntity?

  /** Returns the new row id on insert, or -1 when an existing row was updated. */
  @Upsert suspend fun upsert(alarm: AlarmEntity): Long

  @Query("DELETE FROM alarms WHERE id = :id") suspend fun delete(id: Long)
}
