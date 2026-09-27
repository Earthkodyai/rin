package io.github.earthkodyai.rinalarm.ui.main

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.data.AlarmRepository
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainScreenViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val zone = ZoneId.of("Asia/Bangkok")
  private val now = LocalDateTime.parse("2026-09-28T06:00").atZone(zone).toInstant()

  @Test
  fun uiState_startsLoading() {
    val viewModel = MainScreenViewModel(FakeAlarmRepository(), FixedTimeSource(now, zone))
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun uiState_listsAlarmsWithTheirNextRing() = runTest {
    val daily = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = RepeatDays.EVERY_DAY)
    val off = Alarm(id = 2, time = LocalTime.of(9, 0), enabled = false)
    val viewModel = MainScreenViewModel(FakeAlarmRepository(listOf(daily, off)), FixedTimeSource(now, zone))

    val state = viewModel.uiState.first { it is MainScreenUiState.Success } as MainScreenUiState.Success

    assertEquals(listOf(daily, off), state.alarms.map { it.alarm })
    assertEquals("2026-09-28T07:00+07:00", state.alarms[0].nextRing?.toOffsetDateTime().toString())
    assertNull(state.alarms[1].nextRing)
  }

  @Test
  fun uiState_whenTheRepositoryFails_isError() = runTest {
    val failing =
      object : AlarmRepository by FakeAlarmRepository() {
        override val alarms: Flow<List<Alarm>> = flow { error("disk full") }
      }
    val viewModel = MainScreenViewModel(failing, FixedTimeSource(now, zone))

    assertTrue(viewModel.uiState.first { it !is MainScreenUiState.Loading } is MainScreenUiState.Error)
  }
}

private class FakeAlarmRepository(initial: List<Alarm> = emptyList()) : AlarmRepository {
  private val state = MutableStateFlow(initial)
  override val alarms: Flow<List<Alarm>> = state

  override suspend fun save(alarm: Alarm): Long {
    state.value = state.value.filterNot { it.id == alarm.id } + alarm
    return alarm.id
  }

  override suspend fun delete(id: Long) {
    state.value = state.value.filterNot { it.id == id }
  }
}

private class FixedTimeSource(private val now: Instant, private val zone: ZoneId) : TimeSource {
  override fun now(): Instant = now

  override fun zone(): ZoneId = zone

  override val minuteTicks: Flow<Unit> = flowOf(Unit)
}
