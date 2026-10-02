package io.github.earthkodyai.rinalarm.mission

import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pads at each level (G.2): every level's rules, the 3×3 board, repeats, and Easy unchanged. */
class PadsLevelsTest {
  private var now = 10_000L

  /** Ticks the game at each moment it asks for until [phase], collecting the pads it lights and the lifts on the way. */
  private fun PadsGame.runUntil(phase: PadsPhase, lit: MutableList<Pad> = mutableListOf(), lifts: MutableList<Pad> = mutableListOf()): List<Pad> {
    var guard = 0
    while (state.phase != phase) {
      now = checkNotNull(state.nextAt) { "stuck in ${state.phase}" }
      val before = state
      tick(now)
      if (state.flash != before.flash) lit += checkNotNull(state.lit)
      if (state.lifting && !before.lifting) lifts += checkNotNull(state.hand)
      check(guard++ < 500)
    }
    return lit
  }

  @Test
  fun easy_isTheFrozenGame() {
    assertEquals(PadsRules(), PadsRules.forLevel(Difficulty.EASY))
    with(PadsRules()) {
      assertEquals(listOf(3, 4, 5), lengths)
      assertEquals(3_000L, tapTimeoutMs)
      assertEquals(450L to 450L, moveMs to pressMs)
      assertEquals(PadGrid.TWO, grid)
      assertFalse(repeats)
    }
  }

  @Test
  fun easy_drawsTheSameSequencesAsBeforeG2_soLoggedSeedsReplay() {
    // The draw as it was before G.2: the four pads minus the last one, one nextInt each.
    fun before(seed: Long, lengths: List<Int>): List<List<Pad>> {
      val r = Random(seed)
      val four = listOf(Pad.RED, Pad.BLUE, Pad.YELLOW, Pad.GREEN)
      return lengths.map { n ->
        val out = mutableListOf<Pad>()
        repeat(n) { out += four.filter { it != out.lastOrNull() }.let { it[r.nextInt(it.size)] } }
        out
      }
    }
    for (seed in listOf(1L, 42L, 987_654_321L)) {
      val g = PadsGame(PadsRules(), Random(seed))
      g.start(now)
      val seen = mutableListOf(g.state.sequence)
      while (seen.size < 3) {
        g.runUntil(PadsPhase.INPUT)
        for (pad in g.state.sequence) g.tap(pad, now + 1).also { now += 1 }
        seen += g.state.sequence
      }
      assertEquals(before(seed, listOf(3, 4, 5)), seen)
    }
  }

  @Test
  fun eachLevel_isHarderThanTheOneBelow() {
    val rules = Difficulty.entries.map(PadsRules::forLevel)
    rules.zipWithNext().forEach { (easier, harder) ->
      assertTrue(harder.lengths.zip(easier.lengths).all { (h, e) -> h > e })
      assertTrue(harder.tapTimeoutMs < easier.tapTimeoutMs)
      assertTrue(harder.moveMs + harder.pressMs < easier.moveMs + easier.pressMs)
      assertTrue(harder.grid.size >= easier.grid.size)
    }
    assertEquals(listOf(PadGrid.TWO, PadGrid.TWO, PadGrid.THREE, PadGrid.THREE), rules.map { it.grid })
    assertEquals(listOf(false, false, true, true), rules.map { it.repeats })
    assertEquals(listOf(7, 8, 9), rules.last().lengths)
  }

  @Test
  fun everyLevel_playsThroughItsThreeRounds_atItsOwnLengthsAndTimeout() {
    for (level in Difficulty.entries) {
      val rules = PadsRules.forLevel(level)
      val g = PadsGame(rules, Random(level.ordinal.toLong()))
      assertEquals(rules.grid, g.state.grid)
      g.start(now)
      val lengths = mutableListOf<Int>()
      while (g.state.phase != PadsPhase.PASSED) {
        val shown = g.runUntil(PadsPhase.INPUT)
        assertEquals(g.state.sequence, shown)
        lengths += g.state.sequence.size
        assertTrue(g.state.sequence.all { it in rules.grid.pads })
        // Each tap a moment before its time runs out still counts.
        now += rules.exitMs + rules.tapTimeoutMs - 1
        g.tap(g.state.sequence[0], now)
        for (pad in g.state.sequence.drop(1)) {
          now += rules.tapTimeoutMs - 1
          g.tap(pad, now)
        }
      }
      assertEquals("$level", rules.lengths, lengths)
      assertEquals(0, g.state.mistakes + g.state.timeouts)
    }
  }

