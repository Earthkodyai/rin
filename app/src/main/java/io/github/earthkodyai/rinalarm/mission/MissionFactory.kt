package io.github.earthkodyai.rinalarm.mission

import android.content.Context
import android.media.AudioAttributes
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.character.RinMouth
import io.github.earthkodyai.rinalarm.character.VoicePlayer
import io.github.earthkodyai.rinalarm.time.ElapsedClock
import javax.inject.Inject

/** Builds the running [Mission] for a planned type. */
fun interface MissionFactory {
  fun create(type: MissionType): Mission

  /** A practice round of [type] (UX.8): one short round, no alarm sounding. */
  fun practice(type: MissionType): Mission = create(type)
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

  /**
   * The real game's first round only, at its frozen timings (3.3–3.5): pads of 3, one shuffle of 4 swaps, one
   * sentence. Its sounds go to the media stream, as nothing is ringing.
   */
  override fun practice(type: MissionType): Mission =
    when (type) {
      MissionType.PADS ->
        ColourPadsMission(
          PadsRules(lengths = PadsRules().lengths.take(1)),
          System.nanoTime(),
          AndroidPadNotes(PRACTICE_NOTES),
          clock,
          quiet = RinMouth::awaitQuiet,
        )
      MissionType.CUPS ->
        CupShuffleMission(CupsRules(swaps = CupsRules().swaps.take(1)), System.nanoTime(), clock, quiet = RinMouth::awaitQuiet)
      MissionType.SPEECH ->
        RepeatAfterRinMission(
          RepeatRules(sentences = 1),
          sentences,
          System.nanoTime(),
          AndroidRinVoice(context, attributes = VoicePlayer.SPEECH),
          VoskListener(context, vosk),
          clock,
        )
    }

  private companion object {
    val PRACTICE_NOTES: AudioAttributes =
      AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
  }
}
