package io.github.earthkodyai.rinalarm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.RingOptions
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
  // Schema 2. The SQL defaults must match RingOptions' (AlarmEntityTest checks).
  @ColumnInfo(defaultValue = "30") val rampSeconds: Int = RingOptions.DEFAULT_RAMP_SECONDS,
  @ColumnInfo(defaultValue = "1") val vibrate: Boolean = true,
  @ColumnInfo(defaultValue = "5") val snoozeMinutes: Int = RingOptions.DEFAULT_SNOOZE_MINUTES,
  @ColumnInfo(defaultValue = "3") val maxSnoozes: Int = RingOptions.DEFAULT_MAX_SNOOZES,
  // Schema 3.
  @ColumnInfo(defaultValue = "0") val isTest: Boolean = false,
)

fun AlarmEntity.toAlarm(): Alarm =
  Alarm(
    id = id,
    time = LocalTime.of(hour, minute),
    repeatDays = RepeatDays.fromMask(repeatDays),
    label = label,
    enabled = enabled,
    ring = RingOptions(rampSeconds, vibrate, snoozeMinutes, maxSnoozes),
    isTest = isTest,
  )

fun Alarm.toEntity(): AlarmEntity =
  AlarmEntity(
    id = id,
    hour = time.hour,
    minute = time.minute,
    repeatDays = repeatDays.mask,
    label = label,
    enabled = enabled,
    rampSeconds = ring.rampSeconds,
    vibrate = ring.vibrate,
    snoozeMinutes = ring.snoozeMinutes,
    maxSnoozes = ring.maxSnoozes,
    isTest = isTest,
  )
