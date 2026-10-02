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

  /**
   * This gesture as the ring screen (full body) plays it: a sulk is a huff there too. The user's own Rin folds her
   * arms through her body and her flared skirt, on the way in and while folded, and no keyframe tuning cleared it
   * without the pose turning into arms held straight out (2026-10-01, the user's pick). Pout stays for other models.
   */
  fun onRing(): Gesture = if (this == POUT) HUFF else this

  /**
   * Head and shoulders only: what she may still do while a game is on (G.1, the user). Her arms are in the game: her
   * hand plays the pads (and anything else rose over the board), her hands shuffle the cups (a stretch there left the
   * cups and came back), and Repeat after Rin's sentence sits over her chest.
   */
  val armsStill: Boolean
    get() = this == NOD || this == SHAKE || this == HUFF

  companion object {
    fun fromWire(wire: String): Gesture? = entries.firstOrNull { it.wire == wire }
  }
}

/**
 * Never the same gesture twice in a row (the user, G.1: a stall's huff, then an idle huff 3 s later, read as nagging):
 * a gesture that repeats the one before within [REPEAT_GAP_MS] is dropped, whoever asked for it (idle, a line, a game).
 */
class GestureGate {
  private var last: Gesture? = null
  private var lastAt = Long.MIN_VALUE / 2

  /** Whether [gesture] may play at [nowMs]; if so, it becomes the last one. */
  fun allow(gesture: Gesture, nowMs: Long): Boolean {
    if (gesture == last && nowMs - lastAt < REPEAT_GAP_MS) return false
    last = gesture
    lastAt = nowMs
    return true
  }

  companion object {
    /** Longer than her slowest gesture plus a beat, shorter than the gap between two misses a minute apart. */
    const val REPEAT_GAP_MS = 20_000L
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

  /** The next idle gesture while a game is on: one of [idle]'s that keeps her arms still, or null for none this time. */
  fun idleInGame(mood: Mood): Gesture? = idle(mood).takeIf { it.armsStill }

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
