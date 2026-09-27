package io.github.earthkodyai.rinalarm.setup

import io.github.earthkodyai.rinalarm.setup.CheckId.ALARM_VOLUME
import io.github.earthkodyai.rinalarm.setup.CheckId.AUTOSTART
import io.github.earthkodyai.rinalarm.setup.CheckId.BATTERY
import io.github.earthkodyai.rinalarm.setup.CheckId.DO_NOT_DISTURB
import io.github.earthkodyai.rinalarm.setup.CheckId.EXACT_ALARMS
import io.github.earthkodyai.rinalarm.setup.CheckId.FULL_SCREEN
import io.github.earthkodyai.rinalarm.setup.CheckId.LOCK_SCREEN
import io.github.earthkodyai.rinalarm.setup.CheckId.NOTIFICATIONS
import io.github.earthkodyai.rinalarm.testing.FakeDeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecksTest {
  private val good = FakeDeviceStatus.ALL_GOOD

  private fun severities(status: DeviceStatus): Map<CheckId, Severity> =
    SetupChecks.evaluate(status).associate { it.id to it.severity }

  @Test
  fun allGood_everyCheckOk_noBanner() {
    val results = SetupChecks.evaluate(good)
    assertEquals(listOf(NOTIFICATIONS, FULL_SCREEN, EXACT_ALARMS, ALARM_VOLUME, DO_NOT_DISTURB, BATTERY), results.map { it.id })
    assertTrue(results.all { it.severity == Severity.OK })
    assertFalse(SetupChecks.hasCritical(results))
  }

  @Test
  fun notificationsOff_isCritical() {
    val results = SetupChecks.evaluate(good.copy(notificationsAllowed = false))
    assertEquals(Severity.CRITICAL, results.first { it.id == NOTIFICATIONS }.severity)
    assertTrue(SetupChecks.hasCritical(results))
  }

  @Test
  fun exactAlarmsOff_isCritical() {
    assertEquals(Severity.CRITICAL, severities(good.copy(exactAlarmsAllowed = false))[EXACT_ALARMS])
  }

  @Test
  fun totalSilence_isCritical() {
    assertEquals(Severity.CRITICAL, severities(good.copy(dndSilencesAlarms = true))[DO_NOT_DISTURB])
  }

  @Test
  fun fullScreenOff_isOnlyAWarning_theSoundStillPlays() {
    val results = SetupChecks.evaluate(good.copy(fullScreenAllowed = false))
    assertEquals(Severity.WARNING, results.first { it.id == FULL_SCREEN }.severity)
    assertFalse(SetupChecks.hasCritical(results))
  }

  @Test
  fun belowAndroid14_hasNoFullScreenCheck() {
    assertFalse(FULL_SCREEN in severities(good.copy(sdk = 33, fullScreenAllowed = null)))
  }

  @Test
  fun lowVolume_isANote_becauseTheRingRaisesIt() {
    assertEquals(Severity.INFO, severities(good.copy(alarmVolume = 5, alarmVolumeMax = 15))[ALARM_VOLUME])
    assertEquals(Severity.INFO, severities(good.copy(alarmVolume = 0))[ALARM_VOLUME])
    assertEquals(Severity.OK, severities(good.copy(alarmVolume = 6, alarmVolumeMax = 15))[ALARM_VOLUME])
  }

  @Test
  fun batteryRestrictions_areNotes() {
    assertEquals(Severity.INFO, severities(good.copy(batteryUnrestricted = false))[BATTERY])
    assertEquals(Severity.INFO, severities(good.copy(powerSaveOn = true))[BATTERY])
  }

  @Test
  fun xiaomi_getsTheLockScreenAndAutostartTips() {
    val xiaomi = severities(good.copy(xiaomiFamily = true))
    assertEquals(Severity.INFO, xiaomi[LOCK_SCREEN])
    assertEquals(Severity.INFO, xiaomi[AUTOSTART])
    assertFalse(LOCK_SCREEN in severities(good))
    assertFalse(AUTOSTART in severities(good))
  }
}
