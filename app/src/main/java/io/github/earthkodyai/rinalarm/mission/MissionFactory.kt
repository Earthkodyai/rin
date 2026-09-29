package io.github.earthkodyai.rinalarm.mission

import android.content.Context
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.data.StickerStore
import io.github.earthkodyai.rinalarm.time.ElapsedClock
import javax.inject.Inject

/** Builds the running [Mission] for a planned type. */
fun interface MissionFactory {
  fun create(type: MissionType): Mission
}

class AndroidMissionFactory
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val stickers: StickerStore,
  private val clock: ElapsedClock,
) : MissionFactory {
  override fun create(type: MissionType): Mission =
    when (type) {
      // A fresh seed per ring, logged with the result so a game can be replayed.
      MissionType.PADS -> ColourPadsMission(PadsRules(), System.nanoTime(), AndroidPadNotes(), clock)
      MissionType.CUPS -> CupShuffleMission(CupsRules(), System.nanoTime(), clock)
      // No sticker (it was removed after the ring was planned): throws, and the ring screen offers a plain Dismiss.
      MissionType.QR ->
        QrMission(
          checkNotNull(stickers.current()) { "no QR sticker" },
          StepSensor(context.getSystemService(SensorManager::class.java)),
        )
    }
}
