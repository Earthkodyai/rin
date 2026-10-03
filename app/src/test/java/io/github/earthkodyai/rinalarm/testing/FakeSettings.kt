package io.github.earthkodyai.rinalarm.testing

import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.data.DayMode
import io.github.earthkodyai.rinalarm.data.DayModeKind
import io.github.earthkodyai.rinalarm.data.ThemeMode
import io.github.earthkodyai.rinalarm.mission.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow

/** [AppSettings] in memory. */
class FakeSettings(dayMode: DayMode? = null, legacyPoutOff: Boolean = false) : AppSettings {
  val completed = MutableStateFlow(false)
  override val onboardingCompleted = completed

  override suspend fun setOnboardingCompleted(completed: Boolean) {
    this.completed.value = completed
  }

  override val lastDifficulty = MutableStateFlow(Difficulty.NEW_ALARM)

  override suspend fun setLastDifficulty(difficulty: Difficulty) {
    lastDifficulty.value = difficulty
  }

  override val lastScold = MutableStateFlow(true)

  override suspend fun setLastScold(scold: Boolean) {
    lastScold.value = scold
  }

  /** The old "No pouting" switch, until [clearLegacyPoutOff]. */
  var legacy = legacyPoutOff

  override suspend fun legacyPoutOff(): Boolean = legacy

  override suspend fun clearLegacyPoutOff() {
    legacy = false
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

  override val tournamentGuidePending = MutableStateFlow(true)

  override suspend fun setTournamentGuidePending(pending: Boolean) {
    tournamentGuidePending.value = pending
  }

  override suspend fun setTutorialPending(pending: Boolean) {
    tutorialPending.value = pending
  }
}
