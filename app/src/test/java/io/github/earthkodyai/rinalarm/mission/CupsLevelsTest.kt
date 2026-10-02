package io.github.earthkodyai.rinalarm.mission

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cups at each level (G.3): every level's rules, 4 and 5 cups, and Easy unchanged. */
class CupsLevelsTest {
  /** Ticks the game along until the user may pick; returns the time then. */
  private fun CupsGame.untilPick(from: Long): Long {
    var now = from
    while (state.phase != CupsPhase.PICK) {
      now = state.nextAt ?: error("stuck in ${state.phase}")
      tick(now)
    }
    return now
  }

  private fun CupsGame.ballNow(): Int {
    val act = state.act as CupsAct.Shuffle
    return CupsTimeline.afterSwaps(act.swaps, act.ball)
  }

  @Test
  fun easy_isTheFrozenGame() {
    assertEquals(CupsRules(), CupsRules.forLevel(Difficulty.EASY))
    with(CupsRules()) {
      assertEquals(listOf(4, 5, 6), swaps)
      assertEquals(450L to 150L, swapMs to gapMs)
      assertEquals(3, cups)
    }
    assertEquals(listOf(0 to 1, 1 to 2, 0 to 2), CupsRules.pairs(3))
  }

  @Test
  fun easy_drawsTheSameShufflesAsBeforeG3_soLoggedSeedsReplay() {
    // The draw as it was before G.3: the ball from nextInt(3), then pairs from the fixed list of three.
    fun before(seed: Long): Pair<Int, List<Pair<Int, Int>>> {
      val r = Random(seed)
      val ball = r.nextInt(3)
      val pairs = listOf(0 to 1, 1 to 2, 0 to 2)
      val swaps = mutableListOf<Pair<Int, Int>>()
      while (swaps.size < 4) pairs[r.nextInt(3)].let { if (it != swaps.lastOrNull()) swaps += it }
      return ball to swaps
    }
    for (seed in listOf(1L, 7L, 123_456_789L)) {
      val g = CupsGame(CupsRules(), Random(seed))
      g.start(0)
      val ball = (g.state.act as CupsAct.Lift).ball
      g.tick(g.state.nextAt!!)
      assertEquals(before(seed), ball to (g.state.act as CupsAct.Shuffle).swaps)
    }
  }

  @Test
  fun eachLevel_isHarderThanTheOneBelow() {
    val rules = Difficulty.entries.map(CupsRules::forLevel)
    rules.zipWithNext().forEach { (easier, harder) ->
      assertTrue(harder.swaps.zip(easier.swaps).all { (h, e) -> h > e })
      assertTrue(harder.swapMs < easier.swapMs && harder.gapMs < easier.gapMs)
      assertTrue(harder.cups >= easier.cups)
    }
    assertEquals(listOf(3, 3, 4, 5), rules.map { it.cups })
    assertEquals(listOf(8, 10, 12), rules.last().swaps)
  }

  @Test
  fun everyLevel_winsWithThreeRightPicks_onItsOwnTable() {
    for (level in Difficulty.entries) {
      val rules = CupsRules.forLevel(level)
      val g = CupsGame(rules, Random(level.ordinal + 11L))
      assertEquals(rules.cups, g.state.cups)
      var now = 0L
      g.start(now)
      assertEquals(rules.cups, g.state.act!!.cups)
      val counts = mutableListOf<Int>()
      while (g.state.phase != CupsPhase.PASSED) {
        now = g.untilPick(now)
        val shuffle = g.state.act as CupsAct.Shuffle
        assertEquals(rules.cups, shuffle.cups)
        assertEquals(rules.swapMs, shuffle.swapMs)
        assertTrue(shuffle.swaps.all { it in CupsRules.pairs(rules.cups) })
        assertTrue(shuffle.swaps.zipWithNext().none { (a, b) -> a == b })
        counts += shuffle.swaps.size
        g.pick(g.ballNow(), ++now)
      }
      assertEquals("$level", rules.swaps, counts)
    }
  }

  @Test
  fun fiveCups_theBallStartsAndEndsAnywhere_andAWrongPickShowsWhereItWas() {
    val rules = CupsRules.forLevel(Difficulty.NIGHTMARE)
    val starts = mutableSetOf<Int>()
    val ends = mutableSetOf<Int>()
    for (seed in 0L until 60) {
      val g = CupsGame(rules, Random(seed))
      g.start(0)
      starts += (g.state.act as CupsAct.Lift).ball
      val now = g.untilPick(0)
      val ball = g.ballNow()
      ends += ball
      val wrong = (ball + 1 + seed.toInt() % 4) % 5
      g.pick(wrong, now + 1)
      val reveal = g.state.act as CupsAct.Lift
      assertEquals(false, g.state.right)
      assertEquals(listOf(wrong, ball).sorted(), reveal.lift)
      assertEquals(listOf(ball), reveal.hands)
    }
    assertEquals((0..4).toSet(), starts)
    assertEquals((0..4).toSet(), ends)
  }

