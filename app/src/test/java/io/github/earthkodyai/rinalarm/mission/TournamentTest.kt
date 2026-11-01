package io.github.earthkodyai.rinalarm.mission

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tournament's levels and runs (G.5). */
class TournamentTest {
  @Test
  fun levelOne_isHardsFirstRound_onTheFiveCupTable() {
    val hardPads = PadsRules.forLevel(Difficulty.HARD)
    assertEquals(hardPads.copy(lengths = listOf(5)), TournamentLadder.pads(1))
    val hardCups = CupsRules.forLevel(Difficulty.HARD)
    val cups = TournamentLadder.cups(1)
    assertEquals(hardCups.copy(swaps = listOf(6), cups = 5), cups)
    assertEquals(1, cups.streak)
  }

  @Test
  fun theLevelAfterTheWarmUp_isNightmaresFirstRound() {
    val first = TournamentLadder.WARM_UP_LEVELS + 1
    assertEquals(6, first)
    val nightmarePads = PadsRules.forLevel(Difficulty.NIGHTMARE)
    assertEquals(nightmarePads.copy(lengths = listOf(7)), TournamentLadder.pads(first))
    val nightmareCups = CupsRules.forLevel(Difficulty.NIGHTMARE)
    assertEquals(nightmareCups.copy(swaps = listOf(8)), TournamentLadder.cups(first))
  }

  @Test
  fun theCupsTable_neverChangesSizeDuringARun() {
    assertTrue((1..30).all { TournamentLadder.cups(it).cups == 5 })
  }

  @Test
  fun warmUpLevels_addOne_atHardsSpeed() {
    for (level in 1..TournamentLadder.WARM_UP_LEVELS) {
      val pads = TournamentLadder.pads(level)
      assertEquals(5 + level - 1, pads.lengths.single())
      assertEquals(300L, pads.moveMs)
      assertEquals(2_000L, pads.tapTimeoutMs)
      val cups = TournamentLadder.cups(level)
      assertEquals(6 + level - 1, cups.swaps.single())
      assertEquals(320L, cups.swapMs)
    }
  }

  @Test
  fun eachNightmareLevel_addsOne_andEveryThirdIsSevenPercentFaster() {
    val warmUp = TournamentLadder.WARM_UP_LEVELS
    for (round in 1..30) {
      assertEquals(7 + round - 1, TournamentLadder.pads(warmUp + round).lengths.single())
      assertEquals(8 + round - 1, TournamentLadder.cups(warmUp + round).swaps.single())
    }
    assertEquals(listOf(1.0, 1.0, 1.0, 0.93, 0.93, 0.93), (1..6).map(TournamentLadder::pace))
    assertEquals(220L, TournamentLadder.pads(warmUp + 3).moveMs)
    assertEquals(205L, TournamentLadder.pads(warmUp + 4).moveMs)
    assertEquals(214L, TournamentLadder.cups(warmUp + 4).swapMs)
    // The time to answer a pad never shrinks.
    assertTrue((1..30).all { TournamentLadder.pads(warmUp + it).tapTimeoutMs == 1_200L })
  }

  @Test
  fun speed_stopsAtFloors_theScreenCanStillShow() {
    val pads = TournamentLadder.pads(100)
    assertEquals(TournamentLadder.PADS_MOVE_FLOOR_MS, pads.moveMs)
    assertEquals(TournamentLadder.PADS_PRESS_FLOOR_MS, pads.pressMs)
    val cups = TournamentLadder.cups(100)
    assertEquals(TournamentLadder.CUPS_SWAP_FLOOR_MS, cups.swapMs)
    assertEquals(TournamentLadder.CUPS_GAP_FLOOR_MS, cups.gapMs)
    // Never faster from one level to the next.
    for (level in 1..99) {
      assertTrue(TournamentLadder.pads(level + 1).moveMs <= TournamentLadder.pads(level).moveMs)
      assertTrue(TournamentLadder.cups(level + 1).swapMs <= TournamentLadder.cups(level).swapMs)
    }
  }

  @Test
  fun padsLevels_drawShapedSequences_evenLongOnes_quickly() {
    val started = System.nanoTime()
    for (level in listOf(1, 5, 10, 14)) {
      val rules = TournamentLadder.pads(level)
      repeat(30) { seed ->
        val g = PadsGame(rules, Random(seed * 101L + level))
        g.start(0)
        assertTrue("level $level: ${g.state.sequence}", PadPaths.keepsTheRules(g.state.sequence, PadGrid.THREE))
      }
    }
    // Far past anyone's reach the rules still hold (the band opens above for long rounds).
    for (length in listOf(30, 40)) {
      repeat(10) { seed ->
        val seq = PadPaths.draw(length, PadGrid.THREE, Random(seed.toLong()))
        assertEquals(length, seq.size)
        assertTrue("$length: $seq", PadPaths.keepsTheRules(seq, PadGrid.THREE))
      }
    }
    val ms = (System.nanoTime() - started) / 1_000_000
    assertTrue("$ms ms", ms < 2_000)
  }

  @Test
  fun cupsLevels_shuffleFairly_withEvenOdds() {
    for (level in listOf(1, 5, 10, 20)) {
      val rules = TournamentLadder.cups(level)
      val ends = IntArray(5)
      repeat(400) { seed ->
        val g = CupsGame(rules, Random(seed * 37L + level))
        g.start(0)
        while (g.state.phase != CupsPhase.PICK) g.tick(checkNotNull(g.state.nextAt))
        val shuffle = g.state.act as CupsAct.Shuffle
        assertEquals(rules.swaps.single(), shuffle.swaps.size)
        assertTrue("level $level seed $seed", CupsGame.fair(shuffle.swaps, shuffle.ball, 5))
        ends[CupsTimeline.afterSwaps(shuffle.swaps, shuffle.ball)]++
      }
      assertTrue("level $level: ${ends.toList()}", ends.all { it in 50..110 })
    }
  }

  @Test
  fun aRun_endsAtTheFirstMiss_andIsTimedFromGoToTheLastLevelPassed() {
    val run = TournamentRun(TournamentGame.PADS)
    run.pass(500) // before GO: nothing
    assertEquals(1, run.level)
    run.start(1_000)
    run.pass(8_000)
    run.pass(19_500)
    assertEquals(3, run.level)
    run.miss() // at, say, 25 s: the failed level is not timed
    assertTrue(run.over)
    run.pass(30_000) // a late tap after the end
    assertEquals(TournamentScore(TournamentGame.PADS, levels = 2, timeMs = 18_500), run.score())
  }

  @Test
  fun moreLevelsWin_thenTheFasterTime() {
    val a = TournamentScore(TournamentGame.CUPS, 5, 40_000)
    val b = TournamentScore(TournamentGame.CUPS, 4, 10_000)
    val c = TournamentScore(TournamentGame.CUPS, 5, 30_000)
    assertEquals(listOf(c, a, b), listOf(a, b, c).sorted())
    assertTrue(c.beats(a))
    assertFalse(b.beats(a))
    assertTrue(b.beats(null))
    assertFalse(a.beats(a))
  }
}
