package io.github.earthkodyai.rinalarm.alarm.ring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType
import io.github.earthkodyai.rinalarm.alarm.log.RingLog
import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.di.AppScope
import io.github.earthkodyai.rinalarm.mission.Mission
import io.github.earthkodyai.rinalarm.mission.MissionFactory
import io.github.earthkodyai.rinalarm.mission.MissionProgress
import io.github.earthkodyai.rinalarm.mission.MissionState
import io.github.earthkodyai.rinalarm.time.ElapsedClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The ring screen's side of a ring (task 3.1): runs the mission RingService planned, reports each bit of progress to
 * [RingState] (the service quiets the tone), and asks the service to stop once the mission passes. It never stops
 * the ring by itself otherwise: the service owns the ring, and this screen may never show (08-error-handling).
 *
 * [commands] carries what the activity must send to RingService (it holds the Context); the ring screen stays on for
 * [CELEBRATE_MS] after a pass so Rin can clap, then [RingUiState.finished] closes it.
 */
@HiltViewModel
class RingViewModel
@Inject
constructor(
  private val ringState: RingState,
  private val missions: MissionFactory,
  private val ringLog: RingLog,
  @AppScope private val appScope: CoroutineScope,
  private val clock: ElapsedClock,
) : ViewModel() {
  private val state = MutableStateFlow(RingUiState())
  val uiState: StateFlow<RingUiState> = state.asStateFlow()

  private val commandFlow = MutableSharedFlow<RingCommand>(extraBufferCapacity = 4)
  val commands: SharedFlow<RingCommand> = commandFlow.asSharedFlow()

  private val cueFlow = MutableSharedFlow<Gesture>(extraBufferCapacity = 2)
  /** Gestures for Rin when the phase changes (RingMoods.cue). */
  val cues: SharedFlow<Gesture> = cueFlow.asSharedFlow()

  private var mission: Mission? = null
  private var missionJob: Job? = null
  private var stallJob: Job? = null
  private var ringKey: Any? = null
  private var missionStartedAt = 0L

  init {
    viewModelScope.launch {
      ringState.active.collect { ring ->
        if (ring == null) onRingEnded() else onRing(ring)
      }
    }
  }

  private fun onRing(ring: ActiveRing) {
    val key = ring.request.let { Triple(it.alarmId, it.scheduledAt, it.snoozeCount) }
    if (key == ringKey) {
      state.update { it.copy(ring = ring) }
      return
    }
    ringKey = key
    stopMission()
    state.value = RingUiState(ring = ring)
    refreshPhase()
    val plan = ring.mission ?: return
    val running = runCatching { missions.create(plan.type).also(Mission::start) }.getOrNull()
    if (running == null) {
      state.update { it.copy(missionFailed = true) }
      log(RingEventType.MISSION_FAILED, ring, "type=${plan.type.stored} reason=create_failed")
      return
    }
    mission = running
    missionStartedAt = clock.now()
    log(
      RingEventType.MISSION_STARTED,
      ring,
      "type=${plan.type.stored} target=${running.progress.value.target}" +
        (plan.switchedFrom?.let { " switchedFrom=${it.stored}" } ?: ""),
    )
    missionJob = viewModelScope.launch { running.progress.collect { onProgress(ring, it) } }
  }

  private fun onProgress(ring: ActiveRing, progress: MissionProgress) {
    val before = state.value.progress
    state.update { it.copy(progress = progress) }
    if (before != null && progress.done > before.done) {
      ringState.reportProgress(clock.now())
      refreshPhase()
      // No event marks the idle timeout, so a timer does: STALLED arrives when progress simply stops. Each step
      // restarts it; a one-shot rather than a ticker, so nothing keeps running once the ring is over.
      stallJob?.cancel()
      stallJob =
        viewModelScope.launch {
          delay(RingPolicy.MISSION_IDLE.toMillis())
          refreshPhase()
        }
    }
    when (progress.state) {
      MissionState.RUNNING -> Unit
      MissionState.PASSED -> {
        if (state.value.passed) return
        state.update { it.copy(passed = true) }
        log(
          RingEventType.MISSION_PASSED,
          ring,
          "type=${mission?.type?.stored} done=${progress.done}/${progress.target} tookMs=${clock.now() - missionStartedAt}",
        )
        stopMission()
        refreshPhase()
        commandFlow.tryEmit(RingCommand.Dismiss(RingService.SOURCE_MISSION))
      }
      MissionState.FAILED -> {
        state.update { it.copy(missionFailed = true) }
        log(RingEventType.MISSION_FAILED, ring, "type=${mission?.type?.stored} done=${progress.done}/${progress.target}")
        stopMission()
      }
    }
  }

  private fun onRingEnded() {
    stopMission()
    if (state.value.ring == null) return
    if (!state.value.passed) {
      state.update { it.copy(finished = true) }
      return
    }
    viewModelScope.launch {
      delay(CELEBRATE_MS)
      state.update { it.copy(finished = true) }
    }
  }

  private fun refreshPhase() {
    val current = state.value
    if (current.ring == null) return
    val phase = RingMoods.phase(current.passed, clock.now(), ringState.lastProgressAt)
    if (phase == current.phase) return
    state.update { it.copy(phase = phase) }
    cueFlow.tryEmit(RingMoods.cue(phase))
  }

  fun snooze() = commandFlow.tryEmit(RingCommand.Snooze)

  /** The plain Dismiss: only offered when there is no mission, or it failed. */
  fun dismiss() = commandFlow.tryEmit(RingCommand.Dismiss(RingService.SOURCE_SCREEN))

  fun emergencyStop() = commandFlow.tryEmit(RingCommand.Dismiss(RingService.SOURCE_EMERGENCY))

  private fun stopMission() {
    missionJob?.cancel()
    missionJob = null
    stallJob?.cancel()
    stallJob = null
    mission?.stop()
    mission = null
  }

  override fun onCleared() {
    stopMission()
  }

  private fun log(type: RingEventType, ring: ActiveRing, detail: String) {
    // App scope: a pass logs and then the screen closes; the row must still land.
    appScope.launch { ringLog.record(type, ring.request.alarmId, ring.request.scheduledAt, detail) }
  }

  companion object {
    /** How long Rin claps after a pass before the ring screen closes. */
    const val CELEBRATE_MS = 2_500L  }
}

/**
 * @property ring the ring on screen; kept after it ends so the celebration still shows the time and label.
 * @property progress the mission's, or null when there is no mission (plain Dismiss).
 * @property missionFailed the mission broke; a plain Dismiss takes over.
 * @property finished close the screen.
 */
data class RingUiState(
  val ring: ActiveRing? = null,
  val progress: MissionProgress? = null,
  val phase: RingPhase = RingPhase.WAKING,
  val passed: Boolean = false,
  val missionFailed: Boolean = false,
  val finished: Boolean = false,
) {
  /** A plain Dismiss button instead of the mission and the emergency hold. */
  val plainDismiss: Boolean
    get() = ring?.mission == null || missionFailed
}

sealed interface RingCommand {
  data object Snooze : RingCommand

  data class Dismiss(val source: String) : RingCommand
}
