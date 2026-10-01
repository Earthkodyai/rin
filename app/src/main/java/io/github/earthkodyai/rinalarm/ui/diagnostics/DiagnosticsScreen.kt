package io.github.earthkodyai.rinalarm.ui.diagnostics

import android.Manifest
import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.log.ReliabilityRun
import io.github.earthkodyai.rinalarm.alarm.log.RingOutcome
import io.github.earthkodyai.rinalarm.alarm.log.RingSummary
import io.github.earthkodyai.rinalarm.setup.CheckId
import io.github.earthkodyai.rinalarm.setup.DeviceStatus
import io.github.earthkodyai.rinalarm.setup.SettingsLinks
import io.github.earthkodyai.rinalarm.setup.SetupChecks
import io.github.earthkodyai.rinalarm.setup.rememberNotificationPermissionAction
import io.github.earthkodyai.rinalarm.setup.rememberRuntimePermissionAction
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.QuietPillButton
import io.github.earthkodyai.rinalarm.ui.common.RinPage
import io.github.earthkodyai.rinalarm.ui.common.SectionTitle
import io.github.earthkodyai.rinalarm.ui.common.dateText
import io.github.earthkodyai.rinalarm.ui.common.displayName
import io.github.earthkodyai.rinalarm.ui.common.rememberTimeFormatter
import io.github.earthkodyai.rinalarm.ui.common.rinCard
import io.github.earthkodyai.rinalarm.ui.common.sticker
import io.github.earthkodyai.rinalarm.ui.setup.CheckRow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
  val microphone = rememberRuntimePermissionAction(Manifest.permission.RECORD_AUDIO, onResult = viewModel::refresh)
  DiagnosticsScreen(
    state = state,
    onBack = onBack,
    onFix = { id ->
      if (id == CheckId.NOTIFICATIONS) {
        notifications.run()
      } else if (id == CheckId.MICROPHONE) {
        microphone()
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

@Composable
internal fun DiagnosticsScreen(
  state: DiagnosticsUiState,
  onBack: () -> Unit,
  onFix: (CheckId) -> Unit,
  onStartTest: () -> Unit,
  onCancelTest: () -> Unit,
  onCopyReport: () -> Unit,
) {
  val p = RinTheme.palette
  RinPage(
    stringResource(R.string.diagnostics_title),
    onBack,
    actions = { QuietPillButton(stringResource(R.string.diagnostics_copy), onCopyReport, height = 44.dp) },
    scroll = false,
  ) {
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding =
        PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = 4.dp,
          bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Text(
          stringResource(
            R.string.diagnostics_device,
            state.status.device,
            state.status.androidRelease,
            state.status.appVersion,
          ),
          style = MaterialTheme.typography.bodySmall,
          color = p.muted,
          modifier = Modifier.padding(horizontal = 4.dp),
        )
      }
      item { SectionTitle(stringResource(R.string.diagnostics_checks)) }
      // All the checks on one card, a thin line between them.
      item {
        Column(Modifier.fillMaxWidth().sticker(radius = 22.dp).padding(horizontal = 16.dp, vertical = 6.dp)) {
          state.checks.forEachIndexed { i, check ->
            if (i > 0) HorizontalDivider(color = p.line)
            CheckRow(check, state.status, onFix = { onFix(check.id) })
          }
        }
      }
      item {
        SectionTitle(stringResource(R.string.test_title))
        TestAlarmPanel(state.testRingAt, state.testFailed, onStartTest, onCancelTest, Modifier.padding(top = 10.dp).rinCard())
      }
      item {
        SectionTitle(stringResource(R.string.run_title))
        ReliabilityRunPanel(state.reliabilityRun, Modifier.padding(top = 10.dp).rinCard())
      }
      item {
        SectionTitle(stringResource(R.string.recent_title))
        if (state.recentRings.isEmpty()) {
          Text(stringResource(R.string.recent_empty), color = p.muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
      }
      items(state.recentRings, key = { it.firedAt.toEpochMilli() * 31 + it.alarmId }) { RingRow(it) }
    }
  }
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
  val p = RinTheme.palette
  val timeFormat = rememberTimeFormatter()
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (ringAt == null) {
      Text(stringResource(R.string.test_text), style = MaterialTheme.typography.bodyMedium, color = p.ink)
      PillButton(stringResource(R.string.test_start), onStart, Modifier.fillMaxWidth())
    } else {
      Text(
        stringResource(R.string.test_pending, ringAt.format(timeFormat)),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = p.ink,
      )
      if (cancellable) QuietPillButton(stringResource(R.string.test_cancel), onCancel)
    }
    if (failed) Text(stringResource(R.string.test_failed), color = p.danger, style = MaterialTheme.typography.bodyMedium)
  }
}

/** The Phase 1 exit check, with its rules spelled out so a reset never looks arbitrary. */
@Composable
private fun ReliabilityRunPanel(run: ReliabilityRun, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      pluralStringResource(
        R.plurals.run_count,
        ReliabilityRun.TARGET,
        run.nights.coerceAtMost(ReliabilityRun.TARGET),
        ReliabilityRun.TARGET,
      ),
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
      color = p.ink,
    )
    LinearProgressIndicator(
      progress = { run.nights.coerceAtMost(ReliabilityRun.TARGET) / ReliabilityRun.TARGET.toFloat() },
      modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
      color = p.mint,
      trackColor = p.line,
      gapSize = 0.dp,
      drawStopIndicator = {},
    )
    Text(
      stringResource(if (run.passed) R.string.run_passed else R.string.run_rules),
      style = MaterialTheme.typography.bodyMedium,
      color = p.muted,
    )
    run.breaker?.let { breaker ->
      Text(
        stringResource(R.string.run_reset, dayText(breaker.day), reasonText(breaker.reason)),
        style = MaterialTheme.typography.bodySmall,
        color = p.muted,
      )
    }
  }
}

