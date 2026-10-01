package io.github.earthkodyai.rinalarm.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.setup.CheckId
import io.github.earthkodyai.rinalarm.setup.CheckResult
import io.github.earthkodyai.rinalarm.setup.DeviceStatus
import io.github.earthkodyai.rinalarm.setup.Severity
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.SmallPillButton

/**
 * One setup check: status icon, name, what it means right now, and a Fix/Open pill when there is one. Laid out for a
 * row inside a card (Diagnostics' checks card).
 */
@Composable
fun CheckRow(check: CheckResult, status: DeviceStatus, onFix: () -> Unit, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    SeverityIcon(check.severity)
    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(checkTitle(check.id), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = p.ink)
      Text(checkText(check, status), style = MaterialTheme.typography.bodyMedium, color = p.muted)
      if (check.id == CheckId.FULL_SCREEN && check.severity != Severity.OK && status.xiaomiFamily) {
        Text(stringResource(R.string.check_full_screen_xiaomi), style = MaterialTheme.typography.bodySmall, color = p.muted)
      }
    }
    fixLabel(check)?.let { label -> SmallPillButton(stringResource(label), onFix, Modifier.padding(start = 10.dp)) }
  }
}

@Composable
fun SeverityIcon(severity: Severity) {
  val p = RinTheme.palette
  val (icon, tint, description) =
    when (severity) {
      Severity.OK -> Triple(R.drawable.ic_check_circle, p.mintText, R.string.check_status_ok)
      Severity.INFO -> Triple(R.drawable.ic_info, p.muted, R.string.check_status_info)
      Severity.WARNING -> Triple(R.drawable.ic_warning, if (p.night) NIGHT_AMBER else DAY_AMBER, R.string.check_status_warning)
      // The setup banner's red by day; at night it is too dark on the navy card, so the light danger red.
      Severity.CRITICAL -> Triple(R.drawable.ic_error, if (p.night) p.danger else p.alert, R.string.check_status_critical)
    }
  Icon(painterResource(icon), contentDescription = stringResource(description), tint = tint)
}

/** Amber that reads as "warning": deep enough by day to show on the white card, light at night on the navy one. */
private val DAY_AMBER = Color(0xFFB86E00)
private val NIGHT_AMBER = Color(0xFFFFC24D)

private fun fixLabel(check: CheckResult): Int? =
  when {
    // Nothing reads these HyperOS settings, so they always offer their page.
    check.id == CheckId.AUTOSTART || check.id == CheckId.LOCK_SCREEN -> R.string.check_open
    check.severity == Severity.OK -> null
    check.id == CheckId.MICROPHONE -> R.string.mission_allow
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
      CheckId.MICROPHONE -> R.string.check_microphone
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
    CheckId.MICROPHONE -> stringResource(if (ok) R.string.check_microphone_ok else R.string.check_microphone_info)
  }
}