  @Test
  fun aTapTooLate_atNightmare_isATimeout() {
    val rules = PadsRules.forLevel(Difficulty.NIGHTMARE)
    val g = PadsGame(rules, Random(3))
    g.start(now)
    g.runUntil(PadsPhase.INPUT)
    g.tap(g.state.sequence[0], now + 100)
    g.tap(g.state.sequence[1], now + 100 + rules.tapTimeoutMs)
    assertEquals(PadsPhase.SCOLD, g.state.phase)
    assertEquals(Miss.SLOW, g.state.miss)
  }

  @Test
  fun hardAndUp_useAllNinePads_andRepeatOne_herHandLiftingBetweenThePresses() {
    for (level in listOf(Difficulty.HARD, Difficulty.NIGHTMARE)) {
      val rules = PadsRules.forLevel(level)
      val used = mutableSetOf<Pad>()
      var repeats = 0
      for (seed in 0L until 40) {
        val g = PadsGame(rules, Random(seed))
        g.start(now)
        val sequence = g.state.sequence
        val lifts = mutableListOf<Pad>()
        // Both presses of a repeated pad light it: the demo shows exactly the sequence.
        assertEquals(sequence, g.runUntil(PadsPhase.INPUT, lifts = lifts))
        val expected = sequence.zipWithNext().filter { (a, b) -> a == b }.map { it.first }
        assertEquals(expected, lifts)
        repeats += expected.size
        used += sequence
      }
      assertEquals(PadGrid.THREE.pads.toSet(), used)
      assertTrue("$level: $repeats", repeats > 0)
    }
  }

  @Test
  fun aRepeatedPad_isTappedTwice() {
    val rules = PadsRules.forLevel(Difficulty.HARD)
    val seed = (0L until 200).first { s -> PadsGame(rules, Random(s)).start(0).sequence.zipWithNext().any { (a, b) -> a == b } }
    val g = PadsGame(rules, Random(seed))
    g.start(now)
    g.runUntil(PadsPhase.INPUT)
    val sequence = g.state.sequence
    val at = sequence.zipWithNext().indexOfFirst { (a, b) -> a == b }
    sequence.take(at + 1).forEach { g.tap(it, ++now) }
    // The same pad again is the next right tap, not a miss.
    g.tap(sequence[at + 1], ++now)
    assertEquals(PadsPhase.INPUT, g.state.phase)
    assertEquals(at + 2, g.state.entered)
  }

  @Test
  fun aPadOffTheBoard_isNoTap() {
    val g = PadsGame(PadsRules(), Random(5))
    g.start(now)
    g.runUntil(PadsPhase.INPUT)
    val before = g.state
    g.tap(Pad.WHITE, now + 1)
    assertEquals(before, g.state)
  }

  @Test
  fun theThreeByThreeBoard_keepsTheFourCorners_andNoLookAlikesSideBySide() {
    val three = PadGrid.THREE
    assertEquals(9, three.pads.toSet().size)
    assertEquals(Pad.entries.toSet(), three.pads.toSet())
    for (pad in PadGrid.TWO.pads) {
      assertEquals(PadGrid.TWO.row(pad) * 2, three.row(pad))
      assertEquals(PadGrid.TWO.column(pad) * 2, three.column(pad))
    }
    // Hues within ~30° of each other; purple sits 80° from blue.
    val lookAlikes = listOf(setOf(Pad.RED, Pad.ORANGE), setOf(Pad.ORANGE, Pad.YELLOW), setOf(Pad.RED, Pad.PINK), setOf(Pad.BLUE, Pad.CYAN))
    for (a in three.pads) for (b in three.pads) {
      val sideBySide = abs(three.row(a) - three.row(b)) + abs(three.column(a) - three.column(b)) == 1
      if (sideBySide) assertTrue("$a next to $b", lookAlikes.none { a in it && b in it })
    }
  }

  @Test
  fun everyPad_hasItsOwnNote_noTwoASemitoneApart_andNoneOnTheAlarmBeep() {
    val notes = AndroidPadNotes.FREQUENCIES
    assertEquals(Pad.entries.toSet(), notes.keys)
    // D17's four keep their notes.
    assertEquals(listOf(523.25, 659.26, 783.99, 1046.50), listOf(Pad.RED, Pad.BLUE, Pad.YELLOW, Pad.GREEN).map(notes::getValue))
    fun semitones(a: Double, b: Double) = abs(12 * ln(a / b) / ln(2.0))
    val all = notes.values.toList()
    for (i in all.indices) for (j in i + 1 until all.size) assertTrue(semitones(all[i], all[j]) > 1.5)
    assertTrue(all.all { semitones(it, 880.0) > 1.5 })
  }
}
