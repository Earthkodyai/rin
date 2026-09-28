package io.github.earthkodyai.rinalarm.setup

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.mission.MissionReadiness
import io.github.earthkodyai.rinalarm.mission.Readiness
import javax.inject.Inject

class AndroidDeviceStatus
@Inject
constructor(@ApplicationContext private val context: Context, private val missions: MissionReadiness) :
  DeviceStatusSource {
  override fun read(): DeviceStatus {
    val notifications = context.getSystemService(NotificationManager::class.java)
    val power = context.getSystemService(PowerManager::class.java)
    val audio = context.getSystemService(AudioManager::class.java)
    val alarms = context.getSystemService(AlarmManager::class.java)
    // HyperOS can leave the app-level switch off with the permission granted (1.2), so both must hold.
    val permission =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    return DeviceStatus(
      sdk = Build.VERSION.SDK_INT,
      notificationsAllowed = permission && NotificationManagerCompat.from(context).areNotificationsEnabled(),
      fullScreenAllowed =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) notifications.canUseFullScreenIntent() else null,
      exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms(),
      batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
      powerSaveOn = power.isPowerSaveMode,
      alarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM),
      alarmVolumeMax = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
      dndSilencesAlarms = notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE,
      xiaomiFamily = Build.MANUFACTURER.lowercase() in XIAOMI_FAMILY || Build.BRAND.lowercase() in XIAOMI_FAMILY,
      device = "${Build.MANUFACTURER} ${Build.MODEL}",
      androidRelease = Build.VERSION.RELEASE,
      appVersion =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?",
      readyMissions = missions.check().filterValues { it == Readiness.READY }.keys.map { it.stored },
    )
  }

  private companion object {
    val XIAOMI_FAMILY = setOf("xiaomi", "redmi", "poco")
  }
}
