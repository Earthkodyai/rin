package io.github.earthkodyai.rinalarm.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmWriter
import io.github.earthkodyai.rinalarm.alarm.engine.DeviceStateProbe
import io.github.earthkodyai.rinalarm.alarm.log.RingHistoryRepository
import io.github.earthkodyai.rinalarm.alarm.log.RingSummary
import io.github.earthkodyai.rinalarm.data.AlarmRepository
import io.github.earthkodyai.rinalarm.setup.CheckResult
import io.github.earthkodyai.rinalarm.setup.DeviceStatus
import io.github.earthkodyai.rinalarm.setup.DeviceStatusSource
import io.github.earthkodyai.rinalarm.setup.DiagnosticsReport
import io.github.earthkodyai.rinalarm.setup.SetupChecks
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DiagnosticsViewModel
@Inject
constructor(
  private val statusSource: DeviceStatusSource,
  alarms: AlarmRepository,
  private val writer: AlarmWriter,
  ringHistory: RingHistoryRepository,
  private val deviceState: DeviceStateProbe,
  private val time: TimeSource,
) : ViewModel() {
  private val status = MutableStateFlow(statusSource.read())
  private val testFailed = MutableStateFlow(false)

  val uiState: StateFlow<DiagnosticsUiState> =
    combine(status, alarms.testAlarm, ringHistory.recentRings, testFailed) { status, test, rings, failed ->
        val zone = time.zone()
        DiagnosticsUiState(
          status = status,
          checks = SetupChecks.evaluate(status),
          testRingAt = test?.nextTrigger(time.now(), zone)?.atZone(zone),
          testFailed = failed,
          recentRings = rings,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiagnosticsUiState.initial(status.value))

  /** Re-reads the phone's state; called on every resume, since the fixes happen in Settings. */
  fun refresh() {
    status.value = statusSource.read()
  }

  fun startTest(label: String) {
    viewModelScope.launch {
      testFailed.value = runCatching { writer.scheduleTest(label) }.isFailure
    }
  }

  fun cancelTest() {
    viewModelScope.launch { runCatching { writer.cancelTest() } }
  }

  /** The "Copy report" text, built from the state on screen plus a fresh device snapshot. */
  fun report(): String {
    val state = uiState.value
    return DiagnosticsReport.build(
      state.status,
      state.checks,
      deviceState.snapshot(),
      state.recentRings,
      time.now(),
      time.zone(),
    )
  }
}

data class DiagnosticsUiState(
  val status: DeviceStatus,
  val checks: List<CheckResult>,
  /** When the test alarm rings, or null when none is waiting. */
  val testRingAt: ZonedDateTime?,
  val testFailed: Boolean,
  val recentRings: List<RingSummary>,
) {
  companion object {
    fun initial(status: DeviceStatus) = DiagnosticsUiState(status, SetupChecks.evaluate(status), null, false, emptyList())
  }
}