private fun dayText(day: LocalDate): String = dateText(day)

@Composable
private fun reasonText(reason: ReliabilityRun.Reason): String =
  stringResource(
    when (reason) {
      ReliabilityRun.Reason.MISSED -> R.string.run_reason_missed
      ReliabilityRun.Reason.LATE -> R.string.run_reason_late
      ReliabilityRun.Reason.NO_SOUND -> R.string.run_reason_no_sound
      ReliabilityRun.Reason.FAILED -> R.string.run_reason_failed
      ReliabilityRun.Reason.NO_TIME -> R.string.run_reason_no_time
      ReliabilityRun.Reason.NO_ALARM -> R.string.run_reason_no_alarm
    }
  )

/** One recent ring on its own small card: when, a test tag, how it ended, and how late it fired and sounded. */
@Composable
private fun RingRow(ring: RingSummary) {
  val p = RinTheme.palette
  val timeFormat = rememberTimeFormatter()
  val zone = ZoneId.systemDefault()
  val shown = (ring.scheduledAt ?: ring.firedAt).atZone(zone)
  Column(
    Modifier.fillMaxWidth().sticker(radius = 18.dp, depth = 3.dp).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        "${shown.dayOfWeek.displayName(TextStyle.SHORT)} ${shown.format(timeFormat)}",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
        color = p.ink,
      )
      if (ring.isTest) {
        Text(
          stringResource(R.string.recent_test),
          style = MaterialTheme.typography.labelMedium,
          color = p.onTag,
          modifier = Modifier.background(p.tag, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp, vertical = 2.dp),
        )
      }
    }
    Text(outcomeText(ring.outcome), style = MaterialTheme.typography.bodyMedium, color = p.ink)
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
        color = p.muted,
      )
    }
  }
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

@Preview(heightDp = 1400)
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
      DiagnosticsUiState(
        status,
        SetupChecks.evaluate(status),
        null,
        false,
        rings,
        ReliabilityRun(3, ReliabilityRun.Breaker(LocalDate.of(2026, 9, 24), ReliabilityRun.Reason.LATE, 1)),
      ),
      {},
      {},
      {},
      {},
      {},
    )
  }
}
