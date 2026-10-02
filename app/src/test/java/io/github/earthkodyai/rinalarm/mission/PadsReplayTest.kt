package io.github.earthkodyai.rinalarm.mission

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real rings replayed from their log line (seed + miss trace), G.2. The game is rebuilt from the seed and played as
 * the trace says: right taps up to each miss, then the miss. Every miss must name the pad the game showed, and the
 * right-tap count must come out as logged, so the log is the game the user saw.
 *
 * A lost tap would show as a "wrong" tap on the pad due *after* the one the game wanted (the user had moved on);
 * [Replay.skippedAhead] counts those, as a hint to look closer, not proof.
 */
class PadsReplayTest {
  private data class Missed(val wrong: Boolean, val round: Int, val pos: Int, val tapped: Pad?, val due: Pad?)

  private class Replay(val rightTaps: Int, val roundsPassed: Int, val skippedAhead: Int, val misses: Int)

  private fun parse(trace: String): List<Missed> =
    trace.split(",").map { m ->
      val wrong = m[0] == 'W'
      val (round, pos) = m.substring(1).substringBefore(":").substringBefore("+").split(".").map(String::toInt)
      val pads = if (wrong) m.substringAfter(":").substringBefore("+").split("/").map(Pad::valueOf) else listOf(null, null)
      Missed(wrong, round, pos, pads[0], pads[1])
    }

  private fun replay(rules: PadsRules, seed: Long, trace: String, loggedRightTaps: Int): Replay {
    var now = 1_000L
    val g = PadsGame(rules, Random(seed))
    fun toInput() {
      while (g.state.phase != PadsPhase.INPUT) {
        now = checkNotNull(g.state.nextAt)
        g.tick(now)
      }
    }
    var right = 0
    var ahead = 0
    g.start(now)
    val misses = parse(trace)
    for (m in misses) {
      // Rounds the user passed before this miss.
      while (g.state.round + 1 < m.round) {
        toInput()
        for (p in g.state.sequence) g.tap(p, ++now).also { right++ }
      }
      toInput()
      val sequence = g.state.sequence
      assertEquals("round of $m", m.round, g.state.round + 1)
      for (p in sequence.take(m.pos - 1)) g.tap(p, ++now).also { right++ }
      if (m.wrong) {
        assertEquals("the pad due at $m", sequence[m.pos - 1], m.due)
        if (m.tapped == sequence.getOrNull(m.pos)) ahead++
        g.tap(checkNotNull(m.tapped), ++now)
      } else {
        now += rules.exitMs + rules.tapTimeoutMs
        g.tick(now)
      }
      assertEquals(PadsPhase.SCOLD, g.state.phase)
      now = checkNotNull(g.state.nextAt)
      g.tick(now)
    }
    // The last try, cut short by the emergency stop: only some of its pads were tapped.
    val last = loggedRightTaps - right
    toInput()
    assertTrue("$last right taps in a try of ${g.state.sequence.size}", last in 0 until g.state.sequence.size)
    return Replay(loggedRightTaps, g.state.round, ahead, misses.size)
  }

  /**
   * The rings of 2026-10-02 (G.2) replayed here until G.4 changed how sequences above Easy are drawn; their seeds now
   * draw other games. This plays one under today's rules, logs it the way ColourPadsMission does, and replays the log,
   * so the next "that was right!" can be checked the same way.
   */
  @Test
  fun aLoggedGame_replaysFromItsSeedAndTrace() {
    for (level in listOf(Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE)) {
      val rules = PadsRules.forLevel(level)
      val seed = 30994926827693 + level.ordinal
      var now = 1_000L
      val g = PadsGame(rules, Random(seed))
      var right = 0
      fun toInput() {
        while (g.state.phase != PadsPhase.INPUT) {
          now = checkNotNull(g.state.nextAt)
          g.tick(now)
        }
      }
      fun rightTaps(n: Int) = g.state.sequence.take(n).forEach { g.tap(it, ++now).also { right++ } }
      fun wrongAt(i: Int) {
        rightTaps(i)
        g.tap(rules.grid.pads.first { it != g.state.sequence[i] }, ++now)
        now = checkNotNull(g.state.nextAt)
        g.tick(now)
      }
      g.start(now)
      toInput()
      wrongAt(3)
      toInput()
      rightTaps(g.state.sequence.size)
      toInput()
      rightTaps(2)
      now += rules.tapTimeoutMs
      g.tick(now)
      now = checkNotNull(g.state.nextAt)
      g.tick(now)
      toInput()
      rightTaps(1)
      val r = replay(rules, seed, g.missTrace(), loggedRightTaps = right)
      assertEquals("$level", 1, r.roundsPassed)
      assertEquals("$level", 2, r.misses)
    }
  }
}
