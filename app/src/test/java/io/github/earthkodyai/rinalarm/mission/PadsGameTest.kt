package io.github.earthkodyai.rinalarm.mission

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PadsGameTest {
  private val rules = PadsRules()
  private var now = 10_000L

  private fun game(seed: Long = 7) = PadsGame(rules, Random(seed))

  /** Ticks the game at each moment it asks for until [phase], collecting the pads it lights on the way. */
  private fun PadsGame.runUntil(phase: PadsPhase, lit: MutableList<Pad> = mutableListOf()): List<Pad> {
    var guard = 0
    while (state.phase != phase) {
      now = checkNotNull(state.nextAt) { "stuck in ${state.phase}" }
      val before = state.flash
      tick(now)
      if (state.flash != before) lit += checkNotNull(state.lit)
      check(guard++ < 200)
    }
    return lit
  }

  private fun PadsGame.repeatSequence() {
    for (pad in state.sequence) {
      now += 500
      tap(pad, now)
    }
  }

  @Test
  fun waitsForLetsPlay_andIgnoresTapsAndTicksUntilThen() {
    val g = game()
    assertEquals(PadsPhase.READY, g.state.phase)
    g.tick(now + 60_000)
    g.tap(Pad.RED, now)
    assertEquals(PadsPhase.READY, g.state.phase)
    assertNull(g.state.nextAt)
  }

  @Test
  fun theDemo_pressesEachPadInOrder_thenHandsOver() {
    val g = game()
    g.start(now)
    val sequence = g.state.sequence
    assertEquals(3, sequence.size)
    assertEquals(sequence.first(), g.state.hand)

    val lit = g.runUntil(PadsPhase.INPUT)

    assertEquals(sequence, lit)
    assertNull(g.state.hand)
    assertNull(g.state.lit)
    // Her turn ends with her last press; her hand's exit is added to the user's first tap.
    val demoMs = 3 * (rules.moveMs + rules.pressMs)
    assertEquals(10_000L + demoMs, now)
    assertEquals(now + rules.exitMs + rules.tapTimeoutMs, g.state.nextAt)
  }

  @Test
  fun aTapAsHerHandLeaves_counts() {
    // dev-3: the user answered while her hand was still leaving; that tap was dropped and the next right one read as
    // wrong.
    val g = game()
    g.start(now)
    g.runUntil(PadsPhase.INPUT)
    g.tap(g.state.sequence[0], now + 50)
    assertEquals(1, g.state.entered)
    assertEquals(0, g.state.mistakes)
  }

  @Test
  fun tapsDuringTheDemo_areIgnored_andCounted() {
    val g = game()
    g.start(now)
    val before = g.state
    g.tap(g.state.sequence.first(), now + 100)
    assertEquals(before.copy(earlyTaps = 1), g.state)
  }

  @Test
  fun threeRounds_ofThreeFourFive_pass() {
    val g = game()
    g.start(now)
    for (length in listOf(3, 4, 5)) {
      assertEquals(length, g.state.sequence.size)
      g.runUntil(PadsPhase.INPUT)
      g.repeatSequence()
    }
    assertEquals(PadsPhase.PASSED, g.state.phase)
    assertEquals(0, g.state.mistakes)
    assertEquals(0, g.state.timeouts)
    assertNull(g.state.nextAt)
  }

  @Test
  fun aWrongTap_cutsToTheScold_thenTheSameRoundWithANewSequence() {
    val g = game()
    g.start(now)
    g.runUntil(PadsPhase.INPUT)
    g.repeatSequence() // round 1 done
    g.runUntil(PadsPhase.INPUT)
    val first = g.state.sequence
    val wrong = PadGrid.TWO.pads.first { it != first[0] }

    g.tap(wrong, now + 200)
    assertEquals("W2.1:$wrong/${first[0]}+200", g.missTrace())

    assertEquals(PadsPhase.SCOLD, g.state.phase)
    assertEquals(Miss.WRONG, g.state.miss)
    assertEquals(1, g.state.mistakes)
    assertEquals(now + 200 + rules.scoldMs, g.state.nextAt)
    g.runUntil(PadsPhase.DEMO)
    assertEquals(1, g.state.round)
    assertEquals(4, g.state.sequence.size)
    // A new sequence; with this seed it differs from the missed one.
    assertNotEquals(first, g.state.sequence)
  }

  @Test
  fun skippingHerScold_startsTheSameRoundNow_butNotOnAQuickSecondTap() {
    val g = game()
    g.start(now)
    g.runUntil(PadsPhase.INPUT)
    val first = g.state.sequence
    g.tap(PadGrid.TWO.pads.first { it != first[0] }, now)
    val scold = g.state
    val missedAt = now

    assertEquals(scold, g.skipScold(missedAt + SKIP_GUARD_MS - 1))
    val next = g.skipScold(missedAt + SKIP_GUARD_MS)
    assertEquals(PadsPhase.DEMO, next.phase)
    assertEquals(0, next.round)
    assertEquals(first.size, next.sequence.size)
    assertEquals(missedAt + SKIP_GUARD_MS + rules.moveMs, next.nextAt)
    assertEquals(next, g.skipScold(missedAt + 5_000)) // nothing to skip once the demo runs
  }

  @Test
  fun threeSecondsWithoutATap_isTooSlow_andEachRightTapRestartsTheClock() {
    val g = game()
    g.start(now)
    g.runUntil(PadsPhase.INPUT)
    val handedOver = now + rules.exitMs // her hand gone: the plain 3 s from here
    assertEquals(handedOver + rules.tapTimeoutMs, g.state.nextAt)

    now = handedOver + 2_900
    g.tap(g.state.sequence[0], now)
    g.tick(handedOver + rules.tapTimeoutMs) // the old deadline passes harmlessly
    assertEquals(PadsPhase.INPUT, g.state.phase)

    g.runUntil(PadsPhase.SCOLD)
    assertEquals(handedOver + 2_900 + rules.tapTimeoutMs, now)
    assertEquals(Miss.SLOW, g.state.miss)
    assertEquals(1, g.state.timeouts)
    assertEquals(0, g.state.mistakes)
    assertEquals("S1.2", g.missTrace())
  }

  @Test
  fun aTapAfterTheDeadline_countsAsSlow_evenBeforeTheTimerFires() {
    val g = game()
    g.start(now)
    g.runUntil(PadsPhase.INPUT)
    g.tap(g.state.sequence[0], now + rules.exitMs + rules.tapTimeoutMs)
    assertEquals(Miss.SLOW, g.state.miss)
  }

  @Test
  fun aRightTap_lightsItsPad_briefly() {
    val g = game()
    g.start(now)
    g.runUntil(PadsPhase.INPUT)
    val pad = g.state.sequence[0]
    val flash = g.state.flash
    g.tap(pad, now + 100)
    assertEquals(pad, g.state.lit)
    assertEquals(flash + 1, g.state.flash)
    g.tick(now + 100 + rules.tapFlashMs)
    assertNull(g.state.lit)
    assertEquals(PadsPhase.INPUT, g.state.phase)
  }

  @Test
  fun sequences_neverRepeatAPadBackToBack_andFollowTheSeed() {
    fun sequences(seed: Long): List<List<Pad>> {
      val g = game(seed)
      g.start(now)
      val out = mutableListOf(g.state.sequence)
      repeat(30) {
        g.runUntil(PadsPhase.INPUT)
        g.tap(PadGrid.TWO.pads.first { it != g.state.sequence[0] }, now + 1)
        g.runUntil(PadsPhase.DEMO)
        out += g.state.sequence
      }
      return out
    }
    val a = sequences(42)
    assertTrue(a.all { s -> s.zipWithNext().none { (x, y) -> x == y } })
    assertTrue(a.flatten().toSet() == PadGrid.TWO.pads.toSet())
    now = 10_000L
    assertEquals(a, sequences(42))
  }

  @Test
  fun aOneRoundGame_passesAfterItsOnlySequence() {
    val g = PadsGame(rules.copy(lengths = listOf(3)), Random(1))
    g.start(now)
    assertEquals(1, g.state.rounds)
    g.runUntil(PadsPhase.INPUT)
    assertEquals(3, g.state.sequence.size)
    g.repeatSequence()
    assertEquals(PadsPhase.PASSED, g.state.phase)
  }
}
