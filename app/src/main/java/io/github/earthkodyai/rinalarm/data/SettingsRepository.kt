package io.github.earthkodyai.rinalarm.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Small app-wide flags. */
interface AppSettings {
  /** Set once the permission onboarding (task 1.4) has been finished or skipped through. */
  val onboardingCompleted: Flow<Boolean>

  suspend fun setOnboardingCompleted(completed: Boolean)

  /** "Pout off" (Phase 5): Rin keeps a calm face, and her pouty lines stay unsaid. */
  val poutOff: Flow<Boolean>

  suspend fun setPoutOff(off: Boolean)

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
}

/** [AppSettings] in a Preferences DataStore in device-protected storage. */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) : AppSettings {
  override val onboardingCompleted: Flow<Boolean> = store.data.map { it[ONBOARDING_COMPLETED] ?: false }

  override suspend fun setOnboardingCompleted(completed: Boolean) {
    store.edit { it[ONBOARDING_COMPLETED] = completed }
  }

  override val poutOff: Flow<Boolean> = store.data.map { it[POUT_OFF] ?: false }

  override suspend fun setPoutOff(off: Boolean) {
    store.edit { it[POUT_OFF] = off }
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

  private fun Preferences.dayMode(): DayMode? {
    val kind = DayModeKind.fromStored(this[DAY_MODE]) ?: return null
    return DayMode(kind, this[DAY_MODE_AT] ?: return null)
  }

  private companion object {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val POUT_OFF = booleanPreferencesKey("pout_off")
    val DAY_MODE = stringPreferencesKey("day_mode")
    val DAY_MODE_AT = longPreferencesKey("day_mode_at")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val TUTORIAL_PENDING = booleanPreferencesKey("tutorial_pending")
  }
}
