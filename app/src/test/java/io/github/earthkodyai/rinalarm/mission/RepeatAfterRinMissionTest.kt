package io.github.earthkodyai.rinalarm.mission

import io.github.earthkodyai.rinalarm.character.Speaking
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatAfterRinMissionTest {
  private val pool = RepeatSentences.parse(File("src/main/assets/repeat/sentences.json").readText())

  /** Says each line in [lineMs]; [hasClips] false plays nothing, like a release build. */
  private class FakeVoice(private val hasClips: Boolean = true) : RinVoice {
    override val speaking = MutableStateFlow<Speaking?>(null)
    val said = mutableListOf<String>()
    var released = false

    override suspend fun say(sentence: Sentence): Boolean {
      said += sentence.id
      if (!hasClips) return false
      kotlinx.coroutines.delay(LINE_MS)
      return true
    }

    override fun release() {
      released = true
    }
  }

  /** Answers each listen with the next of [answers] after [SPEAK_MS]; fails on prepare when [broken]. */
  private class FakeListener(private val broken: Boolean = false) : SpeechListener {
    override val level: StateFlow<Float> = MutableStateFlow(0f)
    val grammars = mutableListOf<List<String>>()
    var answer: (List<String>) -> Heard = { Heard.NOTHING }
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun prepare() {
      if (broken) error("no model")
    }

    override suspend fun listen(grammar: List<String>, maxMs: Long, enough: (Heard) -> Boolean): Heard {
      grammars += grammar
      gate?.await()
      kotlinx.coroutines.delay(SPEAK_MS)
      return answer(grammar)
    }

    override fun release() = Unit
  }

  private fun TestScope.mission(voice: RinVoice = FakeVoice(), listener: SpeechListener = FakeListener()) =
    RepeatAfterRinMission(RepeatRules(), pool, seed = 7, voice, listener, clock = { testScheduler.currentTime }, context = StandardTestDispatcher(testScheduler))

  @Test
  fun aGameByVoice_passes_withTheToneQuietForHerAndSilentForTheMic() = runTest {
    val voice = FakeVoice()
    val listener = FakeListener()
    val m = mission(voice, listener)
    listener.answer = { Heard(m.game.value.sentence!!.words.map { HeardWord(it, 1f) }) }
    m.start()
    m.begin()
    runCurrent()
    assertEquals(Hush.QUIET, m.hush.value) // Rin speaking
    advanceTimeBy(LINE_MS + 1)
    runCurrent()
    assertEquals(Hush.SILENT, m.hush.value) // the mic is open
    assertEquals(RepeatPhase.LISTENING, m.game.value.phase)
    testScheduler.advanceUntilIdle()

    assertEquals(MissionState.PASSED, m.progress.value.state)
    assertEquals(3, voice.said.size)
    assertEquals(3, listener.grammars.size)
    assertTrue(listener.grammars.all { "[unk]" in it })
    assertEquals(Hush.NONE, m.hush.value)
    // Begin and three heard tries.
    assertEquals(4, m.progress.value.activity)
    val summary = m.summary()
    assertTrue(summary, summary.startsWith("game=repeat sentences=") && "voice=3 tap=0 listens=3" in summary && "seed=7" in summary)
    m.stop()
    assertTrue(voice.released)
  }

  @Test
  fun noClip_givesTimeToRead_beforeTheMicOpens() = runTest {
    val listener = FakeListener()
    val m = mission(FakeVoice(hasClips = false), listener)
    m.start()
    m.begin()
    runCurrent()
    val reading = RepeatRules().readingMs(m.game.value.sentence!!)
    advanceTimeBy(reading - 1)
    runCurrent()
    assertEquals(RepeatPhase.SPEAKING, m.game.value.phase)
    advanceTimeBy(2)
    runCurrent()
    assertEquals(RepeatPhase.LISTENING, m.game.value.phase)
    m.stop()
  }

  @Test
  fun aMissingModel_turnsTheGameIntoChips_andStillPasses() = runTest {
    val m = mission(listener = FakeListener(broken = true))
    m.start()
    m.begin()
    repeat(3) {
      while (m.game.value.phase != RepeatPhase.TAPPING) {
        testScheduler.advanceTimeBy(100)
        runCurrent()
      }
      val s = m.game.value
      s.sentence!!.words.forEach { w -> m.tapWord(m.game.value.chips.indexOfFirst { !it.used && Sentence.normalize(it.text) == w }) }
      runCurrent()
      assertEquals(Feedback.RIGHT, m.game.value.feedback)
    }
    testScheduler.advanceUntilIdle()
    assertEquals(MissionState.PASSED, m.progress.value.state)
    assertTrue(m.summary(), "voice=0 tap=3" in m.summary() && "mic=failed:IllegalStateException" in m.summary())
    assertEquals(Hush.NONE, m.hush.value)
  }

  @Test
  fun hearAgain_cutsTheTryShort_andRinSaysItAgain() = runTest {
    val voice = FakeVoice()
    val listener = FakeListener().apply { gate = CompletableDeferred() } // the user never answers
    val m = mission(voice, listener)
    m.start()
    m.begin()
    advanceTimeBy(LINE_MS + 1)
    runCurrent()
    assertEquals(RepeatPhase.LISTENING, m.game.value.phase)
    m.hearAgain()
    runCurrent()
    assertEquals(RepeatPhase.SPEAKING, m.game.value.phase)
    assertEquals(Hush.QUIET, m.hush.value)
    assertEquals(2, voice.said.size)
    assertEquals(0, m.game.value.tries)
    m.stop()
  }

  private companion object {
    const val LINE_MS = 2_000L
    const val SPEAK_MS = 1_500L
  }
}
