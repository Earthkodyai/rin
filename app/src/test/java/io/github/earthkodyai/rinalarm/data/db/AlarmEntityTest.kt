package io.github.earthkodyai.rinalarm.data.db

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
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
}
