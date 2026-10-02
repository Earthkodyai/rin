package io.github.earthkodyai.rinalarm.testing

import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.data.DayMode
import io.github.earthkodyai.rinalarm.data.DayModeKind
import io.github.earthkodyai.rinalarm.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

/** [AppSettings] in memory. */
class FakeSettings(poutOff: Boolean = false, dayMode: DayMode? = null) : AppSettings {
  val completed = MutableStateFlow(false)
  override val onboardingCompleted = completed

  override suspend fun setOnboardingCompleted(completed: Boolean) {
    this.completed.value = completed
  }

  override val poutOff = MutableStateFlow(poutOff)

  override suspend fun setPoutOff(off: Boolean) {
    poutOff.value = off
  }

  override val dayMode = MutableStateFlow(dayMode)

  override suspend fun setDayMode(kind: DayModeKind?, atMs: Long) {
    dayMode.value = kind?.let { DayMode(it, atMs) }
  }

  override suspend fun endDayMode(used: DayMode) {
    if (dayMode.value == used) dayMode.value = null
  }

  override val themeMode = MutableStateFlow(ThemeMode.AUTO)

  override suspend fun setThemeMode(mode: ThemeMode) {
    themeMode.value = mode
  }

  override val tutorialPending = MutableStateFlow(false)

  override suspend fun setTutorialPending(pending: Boolean) {
    tutorialPending.value = pending
  }
}
