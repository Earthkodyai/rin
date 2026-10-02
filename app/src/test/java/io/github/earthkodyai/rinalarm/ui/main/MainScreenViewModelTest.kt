package io.github.earthkodyai.rinalarm.ui.main

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.data.AlarmRepository
import io.github.earthkodyai.rinalarm.data.DayMode
import io.github.earthkodyai.rinalarm.data.DayModeKind
import io.github.earthkodyai.rinalarm.testing.FakeAlarms
import io.github.earthkodyai.rinalarm.testing.FakeDeviceStatus
import io.github.earthkodyai.rinalarm.testing.FakeSettings
import io.github.earthkodyai.rinalarm.testing.FixedTimeSource
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
  fun theTour_waitsWhenAsked_andEndingItClearsIt() = runTest {
    val alarms = FakeAlarms()
    val settings = FakeSettings()
    val viewModel = MainScreenViewModel(alarms, alarms, time, FakeDeviceStatus(), settings)
    backgroundScope.launch { viewModel.tourPending.collect {} }
    assertFalse(viewModel.tourPending.value)
    settings.setTutorialPending(true)
    assertTrue(viewModel.tourPending.first { it })
    viewModel.endTour()
    assertFalse(viewModel.tourPending.first { !it })
    assertFalse(settings.tutorialPending.value)
  }

  @Test
  fun tourSteps_skipTheAlarmStep_whenTheListIsEmpty() {
    assertEquals(
      listOf(TourStep.HELLO, TourStep.ADD, TourStep.DAY_MODE, TourStep.TOP, TourStep.GAMES),
      TourStep.steps(hasAlarms = false),
    )
    assertEquals(TourStep.entries, TourStep.steps(hasAlarms = true))
    assertEquals(TourStep.GAMES, TourStep.steps(hasAlarms = true).last())
  }

  @Test
  fun uiState_startsLoading() {
    val alarms = FakeAlarms()
    val viewModel = MainScreenViewModel(alarms, alarms, time, FakeDeviceStatus(), FakeSettings())
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun uiState_listsAlarmsWithTheirNextRing() = runTest {
    val daily = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = RepeatDays.EVERY_DAY)
    val off = Alarm(id = 2, time = LocalTime.of(9, 0), enabled = false)
    val alarms = FakeAlarms(listOf(daily, off))
    val viewModel = MainScreenViewModel(alarms, alarms, time, FakeDeviceStatus(), FakeSettings())

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
    val viewModel = MainScreenViewModel(failing, alarms, time, FakeDeviceStatus(), FakeSettings())

    assertTrue(viewModel.uiState.first { it !is MainScreenUiState.Loading } is MainScreenUiState.Error)
  }

  @Test
  fun dayMode_oneTapSetsIt_aTapOnTheSameCancels_andTheOtherSwitches() = runTest {
    val alarms = FakeAlarms()
    val settings = FakeSettings()
    val viewModel = MainScreenViewModel(alarms, alarms, time, FakeDeviceStatus(), settings)
    backgroundScope.launch { viewModel.dayMode.collect {} }

    viewModel.tapDayMode(DayModeKind.REST)
    assertEquals(DayModeKind.REST, viewModel.dayMode.first { it != null })
    assertEquals(DayMode(DayModeKind.REST, time.now().toEpochMilli()), settings.dayMode.value)

    viewModel.tapDayMode(DayModeKind.SICK)
    assertEquals(DayModeKind.SICK, viewModel.dayMode.first { it == DayModeKind.SICK })

    viewModel.tapDayMode(DayModeKind.SICK)
    assertNull(viewModel.dayMode.first { it == null })
    assertNull(settings.dayMode.value)
  }

  @Test
  fun dayMode_aLapsedOne_showsAsNone() = runTest {
    val alarms = FakeAlarms()
    val lapsed = DayMode(DayModeKind.SICK, time.now().toEpochMilli() - DayMode.LIFETIME.toMillis())
    val viewModel = MainScreenViewModel(alarms, alarms, time, FakeDeviceStatus(), FakeSettings(dayMode = lapsed))
    backgroundScope.launch { viewModel.dayMode.collect {} }
    assertNull(viewModel.dayMode.first())
  }

  @Test
  fun setEnabled_goesThroughTheWriter_andTheListFollows() = runTest {
    val alarms = FakeAlarms(listOf(Alarm(id = 1, time = LocalTime.of(7, 0))))
    val viewModel = MainScreenViewModel(alarms, alarms, time, FakeDeviceStatus(), FakeSettings())
    viewModel.uiState.first { it is MainScreenUiState.Success }

    viewModel.setEnabled(1, false)

    val row = (viewModel.uiState.first { state -> state is MainScreenUiState.Success && !state.alarms[0].alarm.enabled }
        as MainScreenUiState.Success)
      .alarms
      .single()
    assertFalse(row.alarm.enabled)
    assertNull(row.nextRing)
  }

  @Test
  fun uiState_hidesTheTestAlarm() = runTest {
    val alarms = FakeAlarms(listOf(Alarm(id = 1, time = LocalTime.of(7, 0))))
    alarms.scheduleTest("Test alarm")
    val viewModel = MainScreenViewModel(alarms, alarms, time, FakeDeviceStatus(), FakeSettings())

    val state = viewModel.uiState.first { it is MainScreenUiState.Success } as MainScreenUiState.Success

    assertEquals(listOf(1L), state.alarms.map { it.alarm.id })
  }

  @Test
  fun setupIssue_followsTheCriticalChecks_onEachRefresh() {
    val device = FakeDeviceStatus()
    val alarms = FakeAlarms()
    val viewModel = MainScreenViewModel(alarms, alarms, time, device, FakeSettings())

    viewModel.refreshSetup()
    assertFalse(viewModel.setupIssue.value)

    device.status = device.status.copy(notificationsAllowed = false)
    viewModel.refreshSetup()
    assertTrue(viewModel.setupIssue.value)

    // Full-screen off hides the ring behind the lock screen: a banner too (2026-10-01, was a warning in 1.4).
    device.status = FakeDeviceStatus.ALL_GOOD.copy(fullScreenAllowed = false)
    viewModel.refreshSetup()
    assertTrue(viewModel.setupIssue.value)

    // A warning alone (no game can run; alarms still ring and show) is not worth a permanent banner.
    device.status = FakeDeviceStatus.ALL_GOOD.copy(readyMissions = emptyList())
    viewModel.refreshSetup()
    assertFalse(viewModel.setupIssue.value)
  }
}
