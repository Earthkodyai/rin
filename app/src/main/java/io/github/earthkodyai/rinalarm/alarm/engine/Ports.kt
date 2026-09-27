package io.github.earthkodyai.rinalarm.alarm.engine

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.ring.RingRequest
import java.time.Instant

// The Android side of the alarm engine, behind interfaces so AlarmEngineTest runs on the JVM with fakes.

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
