package io.github.earthkodyai.rinalarm.mission

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CupShuffleMissionTest {
  private fun TestScope.mission(quiet: suspend () -> Unit = {}) =
    CupShuffleMission(
      CupsRules(),
      seed = 7,
      clock = { testScheduler.currentTime },
      context = StandardTestDispatcher(testScheduler),
      quiet = quiet,
    )

  private fun TestScope.untilPick(m: CupShuffleMission) {
    while (m.game.value.phase != CupsPhase.PICK) {
      testScheduler.advanceTimeBy(50)
      runCurrent()
    }
  }

  private fun CupShuffleMission.ball(): Int {
    val act = game.value.act as CupsAct.Shuffle
    return CupsTimeline.afterSwaps(act.swaps, act.ball)
  }

  @Test
  fun aWholeGame_passes_rightPicksAreActivity_andTheSummaryHasTheNumbers() = runTest {
    val m = mission()
    m.start()
    m.begin()
    assertEquals(1, m.progress.value.activity)
    repeat(3) { i ->
      untilPick(m)
      assertEquals(i, m.progress.value.done)
      m.pick(m.ball())
      runCurrent()
      assertEquals(i + 2, m.progress.value.activity)
    }
    assertEquals(MissionState.PASSED, m.progress.value.state)
    val summary = m.summary()
    assertTrue(summary, summary.startsWith("game=cup_shuffle streak=3/3 picks=3 mistakes=0 earlyTaps=0 seed=7 picks_trace=R1+"))
    m.stop()
  }

  @Test
  fun aWrongPick_takesTheCountBack_butIsActivity() = runTest {
    val m = mission()
    m.start()
    m.begin()
    untilPick(m)
    m.pick(m.ball())
    untilPick(m)
    assertEquals(1, m.progress.value.done)
    val activity = m.progress.value.activity
    m.pick((m.ball() + 1) % 3)
    runCurrent()
    assertEquals(0, m.progress.value.done)
    assertEquals(activity + 1, m.progress.value.activity)
    m.stop()
  }

  @Test
  fun afterAWrongPick_theNextShuffleWaitsForRinsScoldToEnd() = runTest {
    val lineOver = CompletableDeferred<Unit>()
    val m = mission(quiet = { lineOver.await() })
    m.start()
    m.begin()
    untilPick(m)
    m.pick((m.ball() + 1) % 3)
    runCurrent()
    assertEquals(CupsPhase.REVEAL, m.game.value.phase)

    testScheduler.advanceTimeBy(CupsRules().scoldMs + 5_000)
    runCurrent()
    assertEquals(CupsPhase.REVEAL, m.game.value.phase)

    lineOver.complete(Unit)
    runCurrent()
    assertEquals(CupsPhase.SHUFFLE, m.game.value.phase)
    m.stop()
  }

  @Test
  fun nothingMoves_beforeStartOrAfterStop() = runTest {
    val m = mission()
    m.begin() // not started: ignored
    assertEquals(CupsPhase.READY, m.game.value.phase)
    m.start()
    m.stop()
    m.begin()
    assertEquals(CupsPhase.READY, m.game.value.phase)
  }
}
