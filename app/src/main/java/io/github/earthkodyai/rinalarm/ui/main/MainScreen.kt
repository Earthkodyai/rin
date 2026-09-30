package io.github.earthkodyai.rinalarm.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.character.CharacterView
import io.github.earthkodyai.rinalarm.character.rememberDefaultMood
import io.github.earthkodyai.rinalarm.ui.common.RinLine
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.ui.common.displayName
import io.github.earthkodyai.rinalarm.ui.common.rememberTimeFormatter
import io.github.earthkodyai.rinalarm.ui.common.repeatSummary
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle

@Composable
fun MainScreen(
  onAdd: () -> Unit,
  onEdit: (Long) -> Unit,
  onDiagnostics: () -> Unit,
  viewModel: MainScreenViewModel = hiltViewModel(),
  rin: HomeRinViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val setupIssue by viewModel.setupIssue.collectAsStateWithLifecycle()
  LifecycleResumeEffect(viewModel) {
    viewModel.refreshSetup()
    onPauseOrDispose {}
  }
  LifecycleStartEffect(rin) {
    rin.onShown()
    onStopOrDispose { rin.onHidden() }
  }
  MainScreen(
    state = state,
    onAdd = onAdd,
    onEdit = onEdit,
    onToggle = viewModel::setEnabled,
    setupIssue = setupIssue,
    onDiagnostics = onDiagnostics,
    character = {
      val line by rin.line.collectAsStateWithLifecycle()
      val mood = rememberDefaultMood()
      Column(Modifier.fillMaxWidth()) {
        CharacterView(
          line?.emotion ?: mood,
          Modifier.fillMaxWidth().height(CHARACTER_HEIGHT),
          cues = rin.cues,
          speech = rin.speaking,
          onHeadTap = rin::onHeadTap,
        )
        // Under her strip only while she speaks (4.3): home lines are rare, so no empty band sits over the alarms.
        RinLine(line?.text, reserve = false)
      }
    },
  )
}

/** Rin's strip above the list: head and shoulders, leaving most of a phone screen to the alarms. */
private val CHARACTER_HEIGHT = 220.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
  state: MainScreenUiState,
  onAdd: () -> Unit,
  onEdit: (Long) -> Unit,
  onToggle: (Long, Boolean) -> Unit,
  setupIssue: Boolean = false,
  onDiagnostics: () -> Unit = {},
  // A slot, so previews and UI tests run without a WebView.
  character: @Composable () -> Unit = {},
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.alarms_title)) },
        actions = { TextButton(onClick = onDiagnostics) { Text(stringResource(R.string.diagnostics_title)) } },
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onAdd,
        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
        text = { Text(stringResource(R.string.alarm_add)) },
      )
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      if (setupIssue) SetupBanner(onDiagnostics)
      character()
      AlarmList(state, onEdit, onToggle, Modifier.weight(1f).fillMaxWidth())
    }
  }
}

/** Stays until the problem is fixed: the one warning, before a morning goes wrong, that an alarm could fail. */
@Composable
private fun SetupBanner(onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(painterResource(R.drawable.ic_error), contentDescription = null)
      Text(stringResource(R.string.setup_banner), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun AlarmList(
  state: MainScreenUiState,
  onEdit: (Long) -> Unit,
  onToggle: (Long, Boolean) -> Unit,
  modifier: Modifier,
) {
  when (state) {
    MainScreenUiState.Loading -> Box(modifier) // Blank: Room answers within a frame or two.
    is MainScreenUiState.Error -> Text(stringResource(R.string.alarms_load_error), modifier.padding(16.dp))
    is MainScreenUiState.Success ->
      if (state.alarms.isEmpty()) {
        Text(stringResource(R.string.alarms_empty), modifier.padding(16.dp))
      } else {
        // Bottom padding keeps the last row's switch clear of the floating button.
        LazyColumn(modifier, contentPadding = PaddingValues(bottom = 88.dp)) {
          items(state.alarms, key = { it.alarm.id }) { row ->
            AlarmRowItem(row, onEdit = { onEdit(row.alarm.id) }, onToggle = { onToggle(row.alarm.id, it) })
            HorizontalDivider()
          }
        }
      }
  }
}

@Composable
private fun AlarmRowItem(row: AlarmRow, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
  val timeFormat = rememberTimeFormatter()
  val time = row.alarm.time.format(timeFormat)
  val switchDescription = stringResource(R.string.alarm_switch, time)
  ListItem(
    headlineContent = { Text(time, style = MaterialTheme.typography.headlineMedium) },
    supportingContent = {
      Column {
        if (row.alarm.label.isNotBlank()) Text(row.alarm.label, style = MaterialTheme.typography.bodyMedium)
        Text(repeatSummary(row.alarm.repeatDays), style = MaterialTheme.typography.bodyMedium)
        val next = row.nextRing
        Text(
          if (next == null) {
            stringResource(R.string.alarm_off)
          } else {
            stringResource(
              R.string.alarm_next_ring,
              next.dayOfWeek.displayName(TextStyle.SHORT),
              next.format(timeFormat),
            )
          },
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
    trailingContent = {
      Switch(
        checked = row.alarm.enabled,
        onCheckedChange = onToggle,
        modifier = Modifier.semantics { contentDescription = switchDescription },
      )
    },
    modifier = Modifier.clickable(onClickLabel = stringResource(R.string.alarm_edit), onClick = onEdit),
  )
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
  val zone = ZoneId.of("Asia/Bangkok")
  val alarm = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = RepeatDays.WEEKDAYS, label = "Work")
  val next = ZonedDateTime.of(2026, 9, 28, 7, 0, 0, 0, zone)
  RinAlarmTheme { MainScreen(MainScreenUiState.Success(listOf(AlarmRow(alarm, next))), {}, {}, { _, _ -> }) }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenEmptyPreview() {
  RinAlarmTheme { MainScreen(MainScreenUiState.Success(emptyList()), {}, {}, { _, _ -> }) }
}