  @Test
  fun aSlotOffTheTable_isNoPick() {
    val g = CupsGame(CupsRules(), Random(4))
    g.start(0)
    val now = g.untilPick(0)
    val before = g.state
    g.pick(3, now + 1)
    g.pick(-1, now + 2)
    assertEquals(before, g.state)
  }

  @Test
  fun pairs_spanHerMiddle_andUseEverySlot() {
    assertEquals(listOf(0 to 2, 0 to 3, 1 to 2, 1 to 3), CupsRules.pairs(4))
    assertEquals(listOf(0 to 3, 0 to 4, 1 to 2, 1 to 3, 1 to 4, 2 to 3), CupsRules.pairs(5))
    for (n in CupsRules.CUPS_RANGE) {
      val middle = (n - 1) / 2.0
      val pairs = CupsRules.pairs(n)
      assertEquals((0 until n).toSet(), pairs.flatMap { listOf(it.first, it.second) }.toSet())
      assertTrue(pairs.all { (p, q) -> p < q && p <= middle && q >= middle })
    }
  }

  @Test
  fun theTimeline_movesFiveCups_andKeepsTheBallWithItsCup() {
    val swaps = listOf(0 to 4, 1 to 3, 2 to 3, 0 to 3)
    val act = CupsAct.Shuffle(ball = 4, at = 0, swaps = swaps, leadMs = 300, swapMs = 230, gapMs = 60, exitMs = 250, cups = 5)
    val start = CupsTimeline.frameAt(act, 0)
    assertEquals(listOf(0f, 1f, 2f, 3f, 4f), start.cups.map { it.x })
    val end = CupsTimeline.frameAt(act, act.endsAt)
    assertEquals(setOf(0f, 1f, 2f, 3f, 4f), end.cups.map { it.x }.toSet())
    assertEquals(CupsTimeline.afterSwaps(swaps, 4).toFloat(), end.cups[end.ballCup].x)
    // Mid-swap the two moving cups pass, one in front and one behind.
    val mid = CupsTimeline.frameAt(act, 300 + 115)
    assertFalse(mid.cups.all { it.z == 0f })
  }

  @Test
  fun aboveEasy_everyShuffleKeepsTheBallsCupBusy_andTouchesEveryCup() {
    for (level in listOf(Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE)) {
      val rules = CupsRules.forLevel(level)
      assertTrue(rules.balanced)
      for (seed in 0L until 150) {
        val g = CupsGame(rules, Random(seed))
        g.start(0)
        g.untilPick(0)
        val shuffle = g.state.act as CupsAct.Shuffle
        var at = shuffle.ball
        var moves = 0
        for ((p, q) in shuffle.swaps) {
          if (at == p || at == q) moves++
          at = if (at == p) q else if (at == q) p else at
        }
        assertTrue("$level seed $seed: $moves of ${shuffle.swaps.size}", 2 * moves >= shuffle.swaps.size)
        val touched = shuffle.swaps.flatMap { listOf(it.first, it.second) }.toSet()
        if (shuffle.swaps.size >= rules.cups) assertEquals((0 until rules.cups).toSet(), touched)
      }
    }
    assertFalse(CupsRules.forLevel(Difficulty.EASY).balanced)
  }

  @Test
  fun aBalancedShuffle_stillEndsAnywhere_withEvenOdds() {
    // From every start, where the ball ends after Nightmare's first shuffle: a blind guess should do no better than 1 in 5.
    val rules = CupsRules.forLevel(Difficulty.NIGHTMARE)
    val ends = Array(5) { IntArray(5) }
    for (seed in 0L until 3_000) {
      val g = CupsGame(rules, Random(seed))
      g.start(0)
      g.untilPick(0)
      val shuffle = g.state.act as CupsAct.Shuffle
      ends[shuffle.ball][CupsTimeline.afterSwaps(shuffle.swaps, shuffle.ball)]++
    }
    for (row in ends) {
      val best = row.max().toDouble() / row.sum()
      assertTrue("best blind guess $best", best < 0.27)
    }
  }
}
