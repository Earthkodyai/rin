package io.github.earthkodyai.rinalarm.alarm.log

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.alarm.engine.DeviceStateProbe
import javax.inject.Inject

/** Ported from the S1 spike: everything that can silence or delay a ring, in one log-friendly line. */
class AndroidDeviceState @Inject constructor(@ApplicationContext private val context: Context) : DeviceStateProbe {
  override fun snapshot(): String =
    runCatching {
        val power = context.getSystemService(PowerManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val dnd =
          when (notifications.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
            else -> "?"
          }
        val ringer =
          when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
          }
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        val fullScreen =
          Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notifications.canUseFullScreenIntent()
        listOf(
            "screen=${if (power.isInteractive) "on" else "off"}",
            "idle=${power.isDeviceIdleMode}",
            "saver=${power.isPowerSaveMode}",
            "dozeExempt=${power.isIgnoringBatteryOptimizations(context.packageName)}",
            "dnd=$dnd",
            "ringer=$ringer",
            "alarmVol=${audio.getStreamVolume(AudioManager.STREAM_ALARM)}/" +
              audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            "exact=$exact",
            "fsi=$fullScreen",
            "notif=${notifications.areNotificationsEnabled()}",
          )
          .joinToString(" ")
      }
      .getOrElse { "snapshot_failed=${it.javaClass.simpleName}" }
}
