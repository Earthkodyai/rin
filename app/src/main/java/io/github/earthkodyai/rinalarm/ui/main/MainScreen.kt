package io.github.earthkodyai.rinalarm.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MainScreen(modifier: Modifier = Modifier, viewModel: MainScreenViewModel = hiltViewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  MainScreen(state = state, modifier = modifier)
}

@Composable
internal fun MainScreen(state: MainScreenUiState, modifier: Modifier = Modifier) {
  when (state) {
    MainScreenUiState.Loading -> {
      // Blank: Room answers within a frame or two.
    }
    is MainScreenUiState.Error -> Text(stringResource(R.string.alarms_load_error), modifier = modifier)
    is MainScreenUiState.Success ->
      if (state.alarms.isEmpty()) {
        Text(stringResource(R.string.alarms_empty), modifier = modifier)
      } else {
        LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
          items(state.alarms, key = { it.alarm.id }) { AlarmRowItem(it) }
        }
      }
  }
}

@Composable
private fun AlarmRowItem(row: AlarmRow, modifier: Modifier = Modifier) {
  val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
  Column(modifier) {
    Text(row.alarm.time.format(timeFormat), style = MaterialTheme.typography.headlineMedium)
    Text(repeatSummary(row.alarm.repeatDays), style = MaterialTheme.typography.bodyMedium)
    val next = row.nextRing
    Text(
      if (next == null) {
        stringResource(R.string.alarm_off)
      } else {
        stringResource(R.string.alarm_next_ring, next.dayOfWeek.shortName(), next.format(timeFormat))
      },
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
private fun repeatSummary(days: RepeatDays): String =
  when (days) {
    RepeatDays.NONE -> stringResource(R.string.repeat_once)
    RepeatDays.EVERY_DAY -> stringResource(R.string.repeat_every_day)
    RepeatDays.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
    RepeatDays.WEEKEND -> stringResource(R.string.repeat_weekend)
    else -> days.days.joinToString(", ") { it.shortName() }
  }

private fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
  val zone = ZoneId.of("Asia/Bangkok")
  val alarm = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = RepeatDays.WEEKDAYS)
  val next = ZonedDateTime.of(2026, 9, 28, 7, 0, 0, 0, zone)
  RinAlarmTheme { MainScreen(MainScreenUiState.Success(listOf(AlarmRow(alarm, next)))) }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenEmptyPreview() {
  RinAlarmTheme { MainScreen(MainScreenUiState.Success(emptyList())) }
}
