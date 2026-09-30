package io.github.earthkodyai.rinalarm.character

import kotlin.random.Random

/**
 * Rin's gestures (plan 05a, task 2.4). The page plays each one from a .vrma built from web/character/src/
 * gestures.json; GestureContractTest keeps this list and that table the same. Idle breathing is not a gesture: the
 * page does it all the time.
 */
enum class Gesture(val wire: String) {
  NOD("nod"),
  SHAKE("shake"),
  WAVE("wave"),
  CLAP("clap"),
  JOY("joy"),
  YAWN("yawn"),
  STRETCH("stretch"),
  /** Folded arms: they sit below the home strip's frame, so only full-body views (Phase 3) use it. */
  POUT("pout"),
  /** Head-only sulk for the home strip (task 2.5): turns away with a small shrug, eyes shut a beat, glances back. */
  HUFF("huff");

  /**
   * This gesture as the home strip (head and shoulders) plays it: pout's folded arms fall below the frame, so a sulk is
   * a huff, and a clap is a joy (the user's picks in 2.5; script-bible §3).
   */
  fun onStrip(): Gesture =
    when (this) {
      POUT -> HUFF
      CLAP -> JOY
      else -> this
    }

  companion object {
    fun fromWire(wire: String): Gesture? = entries.firstOrNull { it.wire == wire }
  }
}

/**
 * When Rin gestures on the main screen until Phase 3 drives her (your choice, 2026-09-28): a greeting when the app
 * opens, then now and then a gesture that fits her mood. Only gestures that read from head and shoulders play here,
 * the strip's frame: clap is kept for missions (Phase 3), and pout's folded arms fall below the frame, so a sulk is a
 * huff (your pick, 2026-09-28).
 */
class GestureDirector(private val random: Random = Random.Default) {
  private var last: Gesture? = null

  /** A greeting when she first appears, and again when the app comes back after [GREET_AFTER_MS] or more away. */
  fun greetOnShow(mood: Mood, awayMs: Long?): Gesture? =
    if (awayMs == null || awayMs >= GREET_AFTER_MS) GREETING.getValue(mood).also { last = it } else null

  /** The next idle gesture for [mood], never the same one twice in a row when the mood has a choice. */
  fun idle(mood: Mood): Gesture {
    val choices = IDLE.getValue(mood)
    val pick = (choices - setOfNotNull(last)).ifEmpty { choices }.random(random)
    last = pick
    return pick
  }

  /** Milliseconds until the next idle gesture: 30–60 s. */
  fun nextIdleDelayMs(): Long = random.nextLong(IDLE_MIN_MS, IDLE_MAX_MS + 1)

  companion object {
    const val GREET_AFTER_MS = 5 * 60_000L
    const val IDLE_MIN_MS = 30_000L
    const val IDLE_MAX_MS = 60_000L

    val GREETING: Map<Mood, Gesture> =
      mapOf(
        Mood.SLEEPY to Gesture.YAWN,
        Mood.CHEERFUL to Gesture.WAVE,
        Mood.PROUD to Gesture.WAVE,
        Mood.RELIEVED to Gesture.WAVE,
        Mood.WORRIED to Gesture.NOD,
        Mood.POUTY to Gesture.HUFF,
        Mood.SULKY to Gesture.HUFF,
      )

    val IDLE: Map<Mood, List<Gesture>> =
      mapOf(
        Mood.SLEEPY to listOf(Gesture.YAWN, Gesture.STRETCH),
        Mood.CHEERFUL to listOf(Gesture.STRETCH, Gesture.NOD, Gesture.WAVE),
        Mood.PROUD to listOf(Gesture.NOD, Gesture.JOY),
        Mood.RELIEVED to listOf(Gesture.STRETCH, Gesture.NOD),
        Mood.WORRIED to listOf(Gesture.SHAKE, Gesture.NOD),
        Mood.POUTY to listOf(Gesture.HUFF, Gesture.SHAKE),
        Mood.SULKY to listOf(Gesture.HUFF),
      )
  }
}
