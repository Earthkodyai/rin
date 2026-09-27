package io.github.earthkodyai.rinalarm.ui.onboarding

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.setup.CheckId
import io.github.earthkodyai.rinalarm.setup.DeviceStatus
import io.github.earthkodyai.rinalarm.setup.SettingsLinks
import io.github.earthkodyai.rinalarm.setup.Severity
import io.github.earthkodyai.rinalarm.setup.rememberNotificationPermissionAction
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.ui.diagnostics.TestAlarmPanel
import io.github.earthkodyai.rinalarm.ui.setup.SeverityIcon

/**
 * First-launch setup, one permission per page with the reason next to it (03-mvp: ask in context). Every step can be
 * skipped; skipping a step that can hide or delay alarms asks first, and Diagnostics plus the main-screen banner
 * pick it up afterwards. Rin's AI disclosure (05e) joins the welcome page when she arrives in Phase 2.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  LifecycleResumeEffect(viewModel) {
    viewModel.refresh()
    onPauseOrDispose {}
  }
  BackHandler(enabled = state.index > 0) { viewModel.back() }
  val context = LocalContext.current
  val openFailed = stringResource(R.string.settings_open_failed)
  val testLabel = stringResource(R.string.test_label)
  val notifications = rememberNotificationPermissionAction(state.status.xiaomiFamily, onResult = viewModel::refresh)
  OnboardingScreen(
    state = state,
    notificationsOpenSettings = notifications.opensSettings,
    onAllowNotifications = notifications.run,
    onOpenSettings = { id ->
      if (!SettingsLinks.open(context, id, state.status.xiaomiFamily)) {
        Toast.makeText(context, openFailed, Toast.LENGTH_LONG).show()
      }
    },
    onNext = viewModel::next,
    onStartTest = { viewModel.startTest(testLabel) },
    onFinish = { viewModel.finish(onDone) },
  )
}

@Composable
internal fun OnboardingScreen(
  state: OnboardingUiState,
  notificationsOpenSettings: Boolean,
  onAllowNotifications: () -> Unit,
  onOpenSettings: (CheckId) -> Unit,
  onNext: () -> Unit,
  onStartTest: () -> Unit,
  onFinish: () -> Unit,
) {
  // The step whose skip is being confirmed; saved so a rotation keeps the dialog.
  var confirmSkip by rememberSaveable { mutableStateOf<OnboardingStep?>(null) }
  val status = state.status

  Surface(Modifier.fillMaxSize()) {
    Column(
      Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        stringResource(R.string.onboarding_step, state.index + 1, state.steps.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      when (state.step) {
        OnboardingStep.WELCOME -> {
          Page(R.string.welcome_title, R.string.welcome_text)
          PrimaryButton(R.string.onboarding_start, onNext)
        }
        OnboardingStep.NOTIFICATIONS -> {
          Page(R.string.notif_title, R.string.notif_text)
          PermissionStep(
            granted = status.notificationsAllowed,
            action = if (notificationsOpenSettings) R.string.onboarding_open_settings else R.string.notif_allow,
            onAction = onAllowNotifications,
            onNext = onNext,
            onSkip = { confirmSkip = OnboardingStep.NOTIFICATIONS },
          )
        }
        OnboardingStep.FULL_SCREEN -> {
          Page(R.string.fsi_title, R.string.fsi_text)
          if (status.xiaomiFamily) Text(stringResource(R.string.check_full_screen_xiaomi))
          // Only a warning (the sound is unaffected), so skipping needs no confirmation.
          PermissionStep(
            granted = status.fullScreenAllowed != false,
            action = R.string.onboarding_open_settings,
            onAction = { onOpenSettings(CheckId.FULL_SCREEN) },
            onNext = onNext,
            onSkip = onNext,
          )
        }
        OnboardingStep.EXACT_ALARMS -> {
          Page(R.string.exact_title, R.string.exact_text)
          PermissionStep(
            granted = status.exactAlarmsAllowed,
            action = R.string.onboarding_open_settings,
            onAction = { onOpenSettings(CheckId.EXACT_ALARMS) },
            onNext = onNext,
            onSkip = { confirmSkip = OnboardingStep.EXACT_ALARMS },
          )
        }
        OnboardingStep.PHONE_BRAND -> {
          Page(R.string.brand_title, R.string.brand_text)
          OutlinedButton(onClick = { onOpenSettings(CheckId.LOCK_SCREEN) }, Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.brand_lock_screen))
          }
          OutlinedButton(onClick = { onOpenSettings(CheckId.AUTOSTART) }, Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.brand_autostart))
          }
          OutlinedButton(onClick = { onOpenSettings(CheckId.BATTERY) }, Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.brand_battery))
          }
          Spacer(Modifier.height(8.dp))
          PrimaryButton(R.string.onboarding_next, onNext)
        }
        OnboardingStep.TEST -> {
          Title(R.string.test_title)
          TestAlarmPanel(state.testRingAt, state.testFailed, onStart = onStartTest, onCancel = {}, cancellable = false)
          Spacer(Modifier.height(24.dp))
          // Finishing with a test pending is fine: it still rings, wherever the user is.
          if (state.testStarted) {
            PrimaryButton(R.string.onboarding_finish, onFinish)
          } else {
            TextButton(onClick = onFinish, Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_skip_test)) }
          }
        }
      }
    }
  }

  confirmSkip?.let { step ->
    AlertDialog(
      onDismissRequest = { confirmSkip = null },
      title = { Text(stringResource(R.string.skip_title)) },
      text = {
        Text(
          stringResource(
            if (step == OnboardingStep.NOTIFICATIONS) R.string.skip_notifications_text else R.string.skip_exact_text
          )
        )
      },
      // The safe choice is the prominent one; skipping is the plain text button.
      confirmButton = { Button(onClick = { confirmSkip = null }) { Text(stringResource(R.string.skip_cancel)) } },
      dismissButton = {
        TextButton(
          onClick = {
            confirmSkip = null
            onNext()
          }
        ) {
          Text(stringResource(R.string.skip_confirm))
        }
      },
    )
  }
}

@Composable
private fun Title(title: Int) {
  Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Page(title: Int, text: Int) {
  Title(title)
  Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PrimaryButton(label: Int, onClick: () -> Unit) {
  Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(label)) }
}

/** Granted: a check mark and Next. Not granted: the action, and "Not now" well below it. */
@Composable
private fun PermissionStep(granted: Boolean, action: Int, onAction: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
  if (granted) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SeverityIcon(Severity.OK)
      Text(stringResource(R.string.onboarding_allowed), style = MaterialTheme.typography.titleMedium)
    }
    PrimaryButton(R.string.onboarding_next, onNext)
  } else {
    PrimaryButton(action, onAction)
    Spacer(Modifier.height(24.dp))
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_not_now)) }
  }
}

@Preview(showBackground = true)
@Composable
private fun NotificationsStepPreview() {
  val status = DeviceStatus(35, false, false, true, false, false, 7, 15, false, true, "Xiaomi 2406APNFAG", "15", "0.1.0")
  RinAlarmTheme {
    OnboardingScreen(
      OnboardingUiState(OnboardingSteps.plan(status), 1, status, null, testStarted = false, testFailed = false),
      notificationsOpenSettings = false,
      onAllowNotifications = {},
      onOpenSettings = {},
      onNext = {},
      onStartTest = {},
      onFinish = {},
    )
  }
}
