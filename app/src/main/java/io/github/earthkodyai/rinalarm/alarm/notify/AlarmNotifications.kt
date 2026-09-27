package io.github.earthkodyai.rinalarm.alarm.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.MainActivity
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.engine.MissedAlarmNotifier
import io.github.earthkodyai.rinalarm.alarm.ring.RingActivity
import io.github.earthkodyai.rinalarm.alarm.ring.RingRequest
import io.github.earthkodyai.rinalarm.alarm.ring.RingService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmNotifications @Inject constructor(@ApplicationContext private val context: Context) : MissedAlarmNotifier {
  private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

  /** Called from Application.onCreate. Channels are cheap and idempotent. */
  fun createChannels() {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH)
        .apply {
          // The service plays the sound and vibration itself, on USAGE_ALARM.
          setSound(null, null)
          enableVibration(false)
          lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
    )
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT)
    )
  }

  /**
   * The ring service's foreground notification. Its full-screen intent opens RingActivity over the lock screen. If
   * the full-screen permission is off (HyperOS turns it off for sideloaded apps, S1) Android shows it as a heads-up
   * notification instead; the sound never depends on it.
   */
  fun ringing(request: RingRequest): Notification {
    val fullScreen =
      PendingIntent.getActivity(
        context,
        0,
        Intent(context, RingActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val builder =
      NotificationCompat.Builder(context, CHANNEL_RINGING)
        .setSmallIcon(R.drawable.ic_stat_alarm)
        .setContentTitle(request.label.ifBlank { context.getString(R.string.ring_default_label) })
        .setContentText(
          request.time.format(timeFormat) + if (request.late) " · " + context.getString(R.string.ring_late) else ""
        )
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setFullScreenIntent(fullScreen, true)
        .setContentIntent(fullScreen)
    if (request.snoozesLeft > 0) {
      builder.addAction(0, context.getString(R.string.ring_snooze), serviceAction(RingService.snoozeIntent(context), 1))
    }
    builder.addAction(
      0,
      context.getString(R.string.ring_dismiss),
      serviceAction(RingService.dismissIntent(context, "notification"), 2),
    )
    return builder.build()
  }

  override fun notifyMissed(alarm: Alarm, scheduledAt: Instant) {
    if (!canNotify()) return
    val open =
      PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val time = scheduledAt.atZone(ZoneId.systemDefault()).format(timeFormat)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_MISSED)
        .setSmallIcon(R.drawable.ic_stat_alarm)
        .setContentTitle(context.getString(R.string.missed_title, time))
        .setContentText(context.getString(R.string.missed_text))
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    // canNotify() checked POST_NOTIFICATIONS; lint cannot see through the helper.
    @Suppress("MissingPermission")
    NotificationManagerCompat.from(context).notify(MISSED_ID_BASE + alarm.id.toInt(), notification)
  }

  private fun canNotify(): Boolean =
    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

  private fun serviceAction(intent: Intent, requestCode: Int): PendingIntent =
    PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

  companion object {
    const val CHANNEL_RINGING = "ringing"
    const val CHANNEL_MISSED = "missed"
    const val RINGING_ID = 1
    private const val MISSED_ID_BASE = 1000
  }
}
