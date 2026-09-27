package io.github.earthkodyai.rinalarm.ui.diagnostics

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.log.RingOutcome
import io.github.earthkodyai.rinalarm.alarm.log.RingSummary
import io.github.earthkodyai.rinalarm.setup.CheckId
import io.github.earthkodyai.rinalarm.setup.DeviceStatus
import io.github.earthkodyai.rinalarm.setup.SettingsLinks
import io.github.earthkodyai.rinalarm.setup.SetupChecks
import io.github.earthkodyai.rinalarm.setup.rememberNotificationPermissionAction
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.ui.common.displayName
import io.github.earthkodyai.rinalarm.ui.common.rememberTimeFormatter
import io.github.earthkodyai.rinalarm.ui.setup.CheckRow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  LifecycleResumeEffect(viewModel) {
    viewModel.refresh()
    onPauseOrDispose {}
  }
  val context = LocalContext.current
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  val testLabel = stringResource(R.string.test_label)
  val copied = stringResource(R.string.diagnostics_copied)
  val openFailed = stringResource(R.string.settings_open_failed)
  val notifications = rememberNotificationPermissionAction(state.status.xiaomiFamily, onResult = viewModel::refresh)
  DiagnosticsScreen(
    state = state,
    onBack = onBack,
    onFix = { id ->
      if (id == CheckId.NOTIFICATIONS) {
        notifications.run()
      } else if (!SettingsLinks.open(context, id, state.status.xiaomiFamily)) {
        Toast.makeText(context, openFailed, Toast.LENGTH_LONG).show()
      }
    },
    onStartTest = { viewModel.startTest(testLabel) },
    onCancelTest = viewModel::cancelTest,
    onCopyReport = {
      val report = viewModel.report()
      scope.launch {
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("RinAlarm diagnostics", report)))
        // Android 13+ shows its own confirmation for clipboard writes.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
      }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticsScreen(
  state: DiagnosticsUiState,
  onBack: () -> Unit,
  onFix: (CheckId) -> Unit,
  onStartTest: () -> Unit,
  onCancelTest: () -> Unit,
  onCopyReport: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.diagnostics_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.editor_back))
          }
        },
        actions = { TextButton(onClick = onCopyReport) { Text(stringResource(R.string.diagnostics_copy)) } },
      )
    }
  ) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
      item {
        Text(
          stringResource(
            R.string.diagnostics_device,
            state.status.device,
            state.status.androidRelease,
            state.status.appVersion,
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
      item { SectionTitle(stringResource(R.string.diagnostics_checks)) }
      items(state.checks, key = { it.id }) { check ->
        CheckRow(check, state.status, onFix = { onFix(check.id) })
      }
      item {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionTitle(stringResource(R.string.test_title))
        TestAlarmPanel(state.testRingAt, state.testFailed, onStartTest, onCancelTest, Modifier.padding(horizontal = 16.dp))
        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionTitle(stringResource(R.string.recent_title))
        if (state.recentRings.isEmpty()) {
          Text(stringResource(R.string.recent_empty), Modifier.padding(horizontal = 16.dp))
        }
      }
      items(state.recentRings, key = { it.firedAt.toEpochMilli() * 31 + it.alarmId }) { RingRow(it) }
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
  )
}

/** Shared with onboarding's last step. Tap-only, and Cancel sits apart from Start. */
@Composable
internal fun TestAlarmPanel(
  ringAt: ZonedDateTime?,
  failed: Boolean,
  onStart: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  cancellable: Boolean = true,
) {
  val timeFormat = rememberTimeFormatter()
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (ringAt == null) {
      Text(stringResource(R.string.test_text), style = MaterialTheme.typography.bodyMedium)
      Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.test_start)) }
    } else {
      Text(stringResource(R.string.test_pending, ringAt.format(timeFormat)), style = MaterialTheme.typography.bodyLarge)
      if (cancellable) OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.test_cancel)) }
    }
    if (failed) Text(stringResource(R.string.test_failed), color = MaterialTheme.colorScheme.error)
  }
}

@Composable
private fun RingRow(ring: RingSummary) {
  val timeFormat = rememberTimeFormatter()
  val zone = ZoneId.systemDefault()
  val shown = (ring.scheduledAt ?: ring.firedAt).atZone(zone)
  ListItem(
    headlineContent = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${shown.dayOfWeek.displayName(TextStyle.SHORT)} ${shown.format(timeFormat)}")
        if (ring.isTest) {
          Text(
            stringResource(R.string.recent_test),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
          )
        }
      }
    },
    supportingContent = {
      Column {
        Text(outcomeText(ring.outcome), style = MaterialTheme.typography.bodyMedium)
        val late = ring.late
        if (late != null) {
          val sound = ring.toSound
          Text(
            if (sound == null) {
              stringResource(R.string.recent_timing_fired, seconds(late))
            } else {
              stringResource(R.string.recent_timing, seconds(late), seconds(sound))
            },
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
  )
}

@Composable
private fun outcomeText(outcome: RingOutcome): String =
  stringResource(
    when (outcome) {
      RingOutcome.DISMISSED -> R.string.outcome_dismissed
      RingOutcome.SNOOZED -> R.string.outcome_snoozed
      RingOutcome.AUTO_STOPPED -> R.string.outcome_auto_stopped
      RingOutcome.OVERLAPPED -> R.string.outcome_overlapped
      RingOutcome.FAILED -> R.string.outcome_failed
      RingOutcome.MISSED -> R.string.outcome_missed
      RingOutcome.UNKNOWN -> R.string.outcome_unknown
    }
  )

/** "0.05", "125.3": two decimals under 10 s, one above. Always a dot, since these are measurements. */
private fun seconds(duration: Duration): String {
  val s = duration.toMillis() / 1000.0
  return String.format(Locale.ROOT, if (s < 10) "%.2f" else "%.1f", s)
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun DiagnosticsPreview() {
  val status =
    DeviceStatus(35, false, false, true, false, false, 3, 15, false, true, "Xiaomi 2406APNFAG", "15", "0.1.0")
  val now = Instant.parse("2026-09-28T00:00:00Z")
  val hourAgo = now.minusSeconds(3600)
  val rings =
    listOf(
      RingSummary(1, now, now.plusMillis(52), Duration.ofMillis(52), Duration.ofMillis(900), RingOutcome.DISMISSED, false),
      RingSummary(2, hourAgo, hourAgo.plusMillis(80), Duration.ofMillis(80), null, RingOutcome.FAILED, true),
      RingSummary(3, now.minusSeconds(86400), now.minusSeconds(85000), null, null, RingOutcome.MISSED, false),
    )
  RinAlarmTheme {
    DiagnosticsScreen(
      DiagnosticsUiState(status, SetupChecks.evaluate(status), null, false, rings),
      {},
      {},
      {},
      {},
      {},
    )
  }
}
