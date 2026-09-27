package io.github.earthkodyai.rinalarm.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Query
import androidx.room.Upsert
import io.github.earthkodyai.rinalarm.alarm.engine.PendingRing
import java.time.Instant

/**
 * A ring currently registered with AlarmManager. This table, not AlarmManager, is the source of truth: AlarmManager
 * forgets everything on reboot, and the stored trigger is what lets a restore tell "ring late" from "missed".
 *
 * At most two rows per alarm: the regular next ring and a snooze.
 */
@Entity(
  tableName = "pending_rings",
  primaryKeys = ["alarmId", "snooze"],
  foreignKeys =
    [ForeignKey(entity = AlarmEntity::class, parentColumns = ["id"], childColumns = ["alarmId"], onDelete = ForeignKey.CASCADE)],
)
data class PendingRingEntity(val alarmId: Long, val snooze: Boolean, val triggerAtMillis: Long, val snoozeCount: Int)

fun PendingRingEntity.toPendingRing(): PendingRing =
  PendingRing(alarmId, Instant.ofEpochMilli(triggerAtMillis), snoozeCount)

fun PendingRing.toEntity(): PendingRingEntity =
  PendingRingEntity(alarmId, isSnooze, triggerAt.toEpochMilli(), snoozeCount)

@Dao
interface PendingRingDao {
  @Query("SELECT * FROM pending_rings") suspend fun getAll(): List<PendingRingEntity>

  @Query("SELECT * FROM pending_rings WHERE alarmId = :alarmId AND snooze = :snooze")
  suspend fun get(alarmId: Long, snooze: Boolean): PendingRingEntity?

  @Upsert suspend fun upsert(ring: PendingRingEntity)

  @Query("DELETE FROM pending_rings WHERE alarmId = :alarmId AND snooze = :snooze")
  suspend fun delete(alarmId: Long, snooze: Boolean)
}
