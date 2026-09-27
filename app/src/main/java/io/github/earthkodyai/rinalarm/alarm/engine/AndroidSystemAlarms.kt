package io.github.earthkodyai.rinalarm.alarm.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.MainActivity
import io.github.earthkodyai.rinalarm.alarm.receiver.AlarmFireReceiver
import javax.inject.Inject

/**
 * setAlarmClock for every ring (S1: exact, Doze-proof, shows the alarm icon in the status bar). When exact alarms
 * are not allowed (only possible on Android 12-12L, where SCHEDULE_EXACT_ALARM can be revoked) it falls back to an
 * inexact Doze-allowed alarm; the engine logs ARMED_INEXACT and Diagnostics (1.4) warns.
 */
class AndroidSystemAlarms @Inject constructor(@ApplicationContext private val context: Context) : SystemAlarms {
  private val alarmManager = context.getSystemService(AlarmManager::class.java)

  override fun arm(ring: PendingRing): Boolean {
    val operation = fireIntent(ring.alarmId, ring.isSnooze)
    val at = ring.triggerAt.toEpochMilli()
    if (canScheduleExact()) {
      try {
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent()), operation)
        return true
      } catch (_: SecurityException) {
        // Permission revoked between the check and the call; fall through to inexact.
      }
    }
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
    return false
  }

  override fun disarm(alarmId: Long, snooze: Boolean) = alarmManager.cancel(fireIntent(alarmId, snooze))

  private fun canScheduleExact(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

  /** One PendingIntent per slot. The data URI keeps slots distinct even if request codes ever collide. */
  private fun fireIntent(alarmId: Long, snooze: Boolean): PendingIntent {
    val intent =
      Intent(context, AlarmFireReceiver::class.java)
        .setAction(AlarmFireReceiver.ACTION_FIRE)
        .setData("rinalarm://ring/$alarmId/${if (snooze) "snooze" else "regular"}".toUri())
        .putExtra(AlarmFireReceiver.EXTRA_ALARM_ID, alarmId)
        .putExtra(AlarmFireReceiver.EXTRA_SNOOZE, snooze)
    val requestCode = (alarmId * 2 + if (snooze) 1 else 0).toInt()
    return PendingIntent.getBroadcast(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
  }

  /** Where the system sends a tap on the status-bar alarm icon. */
  private fun showIntent(): PendingIntent =
    PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
}
