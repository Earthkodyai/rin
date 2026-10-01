package io.github.earthkodyai.rinalarm.mission

import kotlinx.coroutines.flow.StateFlow

/**
 * One running mission (plan 05c: start / progress / verify / fallback). [start] begins sensing, [progress] reports
 * each step towards [MissionProgress.target], and the mission verifies itself: it reaches [MissionState.PASSED] only
 * on real input. [MissionState.FAILED] (the microphone was taken, the speech model failed) asks the ring screen to fall
 * back to a plain Dismiss, logged as MISSION_FAILED.
 */
interface Mission {
  val type: MissionType
  val progress: StateFlow<MissionProgress>

  fun start()

  /** Releases the microphone, timers and sounds. Safe to call more than once. */
  fun stop()

  /** Extra key=value pairs for the MISSION_PASSED / MISSION_FAILED log row (the numbers a later tuning needs). */
  fun summary(): String = ""
}

enum class MissionState {
  RUNNING,
  PASSED,
  FAILED,
}

/**
 * @property activity counts signs that the user is working on it without moving [done] (a tap, a word heard). Like a
 *   rise in [done], a rise here quiets the tone (RingPolicy.missionQuiet).
 */
data class MissionProgress(
  val done: Int,
  val target: Int,
  val state: MissionState = MissionState.RUNNING,
  val activity: Int = 0,
) {
  val fraction: Float
    get() = if (target <= 0) 1f else (done.toFloat() / target).coerceIn(0f, 1f)
}
