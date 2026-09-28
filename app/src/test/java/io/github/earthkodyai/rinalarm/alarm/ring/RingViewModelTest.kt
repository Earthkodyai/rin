package io.github.earthkodyai.rinalarm.alarm.ring

import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType
import io.github.earthkodyai.rinalarm.alarm.log.RingLog
import io.github.earthkodyai.rinalarm.mission.Mission
import io.github.earthkodyai.rinalarm.mission.MissionPlan
import io.github.earthkodyai.rinalarm.mission.MissionProgress
import io.github.earthkodyai.rinalarm.mission.MissionState
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.CodeFormat
import io.github.earthkodyai.rinalarm.mission.QrScanPolicy
import io.github.earthkodyai.rinalarm.mission.ScanMission
import io.github.earthkodyai.rinalarm.mission.ScanVerdict
import io.github.earthkodyai.rinalarm.mission.SeenCode
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RingViewModelTest {
  @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

  private val ringState = RingState()
  private val log = RecordingLog()
  private val mission = FakeMission()
  private var clockMs = 1_000L
  private val request =
    RingRequest(4, LocalTime.of(6, 30), "Work", Instant.parse("2026-09-27T23:30:00Z"), 0, 3, late = false, RingOptions())

  private fun TestScope.ringScreen(): Pair<RingViewModel, MutableList<RingCommand>> {
    val viewModel = RingViewModel(ringState, { mission }, log, backgroundScope) { clockMs }
    val commands = mutableListOf<RingCommand>()
    backgroundScope.launch { viewModel.commands.collect { commands += it } }
    runCurrent()
    return viewModel to commands
  }

  @Test
  fun passingTheMission_stopsTheRing_thenClosesAfterTheCelebration() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.WALK)))
      val (viewModel, commands) = ringScreen()
      assertEquals(1, mission.starts)
      assertFalse(viewModel.uiState.value.plainDismiss)

      clockMs = 5_000
      mission.state.value = MissionProgress(12, 30)
      runCurrent()
      assertEquals(5_000L, ringState.lastProgressAt)
      assertEquals(RingPhase.WORKING, viewModel.uiState.value.phase)

      mission.state.value = MissionProgress(30, 30, MissionState.PASSED)
      runCurrent()
      assertEquals(listOf(RingCommand.Dismiss(RingService.SOURCE_MISSION)), commands)
      assertTrue(viewModel.uiState.value.passed)
      assertEquals(listOf(RingEventType.MISSION_STARTED, RingEventType.MISSION_PASSED), log.types)
      assertTrue(mission.stopped)

      // RingService stops the ring; Rin claps before the screen closes.
      ringState.set(null)
      runCurrent()
      assertFalse(viewModel.uiState.value.finished)
      advanceTimeBy(RingViewModel.CELEBRATE_MS + 1)
      assertTrue(viewModel.uiState.value.finished)
    }

  @Test
  fun noMission_offersThePlainDismiss_andClosesAsSoonAsTheRingEnds() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, mission = null))
      val (viewModel, commands) = ringScreen()
      assertEquals(0, mission.starts)
      assertTrue(viewModel.uiState.value.plainDismiss)

      viewModel.dismiss()
      runCurrent()
      assertEquals(listOf(RingCommand.Dismiss(RingService.SOURCE_SCREEN)), commands)

      ringState.set(null)
      runCurrent()
      assertTrue(viewModel.uiState.value.finished)
    }

  @Test
  fun aBrokenMission_fallsBackToThePlainDismiss() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.WALK)))
      val (viewModel, _) = ringScreen()

      mission.state.value = MissionProgress(3, 30, MissionState.FAILED)
      runCurrent()

      assertTrue(viewModel.uiState.value.plainDismiss)
      assertEquals(listOf(RingEventType.MISSION_STARTED, RingEventType.MISSION_FAILED), log.types)
    }

  @Test
  fun stoppingProgress_makesHerPout_andTheEmergencyHoldSaysSo() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.WALK)))
      val (viewModel, commands) = ringScreen()
      mission.state.value = MissionProgress(5, 30)
      runCurrent()

      advanceTimeBy(RingPolicy.MISSION_IDLE.toMillis() - 1)
      assertEquals(RingPhase.WORKING, viewModel.uiState.value.phase)
      clockMs += RingPolicy.MISSION_IDLE.toMillis()
      advanceTimeBy(2)
      assertEquals(RingPhase.STALLED, viewModel.uiState.value.phase)

      viewModel.emergencyStop()
      runCurrent()
      assertEquals(listOf(RingCommand.Dismiss(RingService.SOURCE_EMERGENCY)), commands)
    }

  // --- QR (task 3.2) ---

  private val scan = FakeScanMission()

  private fun TestScope.qrRingScreen(): Pair<RingViewModel, MutableList<RingCommand>> {
    ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.QR)))
    val viewModel = RingViewModel(ringState, { scan }, log, backgroundScope) { clockMs }
    val commands = mutableListOf<RingCommand>()
    backgroundScope.launch { viewModel.commands.collect { commands += it } }
    runCurrent()
    return viewModel to commands
  }

  @Test
  fun qr_theCameraOpensOnlyOnATap_andClosesItselfWhenIdle() =
    runTest(main.dispatcher) {
      val (viewModel, _) = qrRingScreen()
      assertFalse(viewModel.uiState.value.cameraOpen)
      assertEquals(0, scan.opens)

      viewModel.openCamera()
      runCurrent()
      assertTrue(viewModel.uiState.value.cameraOpen)
      assertEquals(1, scan.opens)

      // A step on the way restarts the idle timer.
      advanceTimeBy(QrScanPolicy.CAMERA_IDLE.toMillis() - 1_000)
      scan.state.value = MissionProgress(0, 1, activity = 1)
      runCurrent()
      advanceTimeBy(QrScanPolicy.CAMERA_IDLE.toMillis() - 1_000)
      assertTrue(viewModel.uiState.value.cameraOpen)
      advanceTimeBy(2_000)
      assertFalse(viewModel.uiState.value.cameraOpen)

      viewModel.openCamera()
      runCurrent()
      assertEquals(2, scan.opens)
    }

  @Test
  fun qr_activityQuietsTheTone_likeProgressDoes() =
    runTest(main.dispatcher) {
      val (viewModel, _) = qrRingScreen()
      clockMs = 7_000
      scan.state.value = MissionProgress(0, 1, activity = 1)
      runCurrent()
      assertEquals(7_000L, ringState.lastProgressAt)
      assertEquals(RingPhase.WORKING, viewModel.uiState.value.phase)
    }

  @Test
  fun qr_hintsWhatTheCameraSees_thenPassesAndLogsTheNumbers() =
    runTest(main.dispatcher) {
      val (viewModel, commands) = qrRingScreen()
      viewModel.openCamera()
      runCurrent()

      scan.next = ScanVerdict.TOO_FAR
      viewModel.onScan(listOf(SeenCode("x", CodeFormat.QR, 0.1f)))
      assertEquals(ScanVerdict.TOO_FAR, viewModel.uiState.value.scanHint)
      // An empty frame in between keeps the hint steady.
      scan.next = ScanVerdict.NONE
      viewModel.onScan(emptyList())
      assertEquals(ScanVerdict.TOO_FAR, viewModel.uiState.value.scanHint)

      scan.state.value = MissionProgress(1, 1, MissionState.PASSED)
      runCurrent()
      assertEquals(listOf(RingCommand.Dismiss(RingService.SOURCE_MISSION)), commands)
      assertFalse(viewModel.uiState.value.cameraOpen)
      assertTrue(log.details.last().endsWith(" opens=1 fake=yes"))
    }

  @Test
  fun qr_aCameraThatWillNotStart_fallsBackToThePlainDismiss() =
    runTest(main.dispatcher) {
      val (viewModel, _) = qrRingScreen()
      viewModel.openCamera()
      viewModel.onCameraError(IllegalStateException("in use"))
      runCurrent()

      assertTrue(viewModel.uiState.value.plainDismiss)
      assertFalse(viewModel.uiState.value.cameraOpen)
      assertEquals(RingEventType.MISSION_FAILED, log.types.last())
      assertTrue(log.details.last().contains("reason=camera_IllegalStateException"))
    }

  @Test
  fun walk_hasNoCamera() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.WALK)))
      val (viewModel, _) = ringScreen()
      viewModel.openCamera()
      assertFalse(viewModel.uiState.value.cameraOpen)
    }

  private class FakeScanMission : ScanMission {
    override val type = MissionType.QR
    val state = MutableStateFlow(MissionProgress(0, 1))
    override val progress: StateFlow<MissionProgress> = state
    var opens = 0
    var next = ScanVerdict.NONE

    override fun start() = Unit

    override fun stop() = Unit

    override fun onCodes(codes: List<SeenCode>) = next

    override fun cameraOpened() {
      opens++
    }

    override fun torchChanged(on: Boolean, auto: Boolean) = Unit

    override fun summary() = "opens=$opens fake=yes"
  }

  private class FakeMission : Mission {
    override val type = MissionType.WALK
    val state = MutableStateFlow(MissionProgress(0, 30))
    override val progress: StateFlow<MissionProgress> = state
    var starts = 0
    var stopped = false

    override fun start() {
      starts++
    }

    override fun stop() {
      stopped = true
    }
  }

  private class RecordingLog : RingLog {
    val types = mutableListOf<RingEventType>()
    val details = mutableListOf<String>()

    override suspend fun record(type: RingEventType, alarmId: Long?, scheduledAt: Instant?, detail: String) {
      types += type
      details += detail
    }
  }
}
