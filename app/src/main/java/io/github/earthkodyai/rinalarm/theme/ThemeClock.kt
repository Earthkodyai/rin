package io.github.earthkodyai.rinalarm.theme

import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.data.ThemeMode
import io.github.earthkodyai.rinalarm.di.AppScope
import io.github.earthkodyai.rinalarm.time.TimeSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the app shows its night look now: the user's [ThemeMode] (Settings), and in AUTO the time of day, checked
 * every minute so an open screen turns at 19:00 and 06:00. Both activities read it for RinAlarmTheme.
 */
@Singleton
class ThemeClock @Inject constructor(settings: AppSettings, time: TimeSource, @AppScope scope: CoroutineScope) {
  private fun nowIsNight(mode: ThemeMode, time: TimeSource) = mode.isNight(time.now().atZone(time.zone()).toLocalTime())

  val night: StateFlow<Boolean> =
    combine(settings.themeMode, time.minuteTicks) { mode, _ -> nowIsNight(mode, time) }
      .stateIn(scope, SharingStarted.Eagerly, nowIsNight(ThemeMode.AUTO, time))
}
