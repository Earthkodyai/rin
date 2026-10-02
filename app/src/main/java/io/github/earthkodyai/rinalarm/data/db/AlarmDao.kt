package io.github.earthkodyai.rinalarm.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
  /** The user's alarms, without the Diagnostics test alarm. */
  @Query("SELECT * FROM alarms WHERE isTest = 0 ORDER BY hour, minute, id") fun observeAll(): Flow<List<AlarmEntity>>

  /** The test alarm while it is waiting to ring (it is deleted once it has rung). */
  @Query("SELECT * FROM alarms WHERE isTest = 1 AND enabled = 1 ORDER BY id DESC LIMIT 1")
  fun observeTest(): Flow<AlarmEntity?>

  @Query("SELECT * FROM alarms WHERE enabled = 1") suspend fun getEnabled(): List<AlarmEntity>

  /** For the alarm engine: read once, no Flow. Includes switched-off alarms, whose snoozes may still be pending. */
  @Query("SELECT * FROM alarms") suspend fun getAll(): List<AlarmEntity>

  @Query("SELECT * FROM alarms WHERE id = :id") suspend fun getById(id: Long): AlarmEntity?

  /** Returns the new row id on insert, or -1 when an existing row was updated. */
  @Upsert suspend fun upsert(alarm: AlarmEntity): Long

  @Query("DELETE FROM alarms WHERE id = :id") suspend fun delete(id: Long)

  /** The scold switch (G.1): one column, so a ring in progress never re-arms anything. */
  @Query("UPDATE alarms SET scold = :scold WHERE id = :id") suspend fun setScold(id: Long, scold: Boolean)
}
