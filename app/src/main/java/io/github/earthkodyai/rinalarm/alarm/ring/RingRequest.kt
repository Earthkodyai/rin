package io.github.earthkodyai.rinalarm.alarm.ring

import android.content.Intent
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import java.time.Instant
import java.time.LocalTime

/**
 * Everything the ring service needs, carried in its start intent so ringing never waits on the database.
 *
 * @property scheduledAt the stored trigger; null when the ring fired without one (see AlarmEngine.onFire).
 * @property snoozeCount snoozes already taken; passed back to AlarmEngine.snooze.
 * @property late true when the ring started more than a minute after [scheduledAt] (phone was off or asleep).
 */
data class RingRequest(
  val alarmId: Long,
  val time: LocalTime,
  val label: String,
  val scheduledAt: Instant?,
  val snoozeCount: Int,
  val snoozesLeft: Int,
  val late: Boolean,
  val options: RingOptions,
) {
  fun putInto(intent: Intent): Intent =
    intent
      .putExtra(ALARM_ID, alarmId)
      .putExtra(MINUTE_OF_DAY, time.hour * 60 + time.minute)
      .putExtra(LABEL, label)
      .putExtra(SCHEDULED_AT, scheduledAt?.toEpochMilli() ?: -1L)
      .putExtra(SNOOZE_COUNT, snoozeCount)
      .putExtra(SNOOZES_LEFT, snoozesLeft)
      .putExtra(LATE, late)
      .putExtra(RAMP_SECONDS, options.rampSeconds)
      .putExtra(VIBRATE, options.vibrate)
      .putExtra(SNOOZE_MINUTES, options.snoozeMinutes)
      .putExtra(MAX_SNOOZES, options.maxSnoozes)

  companion object {
    private const val ALARM_ID = "ring.alarmId"
    private const val MINUTE_OF_DAY = "ring.minuteOfDay"
    private const val LABEL = "ring.label"
    private const val SCHEDULED_AT = "ring.scheduledAt"
    private const val SNOOZE_COUNT = "ring.snoozeCount"
    private const val SNOOZES_LEFT = "ring.snoozesLeft"
    private const val LATE = "ring.late"
    private const val RAMP_SECONDS = "ring.rampSeconds"
    private const val VIBRATE = "ring.vibrate"
    private const val SNOOZE_MINUTES = "ring.snoozeMinutes"
    private const val MAX_SNOOZES = "ring.maxSnoozes"

    /**
     * Never fails: a malformed intent still produces a request that rings with defaults, because the service must
     * reach startForeground either way.
     */
    fun from(intent: Intent): RingRequest {
      val minuteOfDay = intent.getIntExtra(MINUTE_OF_DAY, 0).coerceIn(0, 24 * 60 - 1)
      val defaults = RingOptions()
      return RingRequest(
        alarmId = intent.getLongExtra(ALARM_ID, -1),
        time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
        label = intent.getStringExtra(LABEL).orEmpty(),
        scheduledAt = intent.getLongExtra(SCHEDULED_AT, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
        snoozeCount = intent.getIntExtra(SNOOZE_COUNT, 0),
        snoozesLeft = intent.getIntExtra(SNOOZES_LEFT, 0),
        late = intent.getBooleanExtra(LATE, false),
        options =
          runCatching {
              RingOptions(
                rampSeconds = intent.getIntExtra(RAMP_SECONDS, defaults.rampSeconds),
                vibrate = intent.getBooleanExtra(VIBRATE, defaults.vibrate),
                snoozeMinutes = intent.getIntExtra(SNOOZE_MINUTES, defaults.snoozeMinutes),
                maxSnoozes = intent.getIntExtra(MAX_SNOOZES, defaults.maxSnoozes),
              )
            }
            .getOrDefault(defaults),
      )
    }
  }
}
