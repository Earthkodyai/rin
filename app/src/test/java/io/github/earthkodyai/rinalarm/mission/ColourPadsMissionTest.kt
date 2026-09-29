package io.github.earthkodyai.rinalarm.mission

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColourPadsMissionTest {
  private val notes =
    object : PadNotes {
      val played = mutableListOf<Pad>()
      var released = false

      override fun play(pad: Pad) {
        played += pad
      }

      override fun release() {
        released = true
      }
    }

  private fun TestScope.mission() =
    ColourPadsMission(PadsRules(), seed = 5, notes = notes, clock = { testScheduler.currentTime }, context = StandardTestDispatcher(testScheduler))

  /** Lets the demo play out on the timer until it is the user's turn. */
  private fun TestScope.untilYourTurn(m: ColourPadsMission) {
    while (m.game.value.phase != PadsPhase.INPUT) {
      testScheduler.advanceTimeBy(50)
      runCurrent()
    }
  }

  @Test
  fun playsTheDemoOnItsOwnTimer_withANotePerPress() = runTest {
    val m = mission()
    m.start()
    m.begin()
    untilYourTurn(m)

    assertEquals(m.game.value.sequence, notes.played)
    m.stop() // or the game loops forever in virtual time (slow, scold, demo…) and runTest never idles
  }

  @Test
  fun aWholeGame_passes_andEachRightTapIsActivity_withTheNumbersInTheSummary() = runTest {
    val m = mission()
    m.start()
    m.begin()
    assertEquals(1, m.progress.value.activity)
    repeat(3) { round ->
      untilYourTurn(m)
      assertEquals(round, m.progress.value.done)
      m.game.value.sequence.forEach { m.tap(it) }
      runCurrent()
    }

    assertEquals(MissionState.PASSED, m.progress.value.state)
    assertEquals(3, m.progress.value.done)
    assertEquals(1 + 3 + 4 + 5, m.progress.value.activity)
    assertTrue(m.summary(), m.summary().startsWith("game=colour_pads rounds=3/3 mistakes=0 timeouts=0 rightTaps=12 seed=5"))
  }

  @Test
  fun missesAreNotActivity_andTheScoldEndsInANewDemo() = runTest {
    val m = mission()
    m.start()
    m.begin()
    untilYourTurn(m)
    val first = m.game.value.sequence
    m.tap(Pad.entries.first { it != first[0] })
    assertEquals(PadsPhase.SCOLD, m.game.value.phase)
    assertEquals(1, m.progress.value.activity)

    testScheduler.advanceTimeBy(PadsRules().scoldMs + 1)
    runCurrent()
    assertEquals(PadsPhase.DEMO, m.game.value.phase)
    assertTrue(m.summary().contains("mistakes=1"))
    m.stop()
  }

  @Test
  fun stop_releasesTheNotes_andStopsTheTimer() = runTest {
    val m = mission()
    m.start()
    m.begin()
    m.stop()
    val phase = m.game.value
    advanceUntilIdle()
    assertEquals(phase, m.game.value)
    assertTrue(notes.released)
    // Taps after stop do nothing.
    m.tap(Pad.RED)
    assertEquals(phase, m.game.value)
  }
}
