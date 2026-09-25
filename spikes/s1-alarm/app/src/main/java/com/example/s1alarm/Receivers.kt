package com.example.s1alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent) {
    val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
    val a = AlarmStore.all(ctx).firstOrNull { it.id == id }
    if (a == null) {
      RingLog.log(ctx, "fire_unknown", null, "id=$id")
      return
    }
    RingLog.log(ctx, "fired", a, deviceState(ctx))
    AlarmStore.remove(ctx, id)
    RingService.start(ctx, a, late = false)
  }
}

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent) {
    val action = intent.action?.substringAfterLast('.') ?: "?"
    RingLog.log(ctx, "restore", null, "$action pending=${AlarmStore.all(ctx).size}")
    Scheduler.restoreAll(ctx, action)
  }
}
