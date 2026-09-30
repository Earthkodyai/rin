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
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmEngine
import io.github.earthkodyai.rinalarm.data.db.AlarmDao
import io.github.earthkodyai.rinalarm.di.AppScope
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug builds only. Alarms are minute-precise, so "add" picks the first whole minute at least `sec` seconds away.
 *
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugAlarmReceiver --es cmd add --ei sec 60
 *   (optional `--es mission pads|cups|none|rin_picks`, default rin_picks; `--ei snooze 1` for a short snooze in tests)
 *   `--ei sec 0` rings at the next whole minute, the soonest an alarm can ring.
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugAlarmReceiver --es cmd clear
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
            val options = RingOptions(snoozeMinutes = intent.getIntExtra("snooze", RingOptions.DEFAULT_SNOOZE_MINUTES))
            val id = deps.engine().save(Alarm(time = at.toLocalTime(), label = LABEL, mission = mission, ring = options))
            Log.i(TAG, "added id=$id at=${at.toLocalTime()} mission=${mission.stored} snooze=${options.snoozeMinutes}m")
          }
          "clear" ->
            deps.alarmDao().getAll().filter { it.label == LABEL }.forEach {
              deps.engine().delete(it.id)
              Log.i(TAG, "deleted id=${it.id}")
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
  }
}
