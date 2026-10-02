package io.github.earthkodyai.rinalarm.alarm.ring

import android.content.Intent
import io.github.earthkodyai.rinalarm.alarm.AlarmSound
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.mission.Difficulty
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import java.time.Instant
import java.time.LocalTime

/**
 * Everything the ring service needs, carried in its start intent so ringing never waits on the database.
 *
 * @property scheduledAt the stored trigger; null when the ring fired without one (see AlarmEngine.onFire).
 * @property snoozeCount snoozes already taken; passed back to AlarmEngine.snooze.
 * @property late true when the ring started more than a minute after [scheduledAt] (phone was off or asleep).
 * @property mission the alarm's choice; RingService turns it into a MissionPlan when ringing starts.
 * @property isTest the Diagnostics test alarm: it never uses up a rest or sick day meant for a real morning.
 * @property difficulty the game's level (Phase G); [scold] whether Rin scolds a miss (G.1).
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
  val mission: MissionChoice = MissionChoice.RinPicks,
  val isTest: Boolean = false,
  val difficulty: Difficulty = Difficulty.EASY,
  val scold: Boolean = true,
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
      .putExtra(SOUND, options.sound.stored)
      .putExtra(MISSION, mission.stored)
      .putExtra(IS_TEST, isTest)
      .putExtra(DIFFICULTY, difficulty.stored)
      .putExtra(SCOLD, scold)

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
    private const val SOUND = "ring.sound"
    private const val MISSION = "ring.mission"
    private const val IS_TEST = "ring.isTest"
    private const val DIFFICULTY = "ring.difficulty"
    private const val SCOLD = "ring.scold"

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
                sound = AlarmSound.fromStored(intent.getStringExtra(SOUND) ?: AlarmSound.DEFAULT_STORED),
              )
            }
            .getOrDefault(defaults),
        mission = MissionChoice.fromStored(intent.getStringExtra(MISSION) ?: MissionChoice.DEFAULT_STORED),
        isTest = intent.getBooleanExtra(IS_TEST, false),
        difficulty = Difficulty.fromStored(intent.getStringExtra(DIFFICULTY)),
        scold = intent.getBooleanExtra(SCOLD, true),
      )
    }
  }
}
