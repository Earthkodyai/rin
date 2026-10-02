package io.github.earthkodyai.rinalarm.data

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.testing.FakeAlarms
import io.github.earthkodyai.rinalarm.testing.FakeSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPoutOffTest {
  private val alarms =
    FakeAlarms(listOf(Alarm(id = 1, time = LocalTime.of(6, 30)), Alarm(id = 2, time = LocalTime.of(7, 0), enabled = false)))

  @Test
  fun noPoutingOn_turnsScoldingOff_onEveryAlarm_andForNewOnes_once() = runTest {
    val settings = FakeSettings(legacyPoutOff = true)
    LegacyPoutOff(settings, alarms, alarms).run()

    assertEquals(listOf(false, false), alarms.alarms.first().map { it.scold })
    assertFalse(settings.lastScold.value)
    assertFalse(settings.legacy)

    // The user turns it back on for new alarms: a later start leaves that alone.
    settings.lastScold.value = true
    LegacyPoutOff(settings, alarms, alarms).run()
    assertTrue(settings.lastScold.value)
  }

  @Test
  fun noPoutingOff_changesNothing() = runTest {
    val settings = FakeSettings()
    LegacyPoutOff(settings, alarms, alarms).run()

    assertEquals(listOf(true, true), alarms.alarms.first().map { it.scold })
    assertTrue(settings.lastScold.value)
    assertTrue(alarms.saves.isEmpty())
  }
}
