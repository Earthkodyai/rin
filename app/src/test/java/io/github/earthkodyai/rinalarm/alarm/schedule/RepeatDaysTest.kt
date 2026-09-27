package io.github.earthkodyai.rinalarm.alarm.schedule

import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatDaysTest {
  @Test
  fun none_isOneShot() {
    assertTrue(RepeatDays.NONE.isOneShot)
    assertTrue(RepeatDays.NONE.days.isEmpty())
  }

  @Test
  fun of_containsOnlyTheGivenDays() {
    val days = RepeatDays.of(MONDAY, WEDNESDAY)
    assertFalse(days.isOneShot)
    assertEquals(listOf(MONDAY, WEDNESDAY), days.days)
    assertFalse(SUNDAY in days)
  }

  @Test
  fun presets_matchTheirDays() {
    assertEquals(DayOfWeek.entries, RepeatDays.EVERY_DAY.days)
    assertEquals(listOf(SATURDAY, SUNDAY), RepeatDays.WEEKEND.days)
    assertFalse(SATURDAY in RepeatDays.WEEKDAYS)
    assertEquals(5, RepeatDays.WEEKDAYS.days.size)
  }

  @Test
  fun mask_usesIsoOrder_mondayIsBitZero() {
    assertEquals(0b0000001, RepeatDays.of(MONDAY).mask)
    assertEquals(0b1000000, RepeatDays.of(SUNDAY).mask)
  }

  @Test
  fun fromMask_roundTripsEveryValidMask() {
    for (mask in 0..0x7F) assertEquals(mask, RepeatDays.fromMask(mask).mask)
  }

  @Test
  fun fromMask_rejectsBitsOutsideTheWeek() {
    assertThrows(IllegalArgumentException::class.java) { RepeatDays.fromMask(0x80) }
    assertThrows(IllegalArgumentException::class.java) { RepeatDays.fromMask(-1) }
  }

  @Test
  fun toggle_addsThenRemoves() {
    val withSunday = RepeatDays.WEEKDAYS.toggle(SUNDAY)
    assertTrue(SUNDAY in withSunday)
    assertEquals(RepeatDays.WEEKDAYS, withSunday.toggle(SUNDAY))
  }
}
