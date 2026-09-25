package com.example.s1alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Scheduler {
  const val EXTRA_ID = "id"

  fun schedule(ctx: Context, a: Alarm) {
    val am = ctx.getSystemService(AlarmManager::class.java)
    val show = PendingIntent.getActivity(
      ctx, a.id, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
    am.setAlarmClock(AlarmManager.AlarmClockInfo(a.triggerAt, show), operation(ctx, a.id))
    AlarmStore.put(ctx, a)
    RingLog.log(ctx, "scheduled", a, "ringSec=${a.ringSec}")
  }

  fun cancel(ctx: Context, id: Int) {
    ctx.getSystemService(AlarmManager::class.java).cancel(operation(ctx, id))
    AlarmStore.remove(ctx, id)
  }

  /** Re-register everything after boot / update / clock change. Past alarms ring now. */
  fun restoreAll(ctx: Context, reason: String) {
    val now = System.currentTimeMillis()
    for (a in AlarmStore.all(ctx)) {
      if (a.triggerAt <= now) {
        RingLog.log(ctx, "missed", a, reason)
        AlarmStore.remove(ctx, a.id)
        RingService.start(ctx, a, late = true)
      } else {
        schedule(ctx, a)
      }
    }
  }

  private fun operation(ctx: Context, id: Int): PendingIntent =
    PendingIntent.getBroadcast(
      ctx, id,
      Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_ID, id),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
