package io.github.earthkodyai.rinalarm.ui.onboarding

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.character.CharacterAssets
import io.github.earthkodyai.rinalarm.character.Framing
import io.github.earthkodyai.rinalarm.character.Mood
import io.github.earthkodyai.rinalarm.setup.CheckId
import io.github.earthkodyai.rinalarm.setup.DeviceStatus
import io.github.earthkodyai.rinalarm.setup.SettingsLinks
import io.github.earthkodyai.rinalarm.setup.Severity
import io.github.earthkodyai.rinalarm.setup.rememberNotificationPermissionAction
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.ConfirmKind
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.QuietPillButton
import io.github.earthkodyai.rinalarm.ui.common.RinConfirmDialog
import io.github.earthkodyai.rinalarm.ui.common.rinCard
import io.github.earthkodyai.rinalarm.ui.diagnostics.TestAlarmPanel
import io.github.earthkodyai.rinalarm.ui.setup.SeverityIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * First-launch setup, one permission per page with the reason next to it (03-mvp: ask in context). Every step can be
 * skipped; skipping a step that can hide or delay alarms asks first, and Diagnostics plus the main-screen banner
 * pick it up afterwards. Rin's AI disclosure (05e) is on the welcome page, under her.
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
  val p = RinTheme.palette

  Box(Modifier.fillMaxSize().background(p.ground)) {
    Column(
      Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      StepDots(state.index, state.steps.size)
      when (state.step) {
        OnboardingStep.WELCOME -> {
          RinWelcome()
          Page(R.string.welcome_title, R.string.welcome_text)
          Text(stringResource(R.string.about_rin), style = MaterialTheme.typography.bodyMedium, color = p.ink, modifier = Modifier.rinCard())
          PillButton(stringResource(R.string.onboarding_start), onNext, Modifier.fillMaxWidth())
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
          Page(R.string.fsi_title, R.string.fsi_text, note = if (status.xiaomiFamily) R.string.check_full_screen_xiaomi else null)
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
          QuietPillButton(stringResource(R.string.brand_lock_screen), { onOpenSettings(CheckId.LOCK_SCREEN) }, Modifier.fillMaxWidth())
          QuietPillButton(stringResource(R.string.brand_autostart), { onOpenSettings(CheckId.AUTOSTART) }, Modifier.fillMaxWidth())
          QuietPillButton(stringResource(R.string.brand_battery), { onOpenSettings(CheckId.BATTERY) }, Modifier.fillMaxWidth())
          Spacer(Modifier.height(8.dp))
          PillButton(stringResource(R.string.onboarding_next), onNext, Modifier.fillMaxWidth())
        }
        OnboardingStep.TEST -> {
          Title(R.string.test_title)
          TestAlarmPanel(state.testRingAt, state.testFailed, onStart = onStartTest, onCancel = {}, Modifier.rinCard(), cancellable = false)
          Spacer(Modifier.height(16.dp))
          // Finishing with a test pending is fine: it still rings, wherever the user is.
          if (state.testStarted) {
            PillButton(stringResource(R.string.onboarding_finish), onFinish, Modifier.fillMaxWidth())
          } else {
            QuietTextButton(R.string.onboarding_skip_test, onFinish)
          }
        }
      }
    }
  }

  confirmSkip?.let { step ->
    // The safe choice is the pink one; skipping is the quiet pill.
    RinConfirmDialog(
      title = stringResource(R.string.skip_title),
      icon = R.drawable.ic_warning,
      safeLabel = stringResource(R.string.skip_cancel),
      actionLabel = stringResource(R.string.skip_confirm),
      onSafe = { confirmSkip = null },
      onAction = {
        confirmSkip = null
        onNext()
      },
      text = stringResource(if (step == OnboardingStep.NOTIFICATIONS) R.string.skip_notifications_text else R.string.skip_exact_text),
      kind = ConfirmKind.WARNING,
    )
  }
}

/** Where the user is in the setup: a dot per step, pink up to this one; screen readers get "Step 2 of 6" instead. */
@Composable
private fun StepDots(index: Int, count: Int) {
  val p = RinTheme.palette
  val label = stringResource(R.string.onboarding_step, index + 1, count)
  Row(
    Modifier.fillMaxWidth().padding(vertical = 4.dp).clearAndSetSemantics { contentDescription = label },
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    for (i in 0 until count) {
      Box(Modifier.height(8.dp).width(if (i == index) 24.dp else 8.dp).background(if (i <= index) p.primary else p.line, CircleShape))
    }
  }
}

/**
 * Rin on the welcome page, waist up on her panel with the sun (or the moon), cheerful: her still image, not the live
 * page, so the first launch stays light. Builds without a model show the panel alone.
 */
@Composable
private fun RinWelcome() {
  val p = RinTheme.palette
  val context = LocalContext.current
  val still by
    produceState<ImageBitmap?>(null) {
      val path = CharacterAssets.stills(context, Framing.WAIST).let { it[Mood.CHEERFUL] ?: it.values.firstOrNull() }
      value =
        path?.let {
          withContext(Dispatchers.IO) {
            runCatching { context.assets.open(it).use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
          }
        }
    }
  val shape = RoundedCornerShape(28.dp)
  Box(
    Modifier.fillMaxWidth()
      .height(WELCOME_PANEL_HEIGHT)
      .clip(shape)
      .background(p.rinCard)
      .let { if (p.night) it.border(1.dp, p.line, shape) else it }
  ) {
    Box(
      Modifier.align(Alignment.TopEnd)
        .offset(x = (-24).dp, y = 18.dp)
        .size(if (p.night) 64.dp else 96.dp)
        .background(if (p.night) p.glow.copy(alpha = 0.9f) else p.glow, CircleShape)
    )
    still?.let {
      Image(
        it,
        contentDescription = null,
        contentScale = ContentScale.FillHeight,
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxHeight().padding(top = 8.dp),
      )
    }
  }
}

private val WELCOME_PANEL_HEIGHT = 200.dp

@Composable
private fun Title(title: Int) {
  Text(
    stringResource(title),
    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    color = RinTheme.palette.ink,
    modifier = Modifier.padding(top = 4.dp).semantics { heading() },
  )
}

/** The step's title on the ground and why it matters on a card, with a [note] under it for some phones. */
@Composable
private fun Page(title: Int, text: Int, note: Int? = null) {
  val p = RinTheme.palette
  Title(title)
  Column(Modifier.rinCard(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = p.ink)
    if (note != null) Text(stringResource(note), style = MaterialTheme.typography.bodyMedium, color = p.muted)
  }
}

/** "Not now" and "Skip the test": plain text well away from the pink pill, never the button a thumb lands on. */
@Composable
private fun QuietTextButton(label: Int, onClick: () -> Unit) {
  TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = RinTheme.palette.muted)
  }
}

/** Granted: a check mark and Next. Not granted: the action, and "Not now" well below it. */
@Composable
private fun PermissionStep(granted: Boolean, action: Int, onAction: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
  if (granted) {
    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SeverityIcon(Severity.OK)
      Text(
        stringResource(R.string.onboarding_allowed),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = RinTheme.palette.mintText,
      )
    }
    PillButton(stringResource(R.string.onboarding_next), onNext, Modifier.fillMaxWidth())
  } else {
    PillButton(stringResource(action), onAction, Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    QuietTextButton(R.string.onboarding_not_now, onSkip)
  }
}

@Preview
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
