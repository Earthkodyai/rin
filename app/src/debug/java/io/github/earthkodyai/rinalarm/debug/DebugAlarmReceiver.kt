package io.github.earthkodyai.rinalarm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.AlarmSound
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmEngine
import io.github.earthkodyai.rinalarm.data.db.AlarmDao
import io.github.earthkodyai.rinalarm.di.AppScope
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.mission.Difficulty
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug builds only. Alarms are minute-precise, so "add" picks the first whole minute at least `sec` seconds away.
 *
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugAlarmReceiver --es cmd add --ei sec 60
 *   (optional `--es mission pads|cups|none|rin_picks`, default rin_picks; `--ei snooze 1` for a short snooze in tests;
 *   `--es sound rin_picks|beep|<theme id>`, default rin_picks; `--es level easy|normal|hard|nightmare`, default easy;
 *   `--ez scold false` for a ring with scolding off)
 *   `--ei sec 0` rings at the next whole minute, the soonest an alarm can ring.
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugAlarmReceiver --es cmd clear
 * `--es cmd clear-off` deletes every switched-off alarm (leftover test rings); `--es cmd demo` adds the store
 * screenshots' three alarms (task 6.5), switched on, so they ring for real until switched off.
 */
class DebugAlarmReceiver : BroadcastReceiver() {
  @EntryPoint
  @InstallIn(SingletonComponent::class)
  internal interface Deps {
    fun engine(): AlarmEngine

    fun alarmDao(): AlarmDao

    @AppScope fun scope(): CoroutineScope
  }

  override fun onReceive(context: Context, intent: Intent) {
    val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
    val pending = goAsync()
    deps.scope().launch {
      try {
        when (intent.getStringExtra("cmd")) {
          "add" -> {
            val sec = intent.getIntExtra("sec", 60).toLong()
            val at = LocalDateTime.now().plusSeconds(sec).truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            val mission = intent.getStringExtra("mission")?.let(MissionChoice::fromStored) ?: MissionChoice.RinPicks
            val sound = AlarmSound.fromStored(intent.getStringExtra("sound") ?: AlarmSound.DEFAULT_STORED)
            val options = RingOptions(snoozeMinutes = intent.getIntExtra("snooze", RingOptions.DEFAULT_SNOOZE_MINUTES), sound = sound)
            val level = Difficulty.fromStored(intent.getStringExtra("level") ?: Difficulty.DEFAULT_STORED)
            val scold = intent.getBooleanExtra("scold", true)
            val alarm = Alarm(time = at.toLocalTime(), label = LABEL, mission = mission, ring = options, difficulty = level, scold = scold)
            val id = deps.engine().save(alarm)
            Log.i(
              TAG,
              "added id=$id at=${at.toLocalTime()} mission=${mission.stored} level=${level.stored} scold=$scold " +
                "snooze=${options.snoozeMinutes}m sound=${sound.stored}",
            )
          }
          "clear" ->
            deps.alarmDao().getAll().filter { it.label == LABEL }.forEach {
              deps.engine().delete(it.id)
              Log.i(TAG, "deleted id=${it.id}")
            }
          "clear-off" ->
            deps.alarmDao().getAll().filter { !it.enabled && !it.isTest }.forEach {
              deps.engine().delete(it.id)
              Log.i(TAG, "deleted off id=${it.id}")
            }
          "demo" ->
            DEMO.forEach { alarm ->
              val id = deps.engine().save(alarm)
              Log.i(TAG, "demo id=$id at=${alarm.time} label=${alarm.label}")
            }
          else -> Log.w(TAG, "unknown cmd")
        }
      } finally {
        pending.finish()
      }
    }
  }

  private companion object {
    const val TAG = "RinDebug"
    const val LABEL = "smoke"

    /** What a real week might hold: the store screenshots' list. */
    val DEMO =
      listOf(
        Alarm(time = LocalTime.of(6, 30), repeatDays = RepeatDays.WEEKDAYS, label = "Work", mission = MissionChoice.Only(MissionType.PADS)),
        Alarm(
          time = LocalTime.of(7, 15),
          repeatDays = RepeatDays.of(DayOfWeek.SATURDAY),
          label = "Morning run",
          mission = MissionChoice.Only(MissionType.CUPS),
          ring = RingOptions(sound = AlarmSound.fromStored("cafe")),
        ),
        Alarm(time = LocalTime.of(8, 30), repeatDays = RepeatDays.WEEKEND, label = "Weekend"),
      )
  }
}
