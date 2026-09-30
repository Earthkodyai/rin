package io.github.earthkodyai.rinalarm.setup

import io.github.earthkodyai.rinalarm.alarm.ring.RingPolicy

/**
 * What the phone allows right now, read fresh each time (the user can change any of it in Settings). Onboarding,
 * Diagnostics and the main-screen banner all judge it through [SetupChecks], so they never disagree.
 *
 * @property fullScreenAllowed null below Android 14, where full-screen intents need no grant.
 * @property xiaomiFamily Xiaomi, Redmi or POCO (HyperOS/MIUI): gets the Autostart and battery tips.
 */
data class DeviceStatus(
  val sdk: Int,
  val notificationsAllowed: Boolean,
  val fullScreenAllowed: Boolean?,
  val exactAlarmsAllowed: Boolean,
  val batteryUnrestricted: Boolean,
  val powerSaveOn: Boolean,
  val alarmVolume: Int,
  val alarmVolumeMax: Int,
  /** Do Not Disturb set to total silence, which mutes alarms too. */
  val dndSilencesAlarms: Boolean,
  val xiaomiFamily: Boolean,
  val device: String,
  val androidRelease: String,
  val appVersion: String,
  /** Missions that can run now (MissionType.stored), or null when not read (previews). */
  val readyMissions: List<String>? = null,
  /** RECORD_AUDIO for Repeat after Rin; null when the game cannot run here anyway (no mic) or not read. */
  val micAllowed: Boolean? = null,
)

fun interface DeviceStatusSource {
  fun read(): DeviceStatus
}

enum class CheckId {
  NOTIFICATIONS,
  FULL_SCREEN,
  EXACT_ALARMS,
  ALARM_VOLUME,
  DO_NOT_DISTURB,
  BATTERY,
  /**
   * HyperOS "Show on Lock screen" (MIUIOP 10020), separate from Android's full-screen permission. Off by default for
   * sideloaded apps; it blocks the ring page over the lock screen ("Show when locked PermissionDenied", 1.4 device
   * run). No public API reads it, so it is always shown as a tip.
   */
  LOCK_SCREEN,
  /** HyperOS Autostart. No API can read it, so it is always shown as a tip. */
  AUTOSTART,
  /** At least one mission can run (task 3.1); with none, alarms still ring and stop with a plain Dismiss. */
  MISSIONS,
  /**
   * The mic for Repeat after Rin (task 3.5). Asked when the user picks the game; here so Rin picks can include it
   * without editing an alarm. A note, not a warning: the other games need nothing.
   */
  MICROPHONE,
}

/**
 * After an app update: tell the user when full-screen alarms were turned off ([fullScreenAllowed] false; null below
 * Android 14, where it needs no grant) and a notification can reach them at all.
 */
fun fullScreenLostNotice(fullScreenAllowed: Boolean?, canNotify: Boolean): Boolean = fullScreenAllowed == false && canNotify

enum class Severity {
  OK,
  /** Worth knowing; alarms still ring on time. */
  INFO,
  /** Alarms ring, but something the user expects is missing (the lock-screen ring UI). */
  WARNING,
  /** An alarm could be hidden, late or silent. The main screen shows a banner. */
  CRITICAL,
}

data class CheckResult(val id: CheckId, val severity: Severity)

/** The rules (08-error-handling.md), as a pure function so they are unit-tested. */
object SetupChecks {
  fun evaluate(status: DeviceStatus): List<CheckResult> = buildList {
    // Off: Android hides the ring notification and blocks the full-screen launch; only the sound is left (1.2).
    add(CheckResult(CheckId.NOTIFICATIONS, if (status.notificationsAllowed) Severity.OK else Severity.CRITICAL))
    // Off: a heads-up notification instead of the ring screen over the lock screen; the sound is unaffected (S1). Raised
    // from WARNING (1.4) on 2026-10-01: HyperOS turned it off again on an app update, the user got only the sound and
    // had to unlock, and nothing on the main screen said so. Hidden is one of the three things the banner is for.
    status.fullScreenAllowed?.let { add(CheckResult(CheckId.FULL_SCREEN, if (it) Severity.OK else Severity.CRITICAL)) }
    // Only revocable on Android 12-12L; off means inexact rings that can be minutes late.
    add(CheckResult(CheckId.EXACT_ALARMS, if (status.exactAlarmsAllowed) Severity.OK else Severity.CRITICAL))
    // The ring raises a low alarm volume to the floor by itself, so a low volume is only worth a note.
    val lowVolume = RingPolicy.volumeFloorIndex(status.alarmVolume, status.alarmVolumeMax) != null
    add(CheckResult(CheckId.ALARM_VOLUME, if (lowVolume) Severity.INFO else Severity.OK))
    add(CheckResult(CheckId.DO_NOT_DISTURB, if (status.dndSilencesAlarms) Severity.CRITICAL else Severity.OK))
    // setAlarmClock is exempt from Doze and rang on time with battery saver on (S1), so these are notes only.
    add(
      CheckResult(
        CheckId.BATTERY,
        if (status.batteryUnrestricted && !status.powerSaveOn) Severity.OK else Severity.INFO,
      )
    )
    // No ready mission: rings are unaffected, only the out-of-bed task is missing.
    status.readyMissions?.let {
      add(CheckResult(CheckId.MISSIONS, if (it.isEmpty()) Severity.WARNING else Severity.OK))
    }
    status.micAllowed?.let { add(CheckResult(CheckId.MICROPHONE, if (it) Severity.OK else Severity.INFO)) }
    // Autostart off blocks BOOT_COMPLETED on HyperOS, but LOCKED_BOOT_COMPLETED still re-arms (S1).
    if (status.xiaomiFamily) {
      add(CheckResult(CheckId.LOCK_SCREEN, Severity.INFO))
      add(CheckResult(CheckId.AUTOSTART, Severity.INFO))
    }
  }

  fun hasCritical(results: List<CheckResult>): Boolean = results.any { it.severity == Severity.CRITICAL }
}
