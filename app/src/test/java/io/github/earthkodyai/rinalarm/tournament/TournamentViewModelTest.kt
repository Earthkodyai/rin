package io.github.earthkodyai.rinalarm.tournament

import androidx.lifecycle.SavedStateHandle
import io.github.earthkodyai.rinalarm.character.CupsView
import io.github.earthkodyai.rinalarm.data.TournamentEntry
import io.github.earthkodyai.rinalarm.data.TournamentStore
import io.github.earthkodyai.rinalarm.mission.CupsMission
import io.github.earthkodyai.rinalarm.mission.CupsPhase
import io.github.earthkodyai.rinalarm.mission.CupsState
import io.github.earthkodyai.rinalarm.mission.Mission
import io.github.earthkodyai.rinalarm.mission.MissionFactory
import io.github.earthkodyai.rinalarm.mission.MissionProgress
import io.github.earthkodyai.rinalarm.mission.MissionState
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.Pad
import io.github.earthkodyai.rinalarm.mission.PadsMission
import io.github.earthkodyai.rinalarm.mission.PadsPhase
import io.github.earthkodyai.rinalarm.mission.PadsState
import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.mission.TournamentScore
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The tournament's run on its screen (G.5): the count, levels one after another, the miss, the answer, the result. */
class TournamentViewModelTest {
  @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

  private class FakePads : PadsMission {
    override val type = MissionType.PADS
    override val progress = MutableStateFlow(MissionProgress(0, 1))
    override val game = MutableStateFlow(PadsState())
    var begun = false
    var stopped = false
    val taps = mutableListOf<Pad>()

    override fun start() = Unit

    override fun stop() {
      stopped = true
    }

    override fun begin() {
      begun = true
    }

    override fun tap(pad: Pad) {
      taps += pad
    }

    override fun skipScold() = Unit
  }

  private class FakeCups : CupsMission {
    override val type = MissionType.CUPS
    override val progress = MutableStateFlow(MissionProgress(0, 1))
    override val game = MutableStateFlow(CupsState())
    var begun = false

    override fun start() = Unit

    override fun stop() = Unit

    override fun begin() {
      begun = true
    }

    override fun pick(slot: Int) = Unit

    override fun skipScold() = Unit
  }

  private class FakeStore : TournamentStore {
    val bests = MutableStateFlow<Map<TournamentGame, TournamentScore>>(emptyMap())
    override val entry: Flow<TournamentEntry> = MutableStateFlow(TournamentEntry())

    override suspend fun setEntry(entry: TournamentEntry) = Unit

    override fun best(game: TournamentGame): Flow<TournamentScore?> = bests.map { it[game] }

    override suspend fun record(score: TournamentScore): Boolean {
      if (!score.beats(bests.value[score.game])) return false
      bests.value = bests.value + (score.game to score)
      return true
    }
  }

  private val store = FakeStore()
  private val levels = mutableListOf<Mission>()
  private var nowMs = 0L

  private fun TestScope.screen(game: TournamentGame): TournamentViewModel {
    val factory =
      object : MissionFactory {
        override fun create(type: MissionType): Mission = error("not in a tournament")

        override fun tournament(game: TournamentGame, level: Int): Mission =
          (if (game == TournamentGame.PADS) FakePads() else FakeCups()).also { levels += it }
      }
    val vm = TournamentViewModel(factory, store, { nowMs }, SavedStateHandle(mapOf(TournamentViewModel.EXTRA_GAME to game.stored)))
    runCurrent()
    return vm
  }

  private fun TestScope.wait(ms: Long) {
    nowMs += ms
    advanceTimeBy(ms)
    runCurrent()
  }

  private val countdownMs = 3 * TournamentViewModel.COUNT_MS + TournamentViewModel.GO_MS

  @Test
  fun padsRun_countsDown_playsLevels_andEndsAtTheFirstMiss_withTheAnswerShown() =
    runTest(main.dispatcher) {
      val vm = screen(TournamentGame.PADS)
      assertEquals(TournamentPhase.COUNTDOWN, vm.uiState.value.phase)
      assertEquals(3, vm.uiState.value.count)
      wait(TournamentViewModel.COUNT_MS)
      assertEquals(2, vm.uiState.value.count)
      wait(countdownMs - TournamentViewModel.COUNT_MS)
      assertEquals(TournamentPhase.PLAYING, vm.uiState.value.phase)
      val go = vm.uiState.value.startedAt
      assertEquals(countdownMs, go)
      val one = levels.single() as FakePads
      assertTrue(one.begun)

      // Level 1 cleared 9 s after GO: a moment, LEVEL 2, then level 2 on its own.
      wait(9_000)
      one.progress.value = MissionProgress(1, 1, MissionState.PASSED)
      runCurrent()
      wait(TournamentViewModel.PASS_HOLD_MS)
      assertEquals(TournamentPhase.BANNER, vm.uiState.value.phase)
      assertEquals(2, vm.uiState.value.level)
      wait(TournamentViewModel.BANNER_MS)
      assertEquals(TournamentPhase.PLAYING, vm.uiState.value.phase)
      val two = levels[1] as FakePads
      assertTrue(two.begun)
      assertTrue(one.stopped)

      // A wrong tap on the 3rd pad: her hand shows the pad that was due, then the result.
      val sequence = listOf(Pad.RED, Pad.WHITE, Pad.GREEN, Pad.CYAN)
      two.game.value = PadsState(PadsPhase.SCOLD, sequence = sequence, entered = 2)
      runCurrent()
      val shown = vm.uiState.value
      assertEquals(TournamentPhase.REVEAL, shown.phase)
      assertEquals(Pad.GREEN, shown.pads?.hand)
      assertEquals(Pad.GREEN, shown.pads?.lit)
      assertTrue(two.stopped)
      wait(TournamentViewModel.REVEAL_MS)
      val result = vm.uiState.value
      assertEquals(TournamentPhase.RESULTS, result.phase)
      // Timed from GO to the level passed, not to the miss.
      assertEquals(TournamentScore(TournamentGame.PADS, 1, 9_000), result.score)
      assertTrue(result.newBest)
      assertEquals(result.score, store.bests.value[TournamentGame.PADS])
    }

