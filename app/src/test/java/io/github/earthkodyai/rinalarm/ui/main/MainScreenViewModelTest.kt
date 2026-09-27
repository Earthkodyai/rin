package io.github.earthkodyai.rinalarm.ui.main

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.data.AlarmRepository
import io.github.earthkodyai.rinalarm.testing.FakeAlarms
import io.github.earthkodyai.rinalarm.testing.FixedTimeSource
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainScreenViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val zone = ZoneId.of("Asia/Bangkok")
  private val time = FixedTimeSource(LocalDateTime.parse("2026-09-28T06:00").atZone(zone).toInstant(), zone)

  @Test
  fun uiState_startsLoading() {
    val alarms = FakeAlarms()
    val viewModel = MainScreenViewModel(alarms, alarms, time)
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun uiState_listsAlarmsWithTheirNextRing() = runTest {
    val daily = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = RepeatDays.EVERY_DAY)
    val off = Alarm(id = 2, time = LocalTime.of(9, 0), enabled = false)
    val alarms = FakeAlarms(listOf(daily, off))
    val viewModel = MainScreenViewModel(alarms, alarms, time)

    val state = viewModel.uiState.first { it is MainScreenUiState.Success } as MainScreenUiState.Success

    assertEquals(listOf(daily, off), state.alarms.map { it.alarm })
    assertEquals("2026-09-28T07:00+07:00", state.alarms[0].nextRing?.toOffsetDateTime().toString())
    assertNull(state.alarms[1].nextRing)
  }

  @Test
  fun uiState_whenTheRepositoryFails_isError() = runTest {
    val alarms = FakeAlarms()
    val failing =
      object : AlarmRepository by alarms {
        override val alarms: Flow<List<Alarm>> = flow { error("disk full") }
      }
    val viewModel = MainScreenViewModel(failing, alarms, time)

    assertTrue(viewModel.uiState.first { it !is MainScreenUiState.Loading } is MainScreenUiState.Error)
  }

  @Test
  fun setEnabled_goesThroughTheWriter_andTheListFollows() = runTest {
    val alarms = FakeAlarms(listOf(Alarm(id = 1, time = LocalTime.of(7, 0))))
    val viewModel = MainScreenViewModel(alarms, alarms, time)
    viewModel.uiState.first { it is MainScreenUiState.Success }

    viewModel.setEnabled(1, false)

    val row = (viewModel.uiState.first { state -> state is MainScreenUiState.Success && !state.alarms[0].alarm.enabled }
        as MainScreenUiState.Success)
      .alarms
      .single()
    assertFalse(row.alarm.enabled)
    assertNull(row.nextRing)
  }
}
