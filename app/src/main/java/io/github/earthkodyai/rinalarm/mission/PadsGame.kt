package io.github.earthkodyai.rinalarm.mission

import kotlin.random.Random

/** The four colour pads (D17), in grid order: red top left, blue top right, yellow bottom left, green bottom right. */
enum class Pad {
  RED,
  BLUE,
  YELLOW,
  GREEN;

  val row: Int
    get() = ordinal / 2

  val column: Int
    get() = ordinal % 2
}

/**
 * The numbers that shape one game. [lengths] and [tapTimeoutMs] are D17's; the demo timings and [scoldMs] are tuned
 * on the dev rings and then frozen before the held-out ones (docs/spikes/3.3-colour-pads.md).
 *
 * @property moveMs Rin's hand gliding onto the next pad (from above the grid for the first one).
 * @property pressMs the pad lit and its note playing under her finger.
 * @property exitMs her hand leaving the grid after the last press. The user may already answer while it leaves (dev-3:
 *   taps in that gap were ignored, so the next right tap read as wrong); it is added to their first tap's time.
 * @property scoldMs how long the screen stays on Rin after a miss before a new sequence.
 */
data class PadsRules(
  val lengths: List<Int> = listOf(3, 4, 5),
  val tapTimeoutMs: Long = 3_000,
  val moveMs: Long = 450,
  val pressMs: Long = 450,
  val exitMs: Long = 400,
  val scoldMs: Long = 2_500,
  /** How long a pad stays lit after the user taps it. */
  val tapFlashMs: Long = 250,
) {
  init {
    require(lengths.isNotEmpty() && lengths.all { it >= 1 })
  }

  /** The wake-up check's answer (D17): one round of 3 (task 3.6). */
  fun shortRound(): PadsRules = copy(lengths = listOf(lengths.first()))
}

enum class PadsPhase {
  /** Waiting for "Let's play": nothing is shown to someone still asleep. */
  READY,
  /** Rin's hand shows the sequence; taps are ignored. */
  DEMO,
  /** The user repeats it. */
  INPUT,
  /** A miss: the screen cuts to Rin, then the same round starts again with a new sequence. */
  SCOLD,
  PASSED,
}

enum class Miss {
  SLOW,
  WRONG,
}

/**
 * One moment of the game, everything the ring screen draws.
 *
 * @property round 0-based index into [PadsRules.lengths].
 * @property hand the pad Rin's hand is on or gliding to; null while it is off the grid.
 * @property lit the pad that is lit (Rin pressing it, or the user's tap).
 * @property entered how many of [sequence] the user has repeated in this attempt.
 * @property nextAt elapsed-clock time the game must be [PadsGame.tick]ed at, or null when only a tap moves it.
 * @property miss why the last attempt ended (SCOLD only), and [line] which of Rin's lines for it she says.
 */
data class PadsState(
  val phase: PadsPhase = PadsPhase.READY,
  val round: Int = 0,
  val rounds: Int = 3,
  val sequence: List<Pad> = emptyList(),
  val hand: Pad? = null,
  val pressing: Boolean = false,
  val lit: Pad? = null,
  val entered: Int = 0,
  val nextAt: Long? = null,
  val miss: Miss? = null,
  val line: Int = 0,
  val mistakes: Int = 0,
  val timeouts: Int = 0,
  /** Bumps each time a pad lights, so the same pad lit twice in a row still counts as two notes. */
  val flash: Int = 0,
  /** PadsRules.tapTimeoutMs, for the ring screen's countdown bar. */
  val tapTimeoutMs: Long = 3_000,
  /** Taps while Rin was still showing the sequence (ignored, logged). */
  val earlyTaps: Int = 0,
)

/**
 * The colour-pads game (D17, plan phase-3 §2) as a pure state machine: every call takes the elapsed-clock time and
 * returns the next [PadsState], so the rules are unit-tested without Android or coroutines. ColourPadsMission drives
 * it: [tick] at [PadsState.nextAt], [tap] on a pad.
 *
 * A demo, for a sequence of n: step i glides for [PadsRules.moveMs] and then presses for [PadsRules.pressMs]; after
 * the last press the user may answer: the first tap gets [PadsRules.exitMs] (her hand leaving) plus
 * [PadsRules.tapTimeoutMs], each later tap [PadsRules.tapTimeoutMs]. Taps during the demo are ignored and counted.
 */
