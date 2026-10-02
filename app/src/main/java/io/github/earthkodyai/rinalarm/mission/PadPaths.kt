package io.github.earthkodyai.rinalarm.mission

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Sequences for the levels above Easy (G.4, docs/spikes/G.4-difficulty.md). G.2 drew pads freely, so one round could
 * be far easier than the next: at Nightmare 61% of 9-pad rounds held a pad twice in a row and half went A-B-A. The
 * research on remembering a path of places (Corsi blocks) says what makes one easy, and each rule below removes one:
 * - a pad twice in a row is remembered *better* (the Ranschburg effect's facilitation); a pad coming back after two or
 *   more others is remembered worse, so only that kind of repeat is allowed;
 * - A-B-A, a pair of pads coming twice (A-B … A-B), three pads on a line and circling the 2×2 board are shapes,
 *   and a sequence that forms a shape is chunked and remembered as one;
 * - a longer path, and one that crosses itself, is harder: [pathLength] and [crossings] must land in one band for the
 *   sequence's length, so every round of a level is about as hard as every other.
 * Rejection drawing: the steps are drawn under the first rules, then the whole is kept only inside the band (1–2 draws
 * at Nightmare, about 15 for a 20-pad tournament round; simulated).
 */
object PadPaths {
  /**
   * Most draws find one in a few tries; past this many, the last draw that kept the step rules is played, or a plain
   * one (no pad twice in a row) when none did: the 2×2 board runs out of new pairs past about 8 pads, which no level
   * asks of it.
   */
  private const val TRIES = 2_000

  fun draw(length: Int, grid: PadGrid, random: Random): List<Pad> {
    var last: List<Pad>? = null
    repeat(TRIES) {
      val seq = steps(length, grid, random) ?: return@repeat
      if (inBand(seq, grid)) return seq
      last = seq
    }
    return last ?: plain(length, grid, random)
  }

  private fun plain(length: Int, grid: PadGrid, random: Random): List<Pad> {
    val out = ArrayList<Pad>(length)
    repeat(length) { out += grid.pads.filter { it != out.lastOrNull() }.let { it[random.nextInt(it.size)] } }
    return out
  }

  /** Whether [seq] keeps every rule: the step rules at each pad and the band as a whole. */
  fun keepsTheRules(seq: List<Pad>, grid: PadGrid): Boolean =
    seq.indices.all { i -> allows(seq.subList(0, i), seq[i], grid, seq.size) } && inBand(seq, grid)

  fun inBand(seq: List<Pad>, grid: PadGrid): Boolean {
    if (seq.size < 2) return true
    val low = minCrossings(seq.size, grid)
    return pathLength(seq, grid) / (seq.size - 1) >= minStep(grid) && crossings(seq, grid) in low..low + 2
  }

  /** The shortest mean step allowed, in pads: about the median of a free draw on each board. */
  fun minStep(grid: PadGrid): Double = if (grid == PadGrid.THREE) 1.5 else 1.1

  /** The fewest crossings a path of [length] pads must have: about the median of a free draw, so most rounds qualify. */
  fun minCrossings(length: Int, grid: PadGrid): Int =
    if (grid == PadGrid.THREE) maxOf(0, (length - 4) / 2) else if (length >= 6) 1 else 0

  /** How far her finger travels, in pads (a side is 1, a diagonal √2). */
  fun pathLength(seq: List<Pad>, grid: PadGrid): Double =
    seq.zipWithNext().sumOf { (a, b) -> hypot((grid.column(b) - grid.column(a)).toDouble(), (grid.row(b) - grid.row(a)).toDouble()) }

  /** How many times the path crosses itself: two of its strokes cutting through each other, not just meeting at a pad. */
  fun crossings(seq: List<Pad>, grid: PadGrid): Int {
    val strokes = seq.zipWithNext().map { (a, b) -> point(a, grid) to point(b, grid) }
    var n = 0
    for (i in strokes.indices) for (j in i + 2 until strokes.size) if (cross(strokes[i], strokes[j])) n++
    return n
  }

  /** Draws [length] pads under the step rules; null when it walks into a corner where no pad is allowed. */
  private fun steps(length: Int, grid: PadGrid, random: Random): List<Pad>? {
    val out = ArrayList<Pad>(length)
    repeat(length) {
      val options = grid.pads.filter { allows(out, it, grid, length) }
      if (options.isEmpty()) return null
      out += options[random.nextInt(options.size)]
    }
    return out
  }

  private fun allows(before: List<Pad>, pad: Pad, grid: PadGrid, length: Int): Boolean {
    val n = before.size
    if (n >= 1 && pad == before[n - 1]) return false
    if (n >= 2 && pad == before[n - 2]) return false
    // A pad may come back, but no more often than the board's size forces (twice in up to 9 pads on the 3×3).
    if (before.count { it == pad } >= (length + grid.pads.size - 1) / grid.pads.size + 1) return false
    if ((0 until n - 1).any { before[it] == before[n - 1] && before[it + 1] == pad }) return false
    if (n >= 2) {
      val (a, b, c) = Triple(point(before[n - 2], grid), point(before[n - 1], grid), point(pad, grid))
      if (turn(a, b, c) == 0 && dot(a, b, c) > 0) return false
      // Circling the 2×2 board: three sides in a row, turning the same way.
      if (n >= 3 && grid == PadGrid.TWO) {
        val z = point(before[n - 3], grid)
        if (side(z, a) && side(a, b) && side(b, c) && turn(z, a, b) * turn(a, b, c) > 0) return false
      }
    }
    return true
  }

  private fun point(pad: Pad, grid: PadGrid) = grid.column(pad) to grid.row(pad)

  private fun side(a: Pair<Int, Int>, b: Pair<Int, Int>) = abs(a.first - b.first) + abs(a.second - b.second) == 1

  /** The z of (b − a) × (c − b): 0 on a line, its sign the way the path turns. */
  private fun turn(a: Pair<Int, Int>, b: Pair<Int, Int>, c: Pair<Int, Int>) =
    (b.first - a.first) * (c.second - b.second) - (b.second - a.second) * (c.first - b.first)

  private fun dot(a: Pair<Int, Int>, b: Pair<Int, Int>, c: Pair<Int, Int>) =
    (b.first - a.first) * (c.first - b.first) + (b.second - a.second) * (c.second - b.second)

  private fun cross(s: Pair<Pair<Int, Int>, Pair<Int, Int>>, t: Pair<Pair<Int, Int>, Pair<Int, Int>>): Boolean {
    val (a, b) = s
    val (c, d) = t
    if (setOf(a, b, c, d).size < 4) return false
    fun o(p: Pair<Int, Int>, q: Pair<Int, Int>, r: Pair<Int, Int>) = Integer.signum((q.first - p.first) * (r.second - p.second) - (q.second - p.second) * (r.first - p.first))
    return o(a, b, c) * o(a, b, d) < 0 && o(c, d, a) * o(c, d, b) < 0
  }
}
