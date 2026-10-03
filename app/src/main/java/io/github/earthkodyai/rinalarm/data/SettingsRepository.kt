package io.github.earthkodyai.rinalarm.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.earthkodyai.rinalarm.mission.Difficulty
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Small app-wide flags. */
interface AppSettings {
  /** Set once the permission onboarding (task 1.4) has been finished or skipped through. */
  val onboardingCompleted: Flow<Boolean>

  suspend fun setOnboardingCompleted(completed: Boolean)

  /**
   * The level the editor starts a new alarm at (G.1): the last one the user picked, [Difficulty.NEW_ALARM] until then.
   */
  val lastDifficulty: Flow<Difficulty>

  suspend fun setLastDifficulty(difficulty: Difficulty)

  /**
   * Whether a new alarm has Rin scold (G.1): turned off by the scold switch on a ring, back on only from the editor.
   * The home screen follows it too: off, her pouty head-tap line stays unsaid.
   */
  val lastScold: Flow<Boolean>

  suspend fun setLastScold(scold: Boolean)

  /** The "No pouting" switch Settings had until G.1; [LegacyPoutOff] turns it into scold off on every alarm. */
  suspend fun legacyPoutOff(): Boolean

  suspend fun clearLegacyPoutOff()

  /** The rest or sick day waiting for the next ring, whether or not it has lapsed ([DayMode.activeAt] tells). */
  val dayMode: Flow<DayMode?>

  /** Sets [kind] from [atMs], or clears it (null). */
  suspend fun setDayMode(kind: DayModeKind?, atMs: Long)

  /** A ring used [used] up: cleared, unless the user has tapped a new one since. */
  suspend fun endDayMode(used: DayMode)

  /** Day, night, or by the time of day (the app's own look, not the phone's dark mode). */
  val themeMode: Flow<ThemeMode>

  suspend fun setThemeMode(mode: ThemeMode)

  /**
   * The home screen's tour waits to be shown (UX.8): set as onboarding finishes on a new install, or from Settings;
   * cleared when the tour ends or is skipped. Users set up before UX.8 never get it unasked.
   */
  val tutorialPending: Flow<Boolean>

  suspend fun setTutorialPending(pending: Boolean)

  /**
   * The tournament start page's walkthrough waits to be shown (6.10): on everyone's first visit, and again after
   * Settings > Home tour; cleared when it ends or is skipped.
   */
  val tournamentGuidePending: Flow<Boolean>

  suspend fun setTournamentGuidePending(pending: Boolean)
}

/** [AppSettings] in a Preferences DataStore in device-protected storage. */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) : AppSettings {
  override val onboardingCompleted: Flow<Boolean> = store.data.map { it[ONBOARDING_COMPLETED] ?: false }

  override suspend fun setOnboardingCompleted(completed: Boolean) {
    store.edit { it[ONBOARDING_COMPLETED] = completed }
  }

  override val lastDifficulty: Flow<Difficulty> =
    store.data.map { prefs -> prefs[LAST_DIFFICULTY]?.let(Difficulty::fromStored) ?: Difficulty.NEW_ALARM }

  override suspend fun setLastDifficulty(difficulty: Difficulty) {
    store.edit { it[LAST_DIFFICULTY] = difficulty.stored }
  }

  override val lastScold: Flow<Boolean> = store.data.map { it[LAST_SCOLD] ?: true }

  override suspend fun setLastScold(scold: Boolean) {
    store.edit { it[LAST_SCOLD] = scold }
  }

  override suspend fun legacyPoutOff(): Boolean = store.data.first()[POUT_OFF] ?: false

  override suspend fun clearLegacyPoutOff() {
    store.edit { it.remove(POUT_OFF) }
  }

  override val dayMode: Flow<DayMode?> = store.data.map { it.dayMode() }

  override suspend fun setDayMode(kind: DayModeKind?, atMs: Long) {
    store.edit {
      if (kind == null) {
        it.remove(DAY_MODE)
        it.remove(DAY_MODE_AT)
      } else {
        it[DAY_MODE] = kind.stored
        it[DAY_MODE_AT] = atMs
      }
    }
  }

  override suspend fun endDayMode(used: DayMode) {
    store.edit {
      if (it.dayMode() == used) {
        it.remove(DAY_MODE)
        it.remove(DAY_MODE_AT)
      }
    }
  }

  override val themeMode: Flow<ThemeMode> = store.data.map { ThemeMode.fromStored(it[THEME_MODE]) }

  override suspend fun setThemeMode(mode: ThemeMode) {
    store.edit { it[THEME_MODE] = mode.stored }
  }

  override val tutorialPending: Flow<Boolean> = store.data.map { it[TUTORIAL_PENDING] ?: false }

  override suspend fun setTutorialPending(pending: Boolean) {
    store.edit { it[TUTORIAL_PENDING] = pending }
  }

  override val tournamentGuidePending: Flow<Boolean> = store.data.map { !(it[TOURNAMENT_GUIDE_DONE] ?: false) }

  override suspend fun setTournamentGuidePending(pending: Boolean) {
    store.edit { it[TOURNAMENT_GUIDE_DONE] = !pending }
  }

  private fun Preferences.dayMode(): DayMode? {
    val kind = DayModeKind.fromStored(this[DAY_MODE]) ?: return null
    return DayMode(kind, this[DAY_MODE_AT] ?: return null)
  }

  private companion object {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    /** Read once by [LegacyPoutOff], then removed. */
    val POUT_OFF = booleanPreferencesKey("pout_off")
    val LAST_DIFFICULTY = stringPreferencesKey("last_difficulty")
    val LAST_SCOLD = booleanPreferencesKey("last_scold")
    val DAY_MODE = stringPreferencesKey("day_mode")
    val DAY_MODE_AT = longPreferencesKey("day_mode_at")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val TUTORIAL_PENDING = booleanPreferencesKey("tutorial_pending")
    val TOURNAMENT_GUIDE_DONE = booleanPreferencesKey("tournament_guide_done")
  }
}
