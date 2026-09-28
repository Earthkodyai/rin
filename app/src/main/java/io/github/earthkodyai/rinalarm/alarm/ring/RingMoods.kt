package io.github.earthkodyai.rinalarm.alarm.ring

import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.character.Mood

/** Where a ring stands, as far as Rin's face goes. */
enum class RingPhase {
  /** Ringing, nothing done yet (or no mission). */
  WAKING,
  /** Mission progress within RingPolicy.MISSION_IDLE: the tone is quiet. */
  WORKING,
  /** Progress was made, then stopped long enough for the tone to come back. */
  STALLED,
  /** Mission passed: the alarm is off. */
  PASSED,
}

/**
 * Rin on the ring screen until the Phase 5 emotion engine drives her: a mood per [RingPhase] and a gesture when she
 * enters it. Full body, so clap and pout show here (task 2.5 kept them off the home strip).
 */
object RingMoods {
  fun mood(phase: RingPhase): Mood =
    when (phase) {
      RingPhase.WAKING -> Mood.CHEERFUL
      RingPhase.WORKING -> Mood.CHEERFUL
      RingPhase.STALLED -> Mood.POUTY
      RingPhase.PASSED -> Mood.PROUD
    }

  fun cue(phase: RingPhase): Gesture =
    when (phase) {
      RingPhase.WAKING -> Gesture.WAVE
      RingPhase.WORKING -> Gesture.NOD
      RingPhase.STALLED -> Gesture.POUT
      RingPhase.PASSED -> Gesture.CLAP
    }

  /** The phase from what RingState knows. [lastProgressMs] null = no progress yet in this ring. */
  fun phase(passed: Boolean, nowMs: Long, lastProgressMs: Long?): RingPhase =
    when {
      passed -> RingPhase.PASSED
      lastProgressMs == null -> RingPhase.WAKING
      RingPolicy.missionQuiet(nowMs, lastProgressMs) -> RingPhase.WORKING
      else -> RingPhase.STALLED
    }
}
