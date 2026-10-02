package io.github.earthkodyai.rinalarm.alarm.engine

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.ring.RingRequest
import java.time.Instant

// The Android side of the alarm engine, behind interfaces so AlarmEngineTest runs on the JVM with fakes.

/**
 * The only way the UI changes alarms. Implemented by [AlarmEngine], so every edit re-arms AlarmManager and the pending
 * rings under the engine's lock; AlarmRepository is read-only on purpose.
 */
interface AlarmWriter {
  /** Inserts when [Alarm.id] is 0, otherwise updates; re-arms from scratch and cancels a pending snooze. */
  suspend fun save(alarm: Alarm): Long

  /** Switches [alarmId] on or off. Does nothing if it was deleted meanwhile. */
  suspend fun setEnabled(alarmId: Long, enabled: Boolean)

  suspend fun delete(alarmId: Long)

  /** The scold switch (G.1): changes nothing else and re-arms nothing, as it may be flipped while [alarmId] rings. */
  suspend fun setScold(alarmId: Long, scold: Boolean)

  /**
   * Replaces any earlier test alarm with a one-shot test alarm labelled [label], ringing at the first whole minute
   * at least a minute away (alarms are minute-precise). Returns when it will ring.
   */
  suspend fun scheduleTest(label: String): Instant

  suspend fun cancelTest()
}

/** AlarmManager. */
interface SystemAlarms {
  /** Registers [ring], replacing any earlier registration of the same slot. Returns false when it had to go inexact. */
  fun arm(ring: PendingRing): Boolean

  fun disarm(alarmId: Long, snooze: Boolean)
}

/** Starts the ringing foreground service. */
fun interface Ringer {
  /** Returns false when Android refused to start the service. */
  fun start(request: RingRequest): Boolean
}

fun interface MissedAlarmNotifier {
  fun notifyMissed(alarm: Alarm, scheduledAt: Instant)
}

/** One line describing what could silence or delay a ring right now (screen, Doze, DND, volume, permissions). */
fun interface DeviceStateProbe {
  fun snapshot(): String
}
