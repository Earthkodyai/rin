package io.github.earthkodyai.rinalarm.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** The palette in effect: [RinAlarmTheme] provides it, screens read it as `RinTheme.palette`. */
val LocalRinPalette = staticCompositionLocalOf { DayPalette }

object RinTheme {
  val palette: RinPalette
    @Composable @ReadOnlyComposable get() = LocalRinPalette.current
}

/**
 * RinAlarm's look (UX phase): a fixed palette, day or [night], never the wallpaper's (dynamic colour is off) nor the
 * phone's dark mode; ThemeClock decides [night] from the user's setting and the time of day. Material components get
 * the palette through the colour scheme, the app's own shapes through [RinTheme.palette].
 */
@Composable
fun RinAlarmTheme(night: Boolean = false, content: @Composable () -> Unit) {
  val p = if (night) NightPalette else DayPalette
  val scheme =
    if (night) {
      darkColorScheme(
        primary = p.primary,
        onPrimary = p.onPrimary,
        secondary = p.mint,
        onSecondary = p.ground,
        tertiary = p.glow,
        background = p.ground,
        onBackground = p.ink,
        surface = p.ground,
        onSurface = p.ink,
        surfaceVariant = p.card,
        onSurfaceVariant = p.muted,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.card,
        surfaceContainerLow = p.card,
        outline = p.line,
        outlineVariant = p.line,
        secondaryContainer = p.rinCard,
        onSecondaryContainer = p.ink,
        primaryContainer = p.primary,
        onPrimaryContainer = p.onPrimary,
        errorContainer = p.card,
        onErrorContainer = p.danger,
      )
    } else {
      lightColorScheme(
        primary = p.primary,
        onPrimary = p.onPrimary,
        secondary = p.mintText,
        onSecondary = p.card,
        tertiary = p.glow,
        background = p.ground,
        onBackground = p.ink,
        surface = p.ground,
        onSurface = p.ink,
        surfaceVariant = p.card,
        onSurfaceVariant = p.muted,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.card,
        surfaceContainerLow = p.card,
        outline = p.line,
        outlineVariant = p.line,
        secondaryContainer = p.rinCard,
        onSecondaryContainer = p.ink,
        primaryContainer = p.rinCard,
        onPrimaryContainer = p.ink,
      )
    }
  CompositionLocalProvider(LocalRinPalette provides p) {
    MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
  }
}
