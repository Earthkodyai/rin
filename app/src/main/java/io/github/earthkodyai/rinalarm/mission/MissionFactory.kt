package io.github.earthkodyai.rinalarm.mission

import android.content.Context
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.alarm.ring.RingPolicy
import io.github.earthkodyai.rinalarm.data.StickerStore
import javax.inject.Inject

/** Builds the running [Mission] for a planned type. */
fun interface MissionFactory {
  fun create(type: MissionType): Mission
}

class AndroidMissionFactory
@Inject
constructor(@ApplicationContext private val context: Context, private val stickers: StickerStore) : MissionFactory {
  override fun create(type: MissionType): Mission {
    val steps = StepSensor(context.getSystemService(SensorManager::class.java))
    return when (type) {
      MissionType.WALK -> WalkMission(steps, RingPolicy.WALK_STEPS)
      // No sticker (it was removed after the ring was planned): throws, and the ring screen offers a plain Dismiss.
      MissionType.QR -> QrMission(checkNotNull(stickers.current()) { "no QR sticker" }, steps)
    }
  }
}
