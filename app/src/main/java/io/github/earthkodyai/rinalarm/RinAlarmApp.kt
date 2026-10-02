package io.github.earthkodyai.rinalarm

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.earthkodyai.rinalarm.alarm.notify.AlarmNotifications
import io.github.earthkodyai.rinalarm.data.LegacyPoutOff
import io.github.earthkodyai.rinalarm.di.AppScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Also runs in Direct Boot (for the ring path), so onCreate must not touch credential-protected storage. */
@HiltAndroidApp
class RinAlarmApp : Application() {
  @Inject lateinit var notifications: AlarmNotifications
  @Inject lateinit var legacyPoutOff: LegacyPoutOff
  @Inject @AppScope lateinit var appScope: CoroutineScope

  override fun onCreate() {
    super.onCreate()
    notifications.createChannels()
    // The database and settings live in device-protected storage, so this is safe in Direct Boot too.
    appScope.launch { runCatching { legacyPoutOff.run() } }
  }
}
