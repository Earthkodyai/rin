package io.github.earthkodyai.rinalarm.dialogue

import io.github.earthkodyai.rinalarm.character.Mood
import io.github.earthkodyai.rinalarm.mission.Hush
import io.github.earthkodyai.rinalarm.testing.FakeLineVoice
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RinSpeakerTest {
  private val hi = Line("app.morning.01", "app.morning", "Oh, hi! Good morning.", Mood.CHEERFUL, null)
  private val bye = Line("emergency.01", "emergency", "Alarm off.", Mood.WORRIED, null)

  @Test
  fun withNoClip_theLineIsASubtitle_forItsReadingTime_andTheToneIsLeftAlone() = runTest {
    val speaker = RinSpeaker(FakeLineVoice(), backgroundScope)
    val job = speaker.say(hi)
    runCurrent()
    assertEquals(hi, speaker.line.value)
    assertEquals(Hush.NONE, speaker.hush.value)
    advanceTimeBy(RinSpeaker.readingMs(hi) - 1)
    assertEquals(hi, speaker.line.value)
    advanceTimeBy(2)
    assertNull(speaker.line.value)
    assertTrue(job.isCompleted)
  }

  @Test
  fun aClip_ducksTheToneWhileItPlays_thenTheSubtitleLingersABeat() = runTest {
    val voice = FakeLineVoice(clips = setOf(hi.id), clipMs = 1_500)
    val speaker = RinSpeaker(voice, backgroundScope)
    speaker.say(hi)
    runCurrent()
    assertEquals(listOf(hi.id), voice.played)
    assertEquals(Hush.QUIET, speaker.hush.value)
    advanceTimeBy(1_501)
    assertEquals(Hush.NONE, speaker.hush.value)
    assertEquals(hi, speaker.line.value)
    advanceTimeBy(RinSpeaker.TAIL_MS)
    assertNull(speaker.line.value)
  }

  @Test
  fun aNewLine_cutsTheOldOne_orWaitsForIt() = runTest {
    val voice = FakeLineVoice(clips = setOf(hi.id), clipMs = 2_000)
    val speaker = RinSpeaker(voice, backgroundScope)
    val first = speaker.say(hi)
    runCurrent()
    val second = speaker.say(bye) // cuts: no clip, so the tone comes straight back
    runCurrent()
    assertTrue(first.isCancelled)
    assertEquals(bye, speaker.line.value)
    assertEquals(Hush.NONE, speaker.hush.value)

    val queued = speaker.say(hi, cut = false)
    runCurrent()
    assertEquals(bye, speaker.line.value)
    second.join()
    runCurrent()
    assertEquals(hi, speaker.line.value)
    assertEquals(Hush.QUIET, speaker.hush.value)
    queued.join()
    assertNull(speaker.line.value)
  }

  @Test
  fun stop_endsEveryLine_queuedOnesToo() = runTest {
    val speaker = RinSpeaker(FakeLineVoice(), backgroundScope)
    val first = speaker.say(hi)
    val queued = speaker.say(bye, cut = false)
    runCurrent()
    speaker.stop()
    runCurrent()
    assertTrue(first.isCancelled && queued.isCancelled)
    assertNull(speaker.line.value)
    advanceTimeBy(10_000)
    assertNull(speaker.line.value)
    assertFalse(queued.isActive)
  }
}
