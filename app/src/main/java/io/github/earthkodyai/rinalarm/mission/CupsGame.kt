package io.github.earthkodyai.rinalarm.mission

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The numbers that shape one cup shuffle (D17, task 3.4). [swaps] rise with the streak (the user's pick: 4, 5, 6; a
 * wrong pick starts again at the first); Easy's speeds were tuned on the dev rings and then frozen before the held-out
 * ones (docs/spikes/3.4-cup-shuffle.md). [forLevel] gives the other levels theirs (G.3, draft until G.4).
 *
 * @property cups how many cups stand on the table (3 to 5, G.3).
 * @property shaped every shuffle is drawn to one band of difficulty (G.3, G.4: see [CupsGame]'s shapedSwaps), while
 *   the ball still ends anywhere with equal odds. Off at Easy, which keeps its held-out draws.
 * @property swaps swaps in the shuffle before the 1st, 2nd and 3rd pick of a streak; its size is the streak to win.
 * @property leadMs her hands reaching the cups before an act starts moving them; [firstLeadMs] for the first one, so the
 *   table settles in view before the ball shows.
 * @property showMs the ball shown under its cup before the first shuffle.
 * @property rightMs a right pick: the cup stays up over the ball before the next shuffle.
 * @property scoldMs a wrong pick: both cups stay up while Rin sulks, then a new shuffle with the count back at 0.
 */
data class CupsRules(
  val swaps: List<Int> = listOf(4, 5, 6),
  val swapMs: Long = 450,
  val gapMs: Long = 150,
  val leadMs: Long = 300,
  val firstLeadMs: Long = 500,
  val exitMs: Long = 250,
  val upMs: Long = 250,
  val downMs: Long = 250,
  val showMs: Long = 900,
  val rightMs: Long = 600,
  val scoldMs: Long = 2_500,
  val cups: Int = 3,
  val shaped: Boolean = false,
) {
  init {
    require(swaps.isNotEmpty() && swaps.all { it >= 1 })
    require(cups in CUPS_RANGE)
  }

  val streak: Int
    get() = swaps.size

  companion object {
    val CUPS_RANGE = 3..5

    /**
     * Each level's shuffle (plan phase-games, D33): more swaps, quicker swaps with shorter pauses, then a 4th and a
     * 5th cup. Nightmare's 5 cups at about four swaps a second outrun the eye, and three right guesses are 1 in 125.
     */
    fun forLevel(level: Difficulty): CupsRules =
      when (level) {
        Difficulty.EASY -> CupsRules()
        Difficulty.NORMAL -> CupsRules(swaps = listOf(5, 6, 7), swapMs = 380, gapMs = 120, shaped = true)
        Difficulty.HARD -> CupsRules(swaps = listOf(6, 7, 8), swapMs = 320, gapMs = 100, cups = 4, shaped = true)
        Difficulty.NIGHTMARE -> CupsRules(swaps = listOf(8, 10, 12), swapMs = 230, gapMs = 60, cups = 5, shaped = true)
      }

    /**
     * The pairs of slots Rin may swap with [cups] on the table. Her arms cannot cross her middle (cups.ts handInSwap),
     * so her right hand works the low slot and her left the high one, and they meet halfway: a pair must span her
     * middle, its halfway point at most half a slot off it. For 3 cups that is every pair (in the order Easy's draws
     * have always used); for 4 and 5 it still spreads the ball evenly (best blind guess 0.26 and 0.21 a pick).
     * cups.ts pairsFor has the same rule.
     */
    fun pairs(cups: Int): List<Pair<Int, Int>> {
      if (cups == 3) return listOf(0 to 1, 1 to 2, 0 to 2)
      val middle = (cups - 1) / 2.0
      return (0 until cups)
        .flatMap { p -> (p + 1 until cups).map { q -> p to q } }
        .filter { (p, q) -> p <= middle && q >= middle && abs((p + q) / 2.0 - middle) <= 0.5 }
    }
  }
}

/**
 * One act of the game, for the ring screen to draw: the character page (web/character/src/cups.ts, which has the same
 * rules for where each cup is) or the native 2D board. Times are the elapsed clock's. At the start of every act cup i
 * stands in slot i (0 until [cups], the user's left to right) and the ball is under the cup in slot [ball].
 */
sealed interface CupsAct {
  val ball: Int
  val at: Long
  /** How many cups are on the table (CupsRules.cups). */
  val cups: Int

  /** Cups lift and stay up [holdMs] (null: until the next act). Rin's hands lift the ones in [hands]. */
  data class Lift(
    override val ball: Int,
    override val at: Long,
    val lift: List<Int>,
    val hands: List<Int>,
    val leadMs: Long,
    val upMs: Long,
    val holdMs: Long?,
    val downMs: Long,
    val exitMs: Long,
    override val cups: Int = 3,
  ) : CupsAct

  /** Cups standing still, hands off: the table coming into view before the game starts. */
  data class Rest(override val ball: Int, override val at: Long, override val cups: Int = 3) : CupsAct

  /** Her hands slide pairs of cups past each other; in a swap of slots p < q the cup from p passes in front. */
  data class Shuffle(
    override val ball: Int,
    override val at: Long,
    val swaps: List<Pair<Int, Int>>,
    val leadMs: Long,
    val swapMs: Long,
    val gapMs: Long,
    val exitMs: Long,
    override val cups: Int = 3,
  ) : CupsAct {
    /** When the cups come to rest: the pick opens then. */
    val endsAt: Long
      get() = at + leadMs + maxOf(swaps.size * (swapMs + gapMs) - gapMs, 0)
  }
}

enum class CupsPhase {
  /** Waiting for "Let's play": nothing moves for someone still asleep. */
  READY,
  /** Rin lifts the ball's cup so the user sees where it starts. */
  SHOW,
  SHUFFLE,
  /** The cups are still: the user picks one. */
  PICK,
  /** The picked cup is up; after a wrong pick the ball's cup too, and Rin sulks. */
  REVEAL,
  PASSED,
}

/**
 * @property streak right picks in a row; [target] wins.
 * @property right after a pick: whether it found the ball (REVEAL and PASSED).
 * @property nextAt elapsed-clock time the game must be [CupsGame.tick]ed at, or null when only a pick moves it.
 */
data class CupsState(
  val phase: CupsPhase = CupsPhase.READY,
  val streak: Int = 0,
  val target: Int = 3,
  val act: CupsAct? = null,
  val picked: Int? = null,
  val right: Boolean? = null,
  val picks: Int = 0,
  val mistakes: Int = 0,
  /** Taps while the cups were still moving (ignored, logged). */
  val earlyTaps: Int = 0,
  val nextAt: Long? = null,
  /** CupsRules.cups, for the table and the tap zones before the first act. */
  val cups: Int = 3,
)

/**
 * The cup shuffle (D17, plan phase-3 §1) as a pure state machine, like PadsGame: every call takes the elapsed-clock
 * time and returns the next [CupsState]. CupShuffleMission drives it: [tick] at [CupsState.nextAt], [pick] on a cup.
 * Three right in a row wins (1 in 27 by guessing); a wrong pick resets the count and the next shuffle starts from
 * where the ball really is.
 */
class CupsGame(private val rules: CupsRules = CupsRules(), private val random: Random) {
  var state = CupsState(target = rules.streak, cups = rules.cups)
    private set

  /** Where the ball really is. The screen learns it only from the acts. */
  private var ball = 0
  private var shuffles = 0
  private var pickFrom = 0L
  private val trace = mutableListOf<String>()
  private var rightWaits = 0L

  /** The time the user took over each right pick, from the cups coming to rest; the tournament's tie-break (G.5). */
  fun thinkMs(): Long = rightWaits

  /**
   * Every pick, for the log: `R2+850` is a right pick after the 2nd shuffle, 850 ms after the cups stopped;
   * `W3:0/2+1200` a wrong one, slot 0 picked with the ball in slot 2.
   */
  fun trace(): String = trace.joinToString(",")

  /** "Let's play": Rin shows the ball. Ignored once the game is under way. */
  fun start(now: Long): CupsState {
    if (state.phase != CupsPhase.READY) return state
    ball = random.nextInt(rules.cups)
    val act = lift(now, listOf(ball), hands = listOf(ball), hold = rules.showMs, lead = rules.firstLeadMs)
    return set(state.copy(phase = CupsPhase.SHOW, act = act, nextAt = liftEnd(act)))
  }

  fun pick(slot: Int, now: Long): CupsState {
    // Never a crash on the ring screen: a slot off this table (a stale tap zone) is no pick at all.
    if (slot !in 0 until rules.cups) return state
    val s = state
    if (s.phase == CupsPhase.SHOW || s.phase == CupsPhase.SHUFFLE) return set(s.copy(earlyTaps = s.earlyTaps + 1))
    if (s.phase != CupsPhase.PICK) return s
    val wait = now - pickFrom
    if (slot == ball) {
      trace += "R$shuffles+$wait"
      rightWaits += wait
      val streak = s.streak + 1
      if (streak >= rules.streak) {
        // Her arms stay free for the clap: the cup rises by itself and stays up over the ball.
        val act = lift(now, listOf(slot), hands = emptyList(), hold = null)
        return set(s.copy(phase = CupsPhase.PASSED, streak = streak, act = act, picked = slot, right = true, picks = s.picks + 1, nextAt = null))
      }
      val act = lift(now, listOf(slot), hands = emptyList(), hold = rules.rightMs)
      return set(s.copy(phase = CupsPhase.REVEAL, streak = streak, act = act, picked = slot, right = true, picks = s.picks + 1, nextAt = liftEnd(act)))
    }
    trace += "W$shuffles:$slot/$ball+$wait"
    // The picked cup rises by itself; Rin lifts the ball's to show where it was.
    val act = lift(now, listOf(slot, ball).sorted(), hands = listOf(ball), hold = rules.scoldMs)
    return set(
      s.copy(
        phase = CupsPhase.REVEAL,
        streak = 0,
        act = act,
        picked = slot,
        right = false,
        picks = s.picks + 1,
        mistakes = s.mistakes + 1,
        nextAt = liftEnd(act),
      )
    )
  }

  fun tick(now: Long): CupsState {
    val s = state
    val due = s.nextAt ?: return s
    if (now < due) return s
    return when (s.phase) {
      CupsPhase.SHOW,
      CupsPhase.REVEAL -> shuffle(now)
      CupsPhase.SHUFFLE -> {
        pickFrom = due
        set(s.copy(phase = CupsPhase.PICK, nextAt = null))
      }
      else -> s
    }
  }

  /**
   * A tap while Rin sulks over a wrong pick (G.1): both cups come down now instead of after [CupsRules.scoldMs], then the
   * next shuffle. Only once they are fully up (and [SKIP_GUARD_MS] into the sulk), so the user always sees the ball.
   */
  fun skipScold(now: Long): CupsState {
    val s = state
    val act = s.act as? CupsAct.Lift ?: return s
    if (s.phase != CupsPhase.REVEAL || s.right != false) return s
    val up = act.at + act.leadMs + act.upMs
    if (now < up || now < act.at + SKIP_GUARD_MS) return s
    val held = (now - up).coerceAtMost(act.holdMs ?: Long.MAX_VALUE)
    val cut = act.copy(holdMs = held)
    return set(s.copy(act = cut, nextAt = liftEnd(cut)))
  }

  private fun shuffle(now: Long): CupsState {
    val n = rules.swaps[state.streak]
    val swaps = if (rules.shaped) shapedSwaps(n) else draw(n)
    val act = CupsAct.Shuffle(ball, now, swaps, rules.leadMs, rules.swapMs, rules.gapMs, rules.exitMs, rules.cups)
    ball = CupsTimeline.afterSwaps(swaps, ball)
    shuffles++
    return set(state.copy(phase = CupsPhase.SHUFFLE, act = act, picked = null, right = null, nextAt = act.endsAt))
  }

  /**
   * [n] swaps, never the same pair twice in a row: a swap undone at once looks like nothing happened. [far] weighs each
   * pair by how many slots apart its cups are, so long swaps that pass the other cups come more often.
   */
  private fun draw(n: Int, far: Boolean = false): List<Pair<Int, Int>> {
    val swaps = ArrayList<Pair<Int, Int>>(n)
    while (swaps.size < n) {
      val pair = if (far) farPair() else pairs[random.nextInt(pairs.size)]
      if (pair != swaps.lastOrNull()) swaps += pair
    }
    return swaps
  }

  private fun farPair(): Pair<Int, Int> {
    var r = random.nextInt(pairs.sumOf { it.second - it.first })
    for (pair in pairs) {
      r -= pair.second - pair.first
      if (r < 0) return pair
    }
    return pairs.last()
  }

  /**
   * A shuffle in one band of difficulty (CupsRules.shaped). The slot the ball ends in is drawn first, then shuffles
   * until one ends there and [fair] holds: asking only for how the ball moves would tie where it ends to how often it
   * moved (with 4 cups every pair crosses her middle, so 7 swaps with at least 4 moves left a blind guess right 41% of
   * the time). Takes ~10 draws, rarely 100 (simulated); after [SHAPE_TRIES] a plain draw.
   */
  private fun shapedSwaps(n: Int): List<Pair<Int, Int>> {
    val end = random.nextInt(rules.cups)
    repeat(SHAPE_TRIES) {
      val swaps = draw(n, far = true)
      if (CupsTimeline.afterSwaps(swaps, ball) == end && fair(swaps, ball, rules.cups)) return swaps
    }
    return draw(n)
  }

  private fun lift(now: Long, slots: List<Int>, hands: List<Int>, hold: Long?, lead: Long = rules.leadMs) =
    CupsAct.Lift(ball, now, slots, hands, lead, rules.upMs, hold, rules.downMs, rules.exitMs, rules.cups)

  private fun liftEnd(act: CupsAct.Lift): Long = act.at + act.leadMs + act.upMs + (act.holdMs ?: 0) + act.downMs

  private fun set(next: CupsState): CupsState {
    state = next
    return next
  }

  private val pairs = CupsRules.pairs(rules.cups)

  companion object {
    private const val SHAPE_TRIES = 5_000

    /**
     * Whether a shuffle starting with the ball in slot [from] is neither easier nor harder than the rest of its level
     * (G.4). What makes eyes lose a cup is a close pass with the others (multiple-object tracking), and what lets them
     * keep it is a cup that sits still or one followed through move after move, so:
     * - the ball's cup moves in 40–60% of the swaps (G.3: rounds where it barely moved were guessed);
     * - never in more than 2 swaps in a row (the user: a cup swapped again and again is easy to follow);
     * - at most half the swaps, and half the ball's, are between cups side by side (the user: too many of those; on 3
     *   cups only 0–2 is long and it never comes twice in a row, so half is as far as it goes);
     * - with as many swaps as cups, every cup moves at least once.
     */
    fun fair(swaps: List<Pair<Int, Int>>, from: Int, cups: Int): Boolean {
      val n = swaps.size
      var at = from
      var moves = 0
      var run = 0
      var longestRun = 0
      var ballSideBySide = 0
      for ((p, q) in swaps) {
        if (at == p || at == q) {
          moves++
          run++
          longestRun = maxOf(longestRun, run)
          if (q - p == 1) ballSideBySide++
        } else {
          run = 0
        }
        at = if (at == p) q else if (at == q) p else at
      }
      val sideBySide = swaps.count { (p, q) -> q - p == 1 }
      val touched = swaps.flatMap { listOf(it.first, it.second) }.toSet()
      return moves in 2 * n / 5..(3 * n + 4) / 5 &&
        longestRun <= 2 &&
        sideBySide <= (n + 1) / 2 &&
        ballSideBySide <= (moves + 1) / 2 &&
        (n < cups || touched.size == cups)
    }
  }
}

/** A cup on the 2D board: [x] in slots (0 until the act's cups), [z] from -1 (back) to 1 (front), [lift] 0 (down) to 1 (up). */
data class CupPose(val x: Float, val z: Float, val lift: Float)

data class CupsFrame(val cups: List<CupPose>, val ballCup: Int)

/**
 * Where the cups are at a moment of an act: the same rules as the character page's (web/character/src/cups.ts
 * frameAt), without her hands, for the native 2D board.
 */
object CupsTimeline {
  fun afterSwaps(swaps: List<Pair<Int, Int>>, ball: Int): Int =
    swaps.fold(ball) { b, (p, q) ->
      when (b) {
        p -> q
        q -> p
        else -> b
      }
    }

  fun frameAt(act: CupsAct, now: Long): CupsFrame {
    val t = now - act.at
    return when (act) {
      is CupsAct.Rest -> CupsFrame(List(act.cups) { CupPose(it.toFloat(), 0f, 0f) }, act.ball)
      is CupsAct.Lift -> {
        val up = act.leadMs
        val hold = up + act.upMs
        val down = act.holdMs?.let { hold + it } ?: Long.MAX_VALUE
        val lift =
          when {
            t < up -> 0f
            t < hold -> smooth((t - up).toFloat() / act.upMs)
            t < down -> 1f
            else -> smooth(1 - (t - down).toFloat() / act.downMs)
          }
        CupsFrame(List(act.cups) { CupPose(it.toFloat(), 0f, if (it in act.lift) lift else 0f) }, act.ball)
      }
      is CupsAct.Shuffle -> {
        val slotOf = IntArray(act.cups) { it }
        val cups = MutableList(act.cups) { CupPose(it.toFloat(), 0f, 0f) }
        val step = act.swapMs + act.gapMs
        val n = act.swaps.size
        if (n > 0) {
          val i = ((t - act.leadMs).floorDiv(step)).toInt().coerceIn(0, n - 1)
          val into = t - act.leadMs - i * step
          for (j in 0..i) {
            if (j == i && into < act.swapMs) break
            val (p, q) = act.swaps[j]
            val a = slotOf.indexOf(p)
            val b = slotOf.indexOf(q)
            slotOf[a] = q
            slotOf[b] = p
          }
          for (c in slotOf.indices) cups[c] = CupPose(slotOf[c].toFloat(), 0f, 0f)
          val f = into.toFloat() / act.swapMs
          if (f in 0f..<1f) {
            val (p, q) = act.swaps[i]
            val lo = minOf(p, q)
            val hi = maxOf(p, q)
            // Each cup eases in to the middle and out again (cups.ts swapPose): still for a moment side by side.
            val mid = (lo + hi) / 2f
            val arc = sin(PI * smooth(f)).toFloat()
            val along = { from: Int, to: Int ->
              if (f < 0.5f) from + (mid - from) * smooth(2 * f) else mid + (to - mid) * smooth(2 * f - 1)
            }
            cups[slotOf.indexOf(lo)] = CupPose(along(lo, hi), arc, 0f)
            cups[slotOf.indexOf(hi)] = CupPose(along(hi, lo), -arc, 0f)
          }
        }
        CupsFrame(cups, act.ball)
      }
    }
  }

  private fun smooth(u: Float): Float {
    val x = u.coerceIn(0f, 1f)
    return x * x * (3 - 2 * x)
  }
}
