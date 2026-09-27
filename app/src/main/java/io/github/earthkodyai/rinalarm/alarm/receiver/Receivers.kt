package io.github.earthkodyai.rinalarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmEngine
import io.github.earthkodyai.rinalarm.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Both receivers are directBootAware (manifest): they run before the first unlock after a reboot, which works because
// the database lives in device-protected storage. S1 showed HyperOS blocks BOOT_COMPLETED for apps without Autostart
// but still delivers LOCKED_BOOT_COMPLETED.

/** AlarmManager delivers every ring here (not exported; only our PendingIntents can reach it). */
class AlarmFireReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_FIRE) return
    val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
    if (alarmId < 0) return
    val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
    goAsync(context) { onFire(alarmId, snooze) }
  }

  companion object {
    const val ACTION_FIRE = "io.github.earthkodyai.rinalarm.action.FIRE"
    const val EXTRA_ALARM_ID = "alarmId"
    const val EXTRA_SNOOZE = "snooze"
  }
}

/**
 * Re-arms everything after events that wipe or shift AlarmManager registrations. Exported because the system sends
 * these; an app spoofing one only triggers a harmless reconcile, and unknown actions are ignored.
 */
class RescheduleReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    if (action !in ACTIONS) return
    goAsync(context) { reconcile(action.substringAfterLast('.')) }
  }

  internal companion object {
    /** Every action handled; ReceiverManifestTest checks the manifest registers each one. */
    val ACTIONS =
      setOf(
        Intent.ACTION_LOCKED_BOOT_COMPLETED,
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
      )
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReceiverEntryPoint {
  fun engine(): AlarmEngine

  @AppScope fun scope(): CoroutineScope
}

/** Runs [block] off the main thread while keeping the broadcast alive (Android allows about 10 s). */
private fun BroadcastReceiver.goAsync(context: Context, block: suspend AlarmEngine.() -> Unit) {
  val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
  // Null when onReceive is called directly rather than by a broadcast (receiver tests do this for the protected
  // system actions they cannot send).
  val pending: BroadcastReceiver.PendingResult? = goAsync()
  entryPoint.scope().launch {
    try {
      entryPoint.engine().block()
    } finally {
      pending?.finish()
    }
  }
}
