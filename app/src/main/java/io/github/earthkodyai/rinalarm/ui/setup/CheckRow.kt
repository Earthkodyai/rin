package io.github.earthkodyai.rinalarm.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.setup.CheckId
import io.github.earthkodyai.rinalarm.setup.CheckResult
import io.github.earthkodyai.rinalarm.setup.DeviceStatus
import io.github.earthkodyai.rinalarm.setup.Severity

/** One setup check: status icon, name, what it means right now, and a Fix/Open button when there is one. */
@Composable
fun CheckRow(check: CheckResult, status: DeviceStatus, onFix: () -> Unit) {
  ListItem(
    leadingContent = { SeverityIcon(check.severity) },
    headlineContent = { Text(checkTitle(check.id)) },
    supportingContent = {
      Column {
        Text(checkText(check, status))
        if (check.id == CheckId.FULL_SCREEN && check.severity != Severity.OK && status.xiaomiFamily) {
          Text(stringResource(R.string.check_full_screen_xiaomi), style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    trailingContent =
      fixLabel(check)?.let { label -> { TextButton(onClick = onFix) { Text(stringResource(label)) } } },
  )
}

@Composable
fun SeverityIcon(severity: Severity) {
  val (icon, tint, description) =
    when (severity) {
      Severity.OK -> Triple(R.drawable.ic_check_circle, MaterialTheme.colorScheme.primary, R.string.check_status_ok)
      Severity.INFO -> Triple(R.drawable.ic_info, MaterialTheme.colorScheme.onSurfaceVariant, R.string.check_status_info)
      Severity.WARNING -> Triple(R.drawable.ic_warning, WARNING_AMBER, R.string.check_status_warning)
      Severity.CRITICAL -> Triple(R.drawable.ic_error, MaterialTheme.colorScheme.error, R.string.check_status_critical)
    }
  Icon(painterResource(icon), contentDescription = stringResource(description), tint = tint)
}

/** Material's amber 800: reads as "warning" on both light and dark surfaces, unlike the theme's tertiary. */
private val WARNING_AMBER = Color(0xFFFF8F00)

private fun fixLabel(check: CheckResult): Int? =
  when {
    // Nothing reads these HyperOS settings, so they always offer their page.
    check.id == CheckId.AUTOSTART || check.id == CheckId.LOCK_SCREEN -> R.string.check_open
    check.severity == Severity.OK -> null
    else -> R.string.check_fix
  }

@Composable
private fun checkTitle(id: CheckId): String =
  stringResource(
    when (id) {
      CheckId.NOTIFICATIONS -> R.string.check_notifications
      CheckId.FULL_SCREEN -> R.string.check_full_screen
      CheckId.EXACT_ALARMS -> R.string.check_exact
      CheckId.ALARM_VOLUME -> R.string.check_volume
      CheckId.DO_NOT_DISTURB -> R.string.check_dnd
      CheckId.BATTERY -> R.string.check_battery
      CheckId.LOCK_SCREEN -> R.string.check_lock_screen
      CheckId.AUTOSTART -> R.string.check_autostart
      CheckId.MISSIONS -> R.string.check_missions
    }
  )

@Composable
private fun checkText(check: CheckResult, status: DeviceStatus): String {
  val ok = check.severity == Severity.OK
  return when (check.id) {
    CheckId.NOTIFICATIONS ->
      stringResource(if (ok) R.string.check_notifications_ok else R.string.check_notifications_problem)
    CheckId.FULL_SCREEN -> stringResource(if (ok) R.string.check_full_screen_ok else R.string.check_full_screen_problem)
    CheckId.EXACT_ALARMS -> stringResource(if (ok) R.string.check_exact_ok else R.string.check_exact_problem)
    CheckId.ALARM_VOLUME ->
      stringResource(
        if (ok) R.string.check_volume_ok else R.string.check_volume_low,
        status.alarmVolume,
        status.alarmVolumeMax,
      )
    CheckId.DO_NOT_DISTURB -> stringResource(if (ok) R.string.check_dnd_ok else R.string.check_dnd_problem)
    CheckId.BATTERY -> stringResource(if (ok) R.string.check_battery_ok else R.string.check_battery_info)
    CheckId.LOCK_SCREEN -> stringResource(R.string.check_lock_screen_info)
    CheckId.AUTOSTART -> stringResource(R.string.check_autostart_info)
    CheckId.MISSIONS ->
      if (ok) {
        stringResource(R.string.check_missions_ok, status.readyMissions.orEmpty().joinToString())
      } else {
        stringResource(R.string.check_missions_problem)
      }
  }
}
