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
  POUT("pout");

  companion object {
    fun fromWire(wire: String): Gesture? = entries.firstOrNull { it.wire == wire }
  }
}

/**
 * When Rin gestures on the main screen until Phase 3 drives her (your choice, 2026-09-28): a greeting when the app
 * opens, then now and then a gesture that fits her mood. Clap is kept for missions (Phase 3), so it never plays here.
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
        Mood.POUTY to Gesture.POUT,
        Mood.SULKY to Gesture.POUT,
      )

    val IDLE: Map<Mood, List<Gesture>> =
      mapOf(
        Mood.SLEEPY to listOf(Gesture.YAWN, Gesture.STRETCH),
        Mood.CHEERFUL to listOf(Gesture.STRETCH, Gesture.NOD, Gesture.WAVE),
        Mood.PROUD to listOf(Gesture.NOD, Gesture.JOY),
        Mood.RELIEVED to listOf(Gesture.STRETCH, Gesture.NOD),
        Mood.WORRIED to listOf(Gesture.SHAKE, Gesture.NOD),
        Mood.POUTY to listOf(Gesture.POUT, Gesture.SHAKE),
        Mood.SULKY to listOf(Gesture.POUT),
      )
  }
}
