package io.github.earthkodyai.rinalarm.alarm

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSoundTest {
  private val themes = listOf("morning", "cafe", "arcade", "sunny")
  private val day = LocalDate.of(2026, 10, 2)

  @Test
  fun stored_roundTrips_andJunkReadsAsRinPicks() {
    for (sound in listOf(AlarmSound.RinPicks, AlarmSound.Beep, AlarmSound.Theme("cafe"))) {
      assertEquals(sound, AlarmSound.fromStored(sound.stored))
    }
    assertEquals(AlarmSound.RinPicks, AlarmSound.fromStored(""))
    assertEquals(AlarmSound.RinPicks, AlarmSound.fromStored("../secret"))
    assertEquals(AlarmSound.RinPicks, AlarmSound.fromStored("Cafe"))
  }

  @Test
  fun rinPicks_playsEveryThemeOnceInAsManyDays_andNeverTheSameTwiceInARow() {
    val picks = (0L until 8L).map { AlarmSound.resolve(AlarmSound.RinPicks, themes, day.plusDays(it)) }
    assertEquals(themes.toSet(), picks.take(4).toSet())
    assertTrue(picks.zipWithNext().all { (a, b) -> a != b })
  }

  @Test
  fun aPinnedTheme_plays_andAMissingOneFallsBackToRinsPick() {
    assertEquals("cafe", AlarmSound.resolve(AlarmSound.Theme("cafe"), themes, day))
    assertEquals(
      AlarmSound.resolve(AlarmSound.RinPicks, themes, day),
      AlarmSound.resolve(AlarmSound.Theme("dropped"), themes, day),
    )
  }

  @Test
  fun theBeep_orNoThemesAtAll_meansTheBeep() {
    assertNull(AlarmSound.resolve(AlarmSound.Beep, themes, day))
    assertNull(AlarmSound.resolve(AlarmSound.RinPicks, emptyList(), day))
    assertNull(AlarmSound.resolve(AlarmSound.Theme("cafe"), emptyList(), day))
  }

  @Test
  fun daysBeforeTheEpoch_stillPick() {
    assertEquals("morning", AlarmSound.resolve(AlarmSound.RinPicks, themes, LocalDate.ofEpochDay(-4)))
  }
}
