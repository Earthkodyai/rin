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
  /** [type] at [Difficulty.EASY]. */
  fun create(type: MissionType): Mission

  /** [type] at the alarm's [difficulty] (Phase G); Repeat after Rin has one level. */
  fun create(type: MissionType, difficulty: Difficulty): Mission = create(type)

  /** A practice round of [type] (UX.8): one short round at [difficulty], no alarm sounding. */
  fun practice(type: MissionType, difficulty: Difficulty): Mission = create(type, difficulty)

  /**
   * One level of the tournament (G.5): a single round at [TournamentLadder]'s rules, no alarm sounding, no waiting on
   * Rin (she is silent in the tournament). Only the app's factory plays it.
   */
  fun tournament(game: TournamentGame, level: Int): Mission = throw UnsupportedOperationException("no tournament here")
}

class AndroidMissionFactory
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val clock: ElapsedClock,
  private val vosk: VoskModels,
) : MissionFactory {
  private val sentences by lazy { context.assets.open(RepeatSentences.ASSET).use { RepeatSentences.parse(it.reader().readText()) } }

  override fun create(type: MissionType): Mission = create(type, Difficulty.EASY)

  override fun create(type: MissionType, difficulty: Difficulty): Mission =
    when (type) {
      // A fresh seed per ring, logged with the result so a game can be replayed.
      MissionType.PADS ->
        ColourPadsMission(PadsRules.forLevel(difficulty), System.nanoTime(), AndroidPadNotes(), clock, quiet = RinMouth::awaitQuiet)
      MissionType.CUPS ->
        CupShuffleMission(CupsRules.forLevel(difficulty), System.nanoTime(), clock, quiet = RinMouth::awaitQuiet)
      MissionType.SPEECH ->
        RepeatAfterRinMission(RepeatRules(), sentences, System.nanoTime(), AndroidRinVoice(context), VoskListener(context, vosk), clock)
    }

  /**
   * The real game's first round only, at the level's rules: at Easy pads of 3, one shuffle of 4 swaps; one
   * sentence. Its sounds go to the media stream, as nothing is ringing.
   */
  override fun practice(type: MissionType, difficulty: Difficulty): Mission =
    when (type) {
      MissionType.PADS ->
        ColourPadsMission(
          PadsRules.forLevel(difficulty).let { it.copy(lengths = it.lengths.take(1)) },
          System.nanoTime(),
          AndroidPadNotes(PRACTICE_NOTES),
          clock,
          quiet = RinMouth::awaitQuiet,
        )
      MissionType.CUPS ->
        CupShuffleMission(
          CupsRules.forLevel(difficulty).let { it.copy(swaps = it.swaps.take(1)) },
          System.nanoTime(),
          clock,
          quiet = RinMouth::awaitQuiet,
        )
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

  override fun tournament(game: TournamentGame, level: Int): Mission =
    when (game) {
      TournamentGame.PADS -> ColourPadsMission(TournamentLadder.pads(level), System.nanoTime(), AndroidPadNotes(PRACTICE_NOTES), clock)
      TournamentGame.CUPS -> CupShuffleMission(TournamentLadder.cups(level), System.nanoTime(), clock)
    }

  private companion object {
    val PRACTICE_NOTES: AudioAttributes =
      AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
  }
}
