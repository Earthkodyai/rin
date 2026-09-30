package io.github.earthkodyai.rinalarm.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import io.github.earthkodyai.rinalarm.testing.FakeAlarms
import io.github.earthkodyai.rinalarm.testing.FakeDeviceStatus
import io.github.earthkodyai.rinalarm.testing.FakeSettings
import io.github.earthkodyai.rinalarm.testing.FixedTimeSource
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import io.github.earthkodyai.rinalarm.ui.onboarding.OnboardingStep.EXACT_ALARMS
import io.github.earthkodyai.rinalarm.ui.onboarding.OnboardingStep.FULL_SCREEN
import io.github.earthkodyai.rinalarm.ui.onboarding.OnboardingStep.NOTIFICATIONS
import io.github.earthkodyai.rinalarm.ui.onboarding.OnboardingStep.PHONE_BRAND
import io.github.earthkodyai.rinalarm.ui.onboarding.OnboardingStep.TEST
import io.github.earthkodyai.rinalarm.ui.onboarding.OnboardingStep.WELCOME
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val zone = ZoneId.of("Asia/Bangkok")
  private val time = FixedTimeSource(LocalDateTime.parse("2026-09-28T06:00").atZone(zone).toInstant(), zone)
  private val device = FakeDeviceStatus()
  private val alarms = FakeAlarms()
  private val settings = FakeSettings()

  private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
    OnboardingViewModel(device, settings, alarms, alarms, time, saved)

  // --- the plan ---

  @Test
  fun plan_allGranted_isWelcomeThenTest() {
    assertEquals(listOf(WELCOME, TEST), OnboardingSteps.plan(FakeDeviceStatus.ALL_GOOD))
  }

  @Test
  fun plan_asksOnlyForWhatIsMissing_inOrder() {
    val status =
      FakeDeviceStatus.ALL_GOOD.copy(
        notificationsAllowed = false,
        fullScreenAllowed = false,
        exactAlarmsAllowed = false,
        xiaomiFamily = true,
      )
    assertEquals(listOf(WELCOME, NOTIFICATIONS, FULL_SCREEN, EXACT_ALARMS, PHONE_BRAND, TEST), OnboardingSteps.plan(status))
  }

  @Test
  fun plan_belowAndroid14_hasNoFullScreenStep() {
    val status = FakeDeviceStatus.ALL_GOOD.copy(sdk = 33, fullScreenAllowed = null)
    assertFalse(FULL_SCREEN in OnboardingSteps.plan(status))
  }

  // --- the ViewModel ---

  @Test
  fun stepsStayFixed_whenAPermissionIsGrantedMidway() = runTest {
    device.status = FakeDeviceStatus.ALL_GOOD.copy(notificationsAllowed = false)
    val vm = viewModel()
    vm.next()

    device.status = FakeDeviceStatus.ALL_GOOD
    vm.refresh()

    val state = vm.uiState.first { it.status.notificationsAllowed }
    assertEquals(listOf(WELCOME, NOTIFICATIONS, TEST), state.steps)
    assertEquals(NOTIFICATIONS, state.step)
  }

  @Test
  fun back_onTheFirstStep_isNotHandled_elsewhereGoesBackOneStep() = runTest {
    val vm = viewModel()
    assertFalse(vm.back())
    vm.next()
    assertTrue(vm.back())
    assertEquals(WELCOME, vm.uiState.first().step)
  }

  @Test
  fun next_stopsAtTheLastStep() = runTest {
    val vm = viewModel()
    repeat(5) { vm.next() }
    val state = vm.uiState.first { it.step == TEST }
    assertTrue(state.isLast)
  }

  @Test
  fun positionSurvivesProcessDeath() = runTest {
    device.status = FakeDeviceStatus.ALL_GOOD.copy(notificationsAllowed = false)
    val saved = SavedStateHandle()
    viewModel(saved).next()

    // The permission was granted in Settings while the process was gone: the plan must not shrink under the user.
    device.status = FakeDeviceStatus.ALL_GOOD
    val restored = viewModel(saved).uiState.first()

    assertEquals(listOf(WELCOME, NOTIFICATIONS, TEST), restored.steps)
    assertEquals(NOTIFICATIONS, restored.step)
  }

  @Test
  fun startTest_showsTheRingTime_andFinishStaysAfterItRang() = runTest {
    val vm = viewModel()
    vm.startTest("Test alarm")

    val pending = vm.uiState.first { it.testRingAt != null }
    assertEquals("2026-09-28T06:02+07:00", pending.testRingAt?.toOffsetDateTime().toString())
    assertTrue(pending.testStarted)

    alarms.ringTest()
    val after = vm.uiState.first { it.testRingAt == null }
    assertTrue(after.testStarted)
  }

  @Test
  fun startTest_failure_isShown_andDoesNotCountAsStarted() = runTest {
    alarms.failure = IllegalStateException("disk full")
    val vm = viewModel()
    vm.startTest("Test alarm")

    val state = vm.uiState.first { it.testFailed }
    assertFalse(state.testStarted)
    assertNull(state.testRingAt)
  }

  @Test
  fun finish_marksOnboardingDone_thenCallsBack() = runTest {
    var done = false
    viewModel().finish { done = settings.completed.value }
    assertTrue(done)
  }
}
