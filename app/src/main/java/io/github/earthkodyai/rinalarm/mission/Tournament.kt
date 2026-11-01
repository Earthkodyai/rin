package io.github.earthkodyai.rinalarm.mission

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * The tournament (G.5, plan phase-games, D33): one game, levels without end, one miss ends the run. It is a mode the
 * user opens on their own; no alarm gains anything from it (ADR 0004). Stored by [stored].
 */
enum class TournamentGame(val stored: String) {
  PADS("pads"),
  CUPS("cups");

  companion object {
    fun fromStored(value: String?): TournamentGame? = entries.firstOrNull { it.stored == value }
  }
}

/**
 * The levels. The first [WARM_UP_LEVELS] are Hard, each a pad or a swap longer (tester, 2026-10-06: starting at
 * Nightmare was too hard). Cups keep the five cups of Nightmare's table, so it never changes size in the middle of a
 * run. After them comes Nightmare's first round (pads: 7 on the 3×3 board; cups: 8 swaps of 5): each level adds a pad or
 * a swap, and every [SPEED_EVERY] levels Rin gets [SPEED_STEP] faster, down to floors that keep each move several frames
 * long on a 60 fps screen (pads: hand glide 120 ms = 7 frames, press 110 ms; cups: swap 150 ms = 9 frames, gap 30 ms).
 * The time to answer a pad stays Nightmare's: the climb is in length and speed, not a shrinking window.
 */
object TournamentLadder {
  const val WARM_UP_LEVELS = 5
  const val SPEED_EVERY = 3
  const val SPEED_STEP = 0.93

  const val PADS_MOVE_FLOOR_MS = 120L
  const val PADS_PRESS_FLOOR_MS = 110L
  const val CUPS_SWAP_FLOOR_MS = 150L
  const val CUPS_GAP_FLOOR_MS = 30L

  private val padsBase = PadsRules.forLevel(Difficulty.NIGHTMARE)
  private val cupsBase = CupsRules.forLevel(Difficulty.NIGHTMARE)
  private val padsWarmUp = PadsRules.forLevel(Difficulty.HARD)
  private val cupsWarmUp = CupsRules.forLevel(Difficulty.HARD).copy(cups = cupsBase.cups)

  /** How much faster than Nightmare's first round Rin is at its [round]: 1, 1, 1, 0.93, 0.93, 0.93, 0.8649… */
  fun pace(round: Int): Double {
    require(round >= 1)
    return SPEED_STEP.pow((round - 1) / SPEED_EVERY)
  }

  fun pads(level: Int): PadsRules {
    if (level <= WARM_UP_LEVELS) return padsWarmUp.copy(lengths = listOf(padsWarmUp.lengths.first() + level - 1))
    val round = level - WARM_UP_LEVELS
    val k = pace(round)
    return padsBase.copy(
      lengths = listOf(padsBase.lengths.first() + round - 1),
      moveMs = maxOf(PADS_MOVE_FLOOR_MS, (padsBase.moveMs * k).roundToLong()),
      pressMs = maxOf(PADS_PRESS_FLOOR_MS, (padsBase.pressMs * k).roundToLong()),
    )
  }

  fun cups(level: Int): CupsRules {
    if (level <= WARM_UP_LEVELS) return cupsWarmUp.copy(swaps = listOf(cupsWarmUp.swaps.first() + level - 1))
    val round = level - WARM_UP_LEVELS
    val k = pace(round)
    return cupsBase.copy(
      swaps = listOf(cupsBase.swaps.first() + round - 1),
      swapMs = maxOf(CUPS_SWAP_FLOOR_MS, (cupsBase.swapMs * k).roundToLong()),
      gapMs = maxOf(CUPS_GAP_FLOOR_MS, (cupsBase.gapMs * k).roundToLong()),
    )
  }
}

/**
 * A run's result: more levels passed is better; on a tie, the faster to clear them, from GO to the last level passed
 * (the user's pick: the level that ended the run is not timed, so failing it quickly gains nothing). The board ranks
 * by this (G.6).
 */
data class TournamentScore(val game: TournamentGame, val levels: Int, val timeMs: Long) : Comparable<TournamentScore> {
  /** Better scores come first when sorted. */
  override fun compareTo(other: TournamentScore): Int =
    compareValuesBy(this, other, { -it.levels }, { it.timeMs })

  fun beats(other: TournamentScore?): Boolean = other == null || this < other
}

/**
 * One run, as a pure record the screen updates (elapsed-clock times): [start] at GO, each [pass] moves on and stamps
 * the time, and the first [miss] ends it. Calls after the end change nothing, so a late tap cannot add a level.
 */
class TournamentRun(val game: TournamentGame) {
  var level = 1
    private set

  /** From GO to the last level passed; 0 before the first. */
  var timeMs = 0L
    private set

  private var startedAt: Long? = null

  var over = false
    private set

  val passed: Int
    get() = level - 1

  fun start(now: Long) {
    if (startedAt == null) startedAt = now
  }

  fun pass(now: Long) {
    val start = startedAt ?: return
    if (over) return
    timeMs = (now - start).coerceAtLeast(0)
    level++
  }

  fun miss() {
    over = true
  }

  fun score(): TournamentScore = TournamentScore(game, passed, timeMs)
}
