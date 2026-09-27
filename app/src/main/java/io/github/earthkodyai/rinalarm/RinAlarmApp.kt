package io.github.earthkodyai.rinalarm

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.earthkodyai.rinalarm.alarm.notify.AlarmNotifications
import javax.inject.Inject

/** Also runs in Direct Boot (for the ring path), so onCreate must not touch credential-protected storage. */
@HiltAndroidApp
class RinAlarmApp : Application() {
  @Inject lateinit var notifications: AlarmNotifications

  override fun onCreate() {
    super.onCreate()
    notifications.createChannels()
  }
}
