package io.github.earthkodyai.rinalarm.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * An activity's content in RinAlarm's look, day or night by [clock]. The status and navigation bar icons follow the
 * app's look too (light icons at night), not the phone's dark mode, so they stay readable on either ground.
 */
@Composable
fun ComponentActivity.RinThemedContent(clock: ThemeClock, content: @Composable () -> Unit) {
  val night by clock.night.collectAsStateWithLifecycle()
  LaunchedEffect(night) {
    val bars =
      if (night) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
      else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
  }
  RinAlarmTheme(night = night, content = content)
}
