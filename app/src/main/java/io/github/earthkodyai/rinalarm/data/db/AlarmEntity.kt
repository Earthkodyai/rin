package io.github.earthkodyai.rinalarm.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import java.time.LocalTime

@Entity(tableName = "alarms")
data class AlarmEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val hour: Int,
  val minute: Int,
  /** [RepeatDays.mask]: Monday = bit 0, 0 = one-shot. */
  val repeatDays: Int,
  val label: String,
  val enabled: Boolean,
)

fun AlarmEntity.toAlarm(): Alarm =
  Alarm(
    id = id,
    time = LocalTime.of(hour, minute),
    repeatDays = RepeatDays.fromMask(repeatDays),
    label = label,
    enabled = enabled,
  )

fun Alarm.toEntity(): AlarmEntity =
  AlarmEntity(
    id = id,
    hour = time.hour,
    minute = time.minute,
    repeatDays = repeatDays.mask,
    label = label,
    enabled = enabled,
  )