  @Test
  fun taps_onlyReachTheGameWhilePlaying() =
    runTest(main.dispatcher) {
      val vm = screen(TournamentGame.PADS)
      vm.tapPad(Pad.RED) // during the count
      wait(countdownMs)
      vm.tapPad(Pad.BLUE)
      assertEquals(listOf(Pad.BLUE), (levels.single() as FakePads).taps)
    }

  @Test
  fun aRunWithNoLevelCleared_isNoBest_andAWorseOneIsNotKept() =
    runTest(main.dispatcher) {
      store.bests.value = mapOf(TournamentGame.PADS to TournamentScore(TournamentGame.PADS, 3, 40_000))
      val vm = screen(TournamentGame.PADS)
      wait(countdownMs)
      (levels.single() as FakePads).game.value = PadsState(PadsPhase.SCOLD, sequence = listOf(Pad.RED), entered = 0)
      runCurrent()
      wait(TournamentViewModel.REVEAL_MS)
      assertEquals(0, vm.uiState.value.score?.levels)
      assertFalse(vm.uiState.value.newBest)
      assertEquals(3, store.bests.value[TournamentGame.PADS]?.levels)
    }

  @Test
  fun playAgain_startsANewRunFromLevelOne() =
    runTest(main.dispatcher) {
      val vm = screen(TournamentGame.PADS)
      wait(countdownMs)
      (levels.single() as FakePads).game.value = PadsState(PadsPhase.SCOLD, sequence = listOf(Pad.RED), entered = 0)
      runCurrent()
      wait(TournamentViewModel.REVEAL_MS)
      vm.playAgain()
      runCurrent()
      assertEquals(TournamentPhase.COUNTDOWN, vm.uiState.value.phase)
      assertEquals(1, vm.uiState.value.level)
      assertNull(vm.uiState.value.score)
      wait(countdownMs)
      assertEquals(2, levels.size)
    }

  @Test
  fun cups_waitForHerTable_thenCount_andAWrongPickEndsTheRun() =
    runTest(main.dispatcher) {
      val vm = screen(TournamentGame.CUPS)
      assertEquals(TournamentPhase.TABLE, vm.uiState.value.phase)
      wait(1_000)
      assertEquals(TournamentPhase.TABLE, vm.uiState.value.phase)
      vm.onCupsView(CupsView.Shown(listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)))
      runCurrent()
      assertEquals(TournamentPhase.COUNTDOWN, vm.uiState.value.phase)
      wait(countdownMs)
      val one = levels.single() as FakeCups
      assertTrue(one.begun)
      one.game.value = CupsState(CupsPhase.REVEAL, right = false, picks = 1, mistakes = 1, cups = 5)
      runCurrent()
      assertEquals(TournamentPhase.REVEAL, vm.uiState.value.phase)
    }

  @Test
  fun cups_withNoTable_fallBackToTheBoard_andStillStart() =
    runTest(main.dispatcher) {
      val vm = screen(TournamentGame.CUPS)
      wait(TournamentViewModel.TABLE_WAIT_MS)
      assertTrue(vm.uiState.value.cups2d)
      assertEquals(TournamentPhase.COUNTDOWN, vm.uiState.value.phase)
    }

  @Test
  fun done_closesTheScreen_andStopsTheLevel() =
    runTest(main.dispatcher) {
      val vm = screen(TournamentGame.PADS)
      wait(countdownMs)
      vm.done()
      assertTrue(vm.uiState.value.finished)
      assertTrue((levels.single() as FakePads).stopped)
    }

  @Test
  fun timeIsShownAsMinutesSecondsTenths() {
    assertEquals("0:00.0", formatTime(0))
    assertEquals("1:23.4", formatTime(83_456))
    assertEquals("12:05.0", formatTime(725_000))
  }
}
