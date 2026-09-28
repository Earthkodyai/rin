package io.github.earthkodyai.rinalarm.mission

import android.content.Context
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.alarm.ring.RingPolicy
import javax.inject.Inject

/** Builds the running [Mission] for a planned type. */
fun interface MissionFactory {
  fun create(type: MissionType): Mission
}

class AndroidMissionFactory @Inject constructor(@ApplicationContext private val context: Context) : MissionFactory {
  override fun create(type: MissionType): Mission =
    when (type) {
      MissionType.WALK -> WalkMission(context.getSystemService(SensorManager::class.java), RingPolicy.WALK_STEPS)
    }
}