class PadsGame(
  private val rules: PadsRules = PadsRules(),
  private val random: Random,
  private val slowLines: Int = SLOW_LINES,
  private val wrongLines: Int = WRONG_LINES,
) {
  var state = PadsState(rounds = rules.lengths.size, tapTimeoutMs = rules.tapTimeoutMs)
    private set

  private var demoStart = 0L
  private var flashUntil: Long? = null
  private var inputDeadline = 0L
  private val lastLine = mutableMapOf<Miss, Int>()
  /** When the user's current wait began: the answer window opening, or their last right tap. */
  private var waitFrom = 0L
  private val misses = mutableListOf<String>()

  /**
   * Every miss so far, for the log: `W2.3:RED/BLUE+850` is a wrong tap in round 2 on the 3rd pad, RED tapped where
   * BLUE was due, 850 ms into the wait; `S1.1` a timeout on round 1's first pad. Lets a "that was right!" be checked.
   */
  fun missTrace(): String = misses.joinToString(",")

  /** "Let's play": the first demo starts. Ignored once the game is under way. */
  fun start(now: Long): PadsState {
    if (state.phase != PadsPhase.READY) return state
    return beginDemo(round = 0, now)
  }

  fun tap(pad: Pad, now: Long): PadsState {
    val s = state
    if (s.phase == PadsPhase.DEMO) return set(s.copy(earlyTaps = s.earlyTaps + 1))
    if (s.phase != PadsPhase.INPUT) return s
    // A tap after the deadline is a timeout that tick() has not caught yet (the timer runs late under load).
    if (now >= inputDeadline) return tick(now)
    if (pad != s.sequence[s.entered]) {
      misses += "W${s.round + 1}.${s.entered + 1}:$pad/${s.sequence[s.entered]}+${now - waitFrom}"
      return set(scold(s, Miss.WRONG, now))
    }
    waitFrom = now
    val entered = s.entered + 1
    if (entered < s.sequence.size) {
      inputDeadline = now + rules.tapTimeoutMs
      flashUntil = now + rules.tapFlashMs
      return set(s.copy(entered = entered, lit = pad, flash = s.flash + 1, nextAt = minOf(inputDeadline, flashUntil!!)))
    }
    // The round is done: the last pad flashes while the hand comes back for the next one.
    if (s.round + 1 >= rules.lengths.size) {
      return set(s.copy(phase = PadsPhase.PASSED, entered = entered, lit = pad, flash = s.flash + 1, nextAt = null))
    }
    val next = beginDemo(s.round + 1, now)
    return set(next.copy(lit = pad, flash = s.flash + 1, nextAt = now + minOf(rules.tapFlashMs, rules.moveMs)))
  }

  fun tick(now: Long): PadsState {
    val s = state
    return when (s.phase) {
      PadsPhase.READY,
      PadsPhase.PASSED -> s
      PadsPhase.DEMO -> set(demoAt(s, now))
      PadsPhase.INPUT ->
        when {
          now >= inputDeadline -> {
            misses += "S${s.round + 1}.${s.entered + 1}"
            set(scold(s, Miss.SLOW, now))
          }
          else -> {
            val flashing = flashUntil?.let { now < it } == true
            if (!flashing) flashUntil = null
            set(s.copy(lit = if (flashing) s.lit else null, nextAt = flashUntil?.let { minOf(it, inputDeadline) } ?: inputDeadline))
          }
        }
      PadsPhase.SCOLD -> if (now >= (s.nextAt ?: now)) set(beginDemo(s.round, now)) else s
    }
  }

  private fun beginDemo(round: Int, now: Long): PadsState {
    demoStart = now
    flashUntil = null
    val sequence = sequence(rules.lengths[round])
    return set(
      state.copy(
        phase = PadsPhase.DEMO,
        round = round,
        sequence = sequence,
        hand = sequence.first(),
        pressing = false,
        lit = null,
        entered = 0,
        miss = null,
        nextAt = now + rules.moveMs,
      )
    )
  }

  /** Where the demo is at [now]: which step, gliding or pressing, or over. */
  private fun demoAt(s: PadsState, now: Long): PadsState {
    val step = rules.moveMs + rules.pressMs
    val t = now - demoStart
    val i = (t / step).toInt()
    if (i >= s.sequence.size) {
      // Her turn is over as her last press ends: the hand leaves while the user may already answer.
      val end = demoStart + s.sequence.size * step
      waitFrom = end
      inputDeadline = end + rules.exitMs + rules.tapTimeoutMs
      return s.copy(phase = PadsPhase.INPUT, hand = null, pressing = false, lit = null, nextAt = inputDeadline)
    }
    val stepStart = demoStart + i * step
    val pressing = now >= stepStart + rules.moveMs
    val pad = s.sequence[i]
    return s.copy(
      hand = pad,
      pressing = pressing,
      lit = if (pressing) pad else null,
      // A new press is a new note, even for a lit pad left over from the user's last tap.
      flash = if (pressing && !s.pressing) s.flash + 1 else s.flash,
      nextAt = if (pressing) stepStart + step else stepStart + rules.moveMs,
    )
  }

  private fun scold(s: PadsState, miss: Miss, now: Long): PadsState {
    val count = if (miss == Miss.SLOW) slowLines else wrongLines
    // Never the same line twice in a row for the same kind of miss.
    var line = random.nextInt(count)
    if (count > 1 && line == lastLine[miss]) line = (line + 1 + random.nextInt(count - 1)) % count
    lastLine[miss] = line
    flashUntil = null
    return s.copy(
      phase = PadsPhase.SCOLD,
      hand = null,
      pressing = false,
      lit = null,
      miss = miss,
      line = line,
      mistakes = s.mistakes + if (miss == Miss.WRONG) 1 else 0,
      timeouts = s.timeouts + if (miss == Miss.SLOW) 1 else 0,
      nextAt = now + rules.scoldMs,
    )
  }

  /** No pad twice in a row, so every step of the demo is a visible move of her hand. */
  private fun sequence(length: Int): List<Pad> {
    val out = ArrayList<Pad>(length)
    repeat(length) {
      val options = Pad.entries.filter { it != out.lastOrNull() }
      out += options[random.nextInt(options.size)]
    }
    return out
  }

  private fun set(next: PadsState): PadsState {
    state = next
    return next
  }

  companion object {
    /** Rin's lines per miss (res/values/strings.xml pads_scold_slow / pads_scold_wrong; the user's pick, set A). */
    const val SLOW_LINES = 3
    const val WRONG_LINES = 3
  }
}
