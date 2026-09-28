package io.github.earthkodyai.rinalarm.mission

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Walk [target] steps (user decision D15: 30), counted by [StepSensor] (our own accelerometer + gyroscope detector,
 * no runtime permission). A walking-rhythm bob still counts: the mission adds friction, it is not anti-cheat (plan 05c).
 */
class WalkMission(private val steps: StepSource, private val target: Int) : Mission {
  override val type = MissionType.WALK
  private val state = MutableStateFlow(MissionProgress(0, target))
  override val progress: StateFlow<MissionProgress> = state.asStateFlow()
  private var started = false

  override fun start() {
    if (started || state.value.state != MissionState.RUNNING) return
    started = true
    // Without both sensors the mission fails over to a plain Dismiss.
    if (!steps.start(::onStep)) state.value = state.value.copy(state = MissionState.FAILED)
  }

  override fun stop() {
    if (!started) return
    started = false
    steps.stop()
  }

  private fun onStep(total: Int) {
    if (state.value.state != MissionState.RUNNING) return
    val done = total.coerceAtMost(target)
    state.value = MissionProgress(done, target, if (done >= target) MissionState.PASSED else MissionState.RUNNING)
    if (done >= target) stop()
  }
}
