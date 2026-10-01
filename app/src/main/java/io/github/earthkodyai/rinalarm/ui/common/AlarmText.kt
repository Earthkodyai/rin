package io.github.earthkodyai.rinalarm.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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

/**
 * A time as digits only, "07:00" on a 24-hour phone, "7:00" plus [amPm] on a 12-hour one: the alarm cards' big
 * numbers and the list header (UX.2, the user's pick). No locale suffix such as Thai "น.", which the rounded font
 * cannot draw; screen readers still get the locale's full time from [rememberTimeFormatter].
 */
data class ClockText(val digits: String, val amPm: String?) {
  override fun toString(): String = if (amPm == null) digits else "$digits $amPm"
}

@Composable
fun rememberClockText(): (LocalTime) -> ClockText {
  val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
  val locale = LocalLocale.current.platformLocale
  return remember(is24Hour, locale) {
    val digits = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm", Locale.ROOT)
    val amPm = DateTimeFormatter.ofPattern("a", locale)
    val format: (LocalTime) -> ClockText = { ClockText(it.format(digits), if (is24Hour) null else it.format(amPm)) }
    format
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

/** What stops an alarm, as the editor and the alarm list name it. */
@Composable
fun missionChoiceName(choice: MissionChoice): String =
  stringResource(
    when (choice) {
      MissionChoice.RinPicks -> R.string.mission_choice_rin_picks
      MissionChoice.None -> R.string.mission_choice_none
      is MissionChoice.Only ->
        when (choice.type) {
          MissionType.PADS -> R.string.mission_choice_pads
          MissionType.CUPS -> R.string.mission_choice_cups
          MissionType.QR -> R.string.mission_choice_qr
          MissionType.SPEECH -> R.string.mission_choice_speech
        }
    }
  )

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
