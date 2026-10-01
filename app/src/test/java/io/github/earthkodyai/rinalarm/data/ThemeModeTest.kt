package io.github.earthkodyai.rinalarm.data

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
  @Test
  fun auto_isNightFrom19ToJustBefore6() {
    assertFalse(ThemeMode.AUTO.isNight(LocalTime.of(18, 59)))
    assertTrue(ThemeMode.AUTO.isNight(LocalTime.of(19, 0)))
    assertTrue(ThemeMode.AUTO.isNight(LocalTime.of(0, 0)))
    assertTrue(ThemeMode.AUTO.isNight(LocalTime.of(5, 59)))
    assertFalse(ThemeMode.AUTO.isNight(LocalTime.of(6, 0)))
  }

  @Test
  fun dayAndNight_ignoreTheClock() {
    assertFalse(ThemeMode.DAY.isNight(LocalTime.of(23, 0)))
    assertTrue(ThemeMode.NIGHT.isNight(LocalTime.of(12, 0)))
  }

  @Test
  fun anUnknownOrMissingValue_isAuto() {
    assertEquals(ThemeMode.AUTO, ThemeMode.fromStored(null))
    assertEquals(ThemeMode.AUTO, ThemeMode.fromStored("sepia"))
    ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStored(it.stored)) }
  }
}
