package io.github.earthkodyai.rinalarm.ui.diagnostics

import io.github.earthkodyai.rinalarm.alarm.log.RingHistoryRepository
import io.github.earthkodyai.rinalarm.alarm.log.RingOutcome
import io.github.earthkodyai.rinalarm.alarm.log.RingSummary
import io.github.earthkodyai.rinalarm.setup.CheckId
import io.github.earthkodyai.rinalarm.setup.Severity
import io.github.earthkodyai.rinalarm.testing.FakeAlarms
import io.github.earthkodyai.rinalarm.testing.FakeDeviceStatus
import io.github.earthkodyai.rinalarm.testing.FixedTimeSource
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiagnosticsViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val zone = ZoneId.of("Asia/Bangkok")
  private val now = LocalDateTime.parse("2026-09-28T06:00").atZone(zone).toInstant()
  private val time = FixedTimeSource(now, zone)
  private val device = FakeDeviceStatus()
  private val alarms = FakeAlarms()
  private val rings = MutableStateFlow<List<RingSummary>>(emptyList())
  private val history = object : RingHistoryRepository { override val recentRings = rings }

  private fun viewModel() = DiagnosticsViewModel(device, alarms, alarms, history, { "screen=off dnd=off" }, time)

  @Test
  fun refresh_rereadsThePhone() = runTest {
    val vm = viewModel()
    device.status = device.status.copy(notificationsAllowed = false)
    vm.refresh()

    val state = vm.uiState.first { !it.status.notificationsAllowed }
    assertEquals(Severity.CRITICAL, state.checks.first { it.id == CheckId.NOTIFICATIONS }.severity)
  }

  @Test
  fun testAlarm_pending_thenCancelled() = runTest {
    val vm = viewModel()
    vm.startTest("Test alarm")
    assertEquals("2026-09-28T06:02+07:00", vm.uiState.first { it.testRingAt != null }.testRingAt?.toOffsetDateTime().toString())

    vm.cancelTest()
    assertNull(vm.uiState.first { it.testRingAt == null }.testRingAt)
  }

  @Test
  fun testAlarm_failure_isShown() = runTest {
    alarms.failure = IllegalStateException("disk full")
    val vm = viewModel()
    vm.startTest("Test alarm")
    assertTrue(vm.uiState.first { it.testFailed }.testFailed)
  }

  @Test
  fun report_holdsTheChecks_theSnapshot_andTheRings() = runTest {
    rings.value =
      listOf(
        RingSummary(3, now, now.plusMillis(52), Duration.ofMillis(52), Duration.ofMillis(900), RingOutcome.DISMISSED, true)
      )
    device.status = device.status.copy(fullScreenAllowed = false)
    val vm = viewModel()
    vm.refresh()
    vm.uiState.first { it.recentRings.isNotEmpty() && it.status.fullScreenAllowed == false }

    val report = vm.report()

    assertTrue(report, report.startsWith("RinAlarm 0.1.0 diagnostics, 2026-09-28 06:00:00 (Asia/Bangkok)"))
    assertTrue(report, "- FULL_SCREEN: WARNING" in report)
    assertTrue(report, "Now: screen=off dnd=off" in report)
    assertTrue(report, "- 2026-09-28 06:00:00 alarm=3 DISMISSED late=52ms toSound=900ms test" in report)
  }
}
