package io.github.earthkodyai.rinalarm.mission

import kotlinx.coroutines.flow.StateFlow

/**
 * One running mission (plan 05c: start / progress / verify / fallback). [start] begins sensing, [progress] reports
 * each step towards [MissionProgress.target], and the mission verifies itself: it reaches [MissionState.PASSED] only
 * on real sensor input. [MissionState.FAILED] (the sensor stopped, the camera was taken) asks the ring screen to fall
 * back to a plain Dismiss, logged as MISSION_FAILED.
 */
interface Mission {
  val type: MissionType
  val progress: StateFlow<MissionProgress>

  fun start()

  /** Releases sensors or the camera. Safe to call more than once. */
  fun stop()
}

enum class MissionState {
  RUNNING,
  PASSED,
  FAILED,
}

data class MissionProgress(val done: Int, val target: Int, val state: MissionState = MissionState.RUNNING) {
  val fraction: Float
    get() = if (target <= 0) 1f else (done.toFloat() / target).coerceIn(0f, 1f)
}
