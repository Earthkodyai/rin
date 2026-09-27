package io.github.earthkodyai.rinalarm.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmWriter
import io.github.earthkodyai.rinalarm.data.AlarmRepository
import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.setup.DeviceStatus
import io.github.earthkodyai.rinalarm.setup.DeviceStatusSource
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OnboardingStep {
  WELCOME,
  NOTIFICATIONS,
  FULL_SCREEN,
  EXACT_ALARMS,
  /** Xiaomi/Redmi/POCO only: Show on Lock screen, Autostart and battery settings, which no API can read. */
  PHONE_BRAND,
  TEST,
}

object OnboardingSteps {
  /**
   * The steps for this phone, fixed when onboarding starts: a step granted meanwhile shows as done instead of
   * vanishing, so the step count never jumps under the user's finger.
   */
  fun plan(status: DeviceStatus): List<OnboardingStep> = buildList {
    add(OnboardingStep.WELCOME)
    if (!status.notificationsAllowed) add(OnboardingStep.NOTIFICATIONS)
    if (status.fullScreenAllowed == false) add(OnboardingStep.FULL_SCREEN)
    if (!status.exactAlarmsAllowed) add(OnboardingStep.EXACT_ALARMS)
    if (status.xiaomiFamily) add(OnboardingStep.PHONE_BRAND)
    add(OnboardingStep.TEST)
  }
}

@HiltViewModel
class OnboardingViewModel
@Inject
constructor(
  private val statusSource: DeviceStatusSource,
  private val settings: AppSettings,
  alarms: AlarmRepository,
  private val writer: AlarmWriter,
  private val time: TimeSource,
  private val savedState: SavedStateHandle,
) : ViewModel() {
  private val status = MutableStateFlow(statusSource.read())
  private val steps: List<OnboardingStep> =
    savedState.get<List<String>>(KEY_STEPS)?.map(OnboardingStep::valueOf)
      ?: OnboardingSteps.plan(status.value).also { plan -> savedState[KEY_STEPS] = ArrayList(plan.map { it.name }) }
  private val index = MutableStateFlow(savedState.get<Int>(KEY_INDEX) ?: 0)
  private val testStarted = MutableStateFlow(savedState.get<Boolean>(KEY_TEST_STARTED) ?: false)
  private val testFailed = MutableStateFlow(false)

  val uiState: StateFlow<OnboardingUiState> =
    combine(status, index, alarms.testAlarm, testStarted, testFailed) { status, index, test, started, failed ->
        val zone = time.zone()
        OnboardingUiState(steps, index, status, test?.nextTrigger(time.now(), zone)?.atZone(zone), started, failed)
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        OnboardingUiState(steps, index.value, status.value, null, testStarted.value, testFailed = false),
      )

  fun refresh() {
    status.value = statusSource.read()
  }

  fun next() = go(index.value + 1)

  /** Returns false on the first step, so the caller lets Back leave the app. */
  fun back(): Boolean {
    if (index.value == 0) return false
    go(index.value - 1)
    return true
  }

  fun startTest(label: String) {
    viewModelScope.launch {
      testFailed.value = runCatching { writer.scheduleTest(label) }.isFailure
      if (!testFailed.value) {
        testStarted.value = true
        savedState[KEY_TEST_STARTED] = true
      }
    }
  }

  /** Marks onboarding done (so it never shows again), then calls [onDone]. A pending test alarm keeps waiting. */
  fun finish(onDone: () -> Unit) {
    viewModelScope.launch {
      settings.setOnboardingCompleted(true)
      onDone()
    }
  }

  private fun go(to: Int) {
    index.value = to.coerceIn(0, steps.lastIndex)
    savedState[KEY_INDEX] = index.value
  }

  private companion object {
    const val KEY_STEPS = "steps"
    const val KEY_INDEX = "index"
    const val KEY_TEST_STARTED = "testStarted"
  }
}

data class OnboardingUiState(
  val steps: List<OnboardingStep>,
  val index: Int,
  val status: DeviceStatus,
  val testRingAt: ZonedDateTime?,
  /** A test alarm was set at least once, so the last step offers Finish rather than "finish without testing". */
  val testStarted: Boolean,
  val testFailed: Boolean,
) {
  val step: OnboardingStep
    get() = steps[index]

  val isLast: Boolean
    get() = index == steps.lastIndex
}
