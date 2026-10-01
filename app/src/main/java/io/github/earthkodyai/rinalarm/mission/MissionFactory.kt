package io.github.earthkodyai.rinalarm.mission

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.character.RinMouth
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
  private val clock: ElapsedClock,
  private val vosk: VoskModels,
) : MissionFactory {
  private val sentences by lazy { context.assets.open(RepeatSentences.ASSET).use { RepeatSentences.parse(it.reader().readText()) } }

  override fun create(type: MissionType): Mission =
    when (type) {
      // A fresh seed per ring, logged with the result so a game can be replayed.
      MissionType.PADS -> ColourPadsMission(PadsRules(), System.nanoTime(), AndroidPadNotes(), clock, quiet = RinMouth::awaitQuiet)
      MissionType.CUPS -> CupShuffleMission(CupsRules(), System.nanoTime(), clock, quiet = RinMouth::awaitQuiet)
      MissionType.SPEECH ->
        RepeatAfterRinMission(RepeatRules(), sentences, System.nanoTime(), AndroidRinVoice(context), VoskListener(context, vosk), clock)
    }
}
