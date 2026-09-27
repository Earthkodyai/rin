package io.github.earthkodyai.rinalarm.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import java.time.DayOfWeek
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

// Text shared by the alarm list and the editor.

/**
 * Formats times of day the way the phone's 12/24-hour setting asks. The locale alone is not enough: a Thai locale
 * with the 24-hour switch on must still show 07:00, and the time picker follows the same switch.
 */
@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
  val context = LocalContext.current
  val locale = LocalLocale.current.platformLocale
  val is24Hour = DateFormat.is24HourFormat(context)
  return remember(locale, is24Hour) {
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)
  }
}

@Composable
fun repeatSummary(days: RepeatDays): String =
  when (days) {
    RepeatDays.NONE -> stringResource(R.string.repeat_once)
    RepeatDays.EVERY_DAY -> stringResource(R.string.repeat_every_day)
    RepeatDays.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
    RepeatDays.WEEKEND -> stringResource(R.string.repeat_weekend)
    else -> {
      val locale = LocalLocale.current.platformLocale
      days.days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
  }

/** A weekday's name in the UI's current locale (observable, so a locale change recomposes). */
@Composable
fun DayOfWeek.displayName(style: TextStyle): String = getDisplayName(style, LocalLocale.current.platformLocale)

/** "7 h 20 min", "2 d 1 h", "1 min". Rounded up to the minute, so an alarm 30 s away never reads "0 min". */
@Composable
fun durationText(duration: Duration): String {
  val parts = TimeUntil.of(duration)
  return buildList {
      if (parts.days > 0) add(stringResource(R.string.duration_days, parts.days))
      if (parts.hours > 0) add(stringResource(R.string.duration_hours, parts.hours))
      if (parts.minutes > 0 || isEmpty()) add(stringResource(R.string.duration_minutes, parts.minutes))
    }
    .joinToString(" ")
}

/** Whole days, hours and minutes until a ring, rounded up to the next minute. */
data class TimeUntil(val days: Long, val hours: Int, val minutes: Int) {
  companion object {
    fun of(duration: Duration): TimeUntil {
      if (duration.isNegative) return TimeUntil(0, 0, 0)
      val seconds = duration.seconds + if (duration.nano > 0) 1 else 0
      val totalMinutes = (seconds + 59) / 60
      return TimeUntil(totalMinutes / (24 * 60), ((totalMinutes / 60) % 24).toInt(), (totalMinutes % 60).toInt())
    }
  }
}
