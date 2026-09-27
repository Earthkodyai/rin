package io.github.earthkodyai.rinalarm.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Small app-wide flags. */
interface AppSettings {
  /** Set once the permission onboarding (task 1.4) has been finished or skipped through. */
  val onboardingCompleted: Flow<Boolean>

  suspend fun setOnboardingCompleted(completed: Boolean)
}

/** [AppSettings] in a Preferences DataStore in device-protected storage. */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) : AppSettings {
  override val onboardingCompleted: Flow<Boolean> = store.data.map { it[ONBOARDING_COMPLETED] ?: false }

  override suspend fun setOnboardingCompleted(completed: Boolean) {
    store.edit { it[ONBOARDING_COMPLETED] = completed }
  }

  private companion object {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
  }
}
