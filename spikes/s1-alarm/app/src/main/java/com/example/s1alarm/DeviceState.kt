package com.example.s1alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager

/** One-line snapshot of everything that can silence or delay an alarm. */
fun deviceState(ctx: Context): String {
  val pm = ctx.getSystemService(PowerManager::class.java)
  val nm = ctx.getSystemService(NotificationManager::class.java)
  val audio = ctx.getSystemService(AudioManager::class.java)
  val am = ctx.getSystemService(AlarmManager::class.java)
  val zen = when (nm.currentInterruptionFilter) {
    NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
    NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
    NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
    NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
    else -> "?"
  }
  val ringer = when (audio.ringerMode) {
    AudioManager.RINGER_MODE_SILENT -> "silent"
    AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
    else -> "normal"
  }
  val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
  val fsi = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
  return listOf(
    "screen=${if (pm.isInteractive) "on" else "off"}",
    "idle=${pm.isDeviceIdleMode}",
    "saver=${pm.isPowerSaveMode}",
    "dozeExempt=${pm.isIgnoringBatteryOptimizations(ctx.packageName)}",
    "dnd=$zen",
    "ringer=$ringer",
    "alarmVol=${audio.getStreamVolume(AudioManager.STREAM_ALARM)}/${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}",
    "exact=$exact",
    "fsi=$fsi",
    "notif=${nm.areNotificationsEnabled()}",
  ).joinToString(" ")
}
