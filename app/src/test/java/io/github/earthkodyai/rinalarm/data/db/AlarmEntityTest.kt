package io.github.earthkodyai.rinalarm.data.db

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmEntityTest {
  @Test
  fun alarm_roundTripsThroughTheEntity() {
    val alarm =
      Alarm(
        id = 7,
        time = LocalTime.of(6, 45),
        repeatDays = RepeatDays.of(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY),
        label = "Gym",
        enabled = false,
        ring = RingOptions(rampSeconds = 0, vibrate = false, snoozeMinutes = 9, maxSnoozes = 1),
      )
    assertEquals(alarm, alarm.toEntity().toAlarm())
  }

  @Test
  fun entity_storesWallClockFieldsAndMask() {
    val entity = Alarm(time = LocalTime.of(23, 5), repeatDays = RepeatDays.WEEKEND).toEntity()
    assertEquals(23, entity.hour)
    assertEquals(5, entity.minute)
    assertEquals(0b1100000, entity.repeatDays)
  }

  @Test
  fun sqlColumnDefaults_matchRingOptionsDefaults() {
    // Rows migrated from schema 1 get the SQL defaults; they must be the same alarm a new one would be.
    val defaults = RingOptions()
    // Read what Room actually uses: the exported schema (unit tests run with the module as working directory).
    val schema = File("schemas/io.github.earthkodyai.rinalarm.data.db.RinDatabase/2.json").readText()
    val sql =
      Regex(""""fieldPath": "(\w+)",[^}]*?"defaultValue": "([^"]*)"""")
        .findAll(schema)
        .associate { it.groupValues[1] to it.groupValues[2] }
    assertEquals(defaults.rampSeconds.toString(), sql["rampSeconds"])
    assertEquals(if (defaults.vibrate) "1" else "0", sql["vibrate"])
    assertEquals(defaults.snoozeMinutes.toString(), sql["snoozeMinutes"])
    assertEquals(defaults.maxSnoozes.toString(), sql["maxSnoozes"])
  }
}
