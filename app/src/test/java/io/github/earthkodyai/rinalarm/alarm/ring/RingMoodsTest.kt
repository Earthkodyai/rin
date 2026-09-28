package io.github.earthkodyai.rinalarm.alarm.ring

import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.character.Mood
import org.junit.Assert.assertEquals
import org.junit.Test

class RingMoodsTest {
  @Test
  fun phase_followsProgressAndTheIdleTimeout() {
    assertEquals(RingPhase.WAKING, RingMoods.phase(passed = false, nowMs = 5_000, lastProgressMs = null))
    assertEquals(RingPhase.WORKING, RingMoods.phase(passed = false, nowMs = 5_000, lastProgressMs = 4_000))
    assertEquals(RingPhase.STALLED, RingMoods.phase(passed = false, nowMs = 40_000, lastProgressMs = 4_000))
    assertEquals(RingPhase.PASSED, RingMoods.phase(passed = true, nowMs = 40_000, lastProgressMs = 4_000))
  }

  @Test
  fun passing_makesHerProud_andClap() {
    assertEquals(Mood.PROUD, RingMoods.mood(RingPhase.PASSED))
    assertEquals(Gesture.CLAP, RingMoods.cue(RingPhase.PASSED))
  }

  @Test
  fun stalling_makesHerPout() {
    assertEquals(Mood.POUTY, RingMoods.mood(RingPhase.STALLED))
    assertEquals(Gesture.POUT, RingMoods.cue(RingPhase.STALLED))
  }
}
