package io.github.earthkodyai.rinalarm.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One row of the ring log. No foreign key on [alarmId]: the log must outlive a deleted alarm, because it is the
 * evidence for the 14-night reliability run.
 */
@Entity(tableName = "ring_events")
data class RingEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val atMillis: Long,
  /** [io.github.earthkodyai.rinalarm.alarm.log.RingEventType] name. */
  val type: String,
  val alarmId: Long?,
  val scheduledAtMillis: Long?,
  val detail: String,
)

@Dao
interface RingEventDao {
  @Insert suspend fun insert(event: RingEventEntity)

  /** Newest first, for Diagnostics (1.4). */
  @Query("SELECT * FROM ring_events ORDER BY id DESC LIMIT :limit")
  fun observeRecent(limit: Int): Flow<List<RingEventEntity>>

  /** Oldest first, for export (1.5). */
  @Query("SELECT * FROM ring_events ORDER BY id") suspend fun getAll(): List<RingEventEntity>
}
