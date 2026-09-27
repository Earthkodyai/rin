package io.github.earthkodyai.rinalarm.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmWriter
import io.github.earthkodyai.rinalarm.data.AlarmRepository
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainScreenViewModel
@Inject
constructor(alarmRepository: AlarmRepository, private val writer: AlarmWriter, timeSource: TimeSource) : ViewModel() {
  val uiState: StateFlow<MainScreenUiState> =
    combine<List<Alarm>, Unit, MainScreenUiState>(alarmRepository.alarms, timeSource.minuteTicks) { alarms, _ ->
        val now = timeSource.now()
        val zone = timeSource.zone()
        val rows = alarms.map { AlarmRow(it, it.nextTrigger(now, zone)?.atZone(zone)) }
        MainScreenUiState.Success(rows)
      }
      .catch { emit(MainScreenUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

  /** The list's on/off switch. The engine re-reads the alarm, so a stale row cannot overwrite a newer edit. */
  fun setEnabled(alarmId: Long, enabled: Boolean) {
    viewModelScope.launch { writer.setEnabled(alarmId, enabled) }
  }
}

/** An alarm plus when it rings next in the current zone (null while switched off). */
data class AlarmRow(val alarm: Alarm, val nextRing: ZonedDateTime?)

sealed interface MainScreenUiState {
  data object Loading : MainScreenUiState

  data class Error(val throwable: Throwable) : MainScreenUiState

  data class Success(val alarms: List<AlarmRow>) : MainScreenUiState
}
