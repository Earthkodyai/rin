package io.github.earthkodyai.rinalarm.alarm.log

import android.util.Log
import io.github.earthkodyai.rinalarm.data.db.RingEventDao
import io.github.earthkodyai.rinalarm.data.db.RingEventEntity
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.Instant
import javax.inject.Inject

/** Everything the ring log records. Stored by name, so entries may be added but never renamed. */
enum class RingEventType {
  /** Pending rings brought back in line; detail = reason and counts. */
  RECONCILE,
  /** A ring registered with AlarmManager (logged only when its time changed); scheduledAt = trigger. */
  ARMED,
  /** Exact alarms not allowed, registered inexact instead: the ring may be late. */
  ARMED_INEXACT,
  ARM_FAILED,
  /** A pending ring dropped because its alarm is gone or switched off. */
  DISARMED,
  /** AlarmManager delivered a ring. at - scheduledAt is how late it fired; detail = device snapshot. */
  FIRED,
  /** Delivered more than a minute before the stored time (a stale registration); re-armed, not rung. */
  EARLY_FIRE,
  /** A second delivery of a ring that already fired. */
  DUPLICATE_FIRE,
  /** Delivered for an alarm that is deleted or off. */
  FIRE_UNKNOWN,
  /** Found more than 15 minutes overdue (phone off or asleep); the user got a notification instead. */
  MISSED,
  /** Sound started; at - FIRED.at is the fire-to-sound latency. */
  RING_START,
  RING_FAIL,
  /** The ring foreground service could not start. */
  FGS_FAIL,
  /** Another alarm fired while one was ringing; the current ring covers both. */
  OVERLAP,
  VOLUME_RAISED,
  /** Audio focus came through after being refused at ring start (Android 15+: once the ring page shows). */
  FOCUS_GRANTED,
  /** Tone paused (vibration goes on); detail reason=call or reason=focus. */
  TONE_PAUSED,
  TONE_RESUMED,
  SNOOZED,
  SNOOZE_DENIED,
  DISMISSED,
  AUTO_STOPPED,
}

/** Append-only log of alarm events: the evidence for the 14-night reliability run and for Diagnostics. */
interface RingLog {
  suspend fun record(type: RingEventType, alarmId: Long? = null, scheduledAt: Instant? = null, detail: String = "")
}

/** Writes to Room (device-protected, so it works before the first unlock) and mirrors every row to logcat. */
class RoomRingLog @Inject constructor(private val dao: RingEventDao, private val time: TimeSource) : RingLog {
  override suspend fun record(type: RingEventType, alarmId: Long?, scheduledAt: Instant?, detail: String) {
    val at = time.now()
    val lateMs = scheduledAt?.let { at.toEpochMilli() - it.toEpochMilli() }
    Log.i(TAG, "$type alarm=${alarmId ?: "-"} scheduled=${scheduledAt ?: "-"} deltaMs=${lateMs ?: "-"} $detail")
    // A failed log write must never break the ring path.
    runCatching {
      dao.insert(RingEventEntity(0, at.toEpochMilli(), type.name, alarmId, scheduledAt?.toEpochMilli(), detail))
    }
      .onFailure { Log.e(TAG, "ring log write failed", it) }
  }

  companion object {
    /** Filter with `adb logcat -s RinRing`. */
    const val TAG = "RinRing"
  }
}
