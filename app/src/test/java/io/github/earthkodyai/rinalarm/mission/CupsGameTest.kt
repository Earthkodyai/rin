package io.github.earthkodyai.rinalarm.mission

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CupsGameTest {
  private val rules = CupsRules()

  /** Ticks the game along until the user may pick; returns the time then. */
  private fun CupsGame.untilPick(from: Long): Long {
    var now = from
    while (state.phase != CupsPhase.PICK) {
      now = state.nextAt ?: error("stuck in ${state.phase}")
      tick(now)
    }
    return now
  }

  /** Where the ball is, as the screen could follow it: the act's start and its swaps. */
  private fun CupsGame.ballNow(): Int {
    val act = state.act as CupsAct.Shuffle
    return CupsTimeline.afterSwaps(act.swaps, act.ball)
  }

  @Test
  fun startShowsTheBall_thenShufflesAsManyTimesAsTheStreakAsks() {
    val game = CupsGame(rules, Random(1))
    game.start(0)
    val show = game.state.act as CupsAct.Lift
    assertEquals(CupsPhase.SHOW, game.state.phase)
    assertEquals(listOf(show.ball), show.lift)
    assertEquals(listOf(show.ball), show.hands) // Rin lifts it herself
    assertEquals(rules.firstLeadMs, show.leadMs) // time for the camera to reach the table

    game.tick(game.state.nextAt!!)
    val shuffle = game.state.act as CupsAct.Shuffle
    assertEquals(CupsPhase.SHUFFLE, game.state.phase)
    assertEquals(rules.swaps[0], shuffle.swaps.size)
    assertEquals(show.ball, shuffle.ball)
    assertEquals(shuffle.endsAt, game.state.nextAt)
  }

  @Test
  fun threeRightInARow_pass_withSwapsRisingEachTime() {
    val game = CupsGame(rules, Random(2))
    var now = 0L
    game.start(now)
    repeat(3) { i ->
      now = game.untilPick(now)
      assertEquals(rules.swaps[i], (game.state.act as CupsAct.Shuffle).swaps.size)
      game.pick(game.ballNow(), now + 800)
      assertTrue(game.state.right == true)
      assertEquals(i + 1, game.state.streak)
      now += 800
    }
    assertEquals(CupsPhase.PASSED, game.state.phase)
    val last = game.state.act as CupsAct.Lift
    assertNull(last.holdMs) // stays up over the ball through the celebration
    assertEquals(emptyList<Int>(), last.hands) // her arms are free to clap
    assertNull(game.state.nextAt)
    assertEquals(0, game.state.mistakes)
    assertEquals("R1+800,R2+800,R3+800", game.trace())
  }

  @Test
  fun aWrongPick_showsBothCups_resetsTheCount_andTheNextShuffleStartsWhereTheBallIs() {
    val game = CupsGame(rules, Random(3))
    var now = game.untilPick(0L.also { game.start(it) })
    game.pick(game.ballNow(), now)
    now = game.untilPick(now)
    val ball = game.ballNow()
    val wrong = (ball + 1) % 3
    game.pick(wrong, now + 1_200)

    val reveal = game.state
    assertEquals(CupsPhase.REVEAL, reveal.phase)
    assertFalse(reveal.right!!)
    assertEquals(0, reveal.streak)
    assertEquals(1, reveal.mistakes)
    val lift = reveal.act as CupsAct.Lift
    assertEquals(listOf(wrong, ball).sorted(), lift.lift)
    assertEquals(listOf(ball), lift.hands) // she shows where it was; the user's cup rises by itself
    assertEquals(rules.scoldMs, lift.holdMs)

    game.tick(reveal.nextAt!!)
    val next = game.state.act as CupsAct.Shuffle
    assertEquals(ball, next.ball)
    assertEquals(rules.swaps[0], next.swaps.size)
    assertEquals("R1+0,W2:$wrong/$ball+1200", game.trace())
  }

  @Test
  fun tapsWhileTheCupsMove_areCountedAndIgnored() {
    val game = CupsGame(rules, Random(4))
    game.start(0)
    game.pick(0, 100)
    game.tick(game.state.nextAt!!)
    game.pick(1, game.state.nextAt!! - 1)
    assertEquals(2, game.state.earlyTaps)
    assertEquals(0, game.state.picks)
    assertEquals(CupsPhase.SHUFFLE, game.state.phase)
  }

  @Test
  fun aShuffle_neverRepeatsAPairBackToBack() {
    val game = CupsGame(rules.copy(swaps = listOf(30)), Random(5))
    game.start(0)
    game.tick(game.state.nextAt!!)
    val swaps = (game.state.act as CupsAct.Shuffle).swaps
    assertTrue(swaps.zipWithNext().none { (a, b) -> a == b })
    assertTrue(swaps.all { (p, q) -> p != q && p in 0..2 && q in 0..2 })
  }

  @Test
  fun theTimeline_movesCupsAsTheActSays() {
    val act = CupsAct.Shuffle(ball = 0, at = 1_000, swaps = listOf(0 to 1, 1 to 2), leadMs = 300, swapMs = 400, gapMs = 100, exitMs = 200)
    val start = CupsTimeline.frameAt(act, 1_000)
    assertEquals(listOf(0f, 1f, 2f), start.cups.map { it.x })
    // Halfway through the first swap: cup 0 passes in front, cup 1 behind.
    val mid = CupsTimeline.frameAt(act, 1_000 + 300 + 200)
    assertEquals(0.5f, mid.cups[0].x, 1e-4f)
    assertEquals(1f, mid.cups[0].z, 1e-4f)
    assertEquals(-1f, mid.cups[1].z, 1e-4f)
    val end = CupsTimeline.frameAt(act, act.endsAt)
    assertEquals(listOf(2f, 0f, 1f), end.cups.map { it.x })
    assertEquals(CupsTimeline.afterSwaps(act.swaps, 0), end.cups[end.ballCup].x.toInt())

    val lift = CupsAct.Lift(1, 0, listOf(1), listOf(1), leadMs = 300, upMs = 200, holdMs = 500, downMs = 200, exitMs = 200)
    assertEquals(0f, CupsTimeline.frameAt(lift, 300).cups[1].lift)
    assertEquals(1f, CupsTimeline.frameAt(lift, 600).cups[1].lift)
    assertEquals(0f, CupsTimeline.frameAt(lift, 600).cups[0].lift)
    assertEquals(0f, CupsTimeline.frameAt(lift, 1_200).cups[1].lift)
  }
}
