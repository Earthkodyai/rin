package io.github.earthkodyai.rinalarm.alarm.ring

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.character.CharacterView
import io.github.earthkodyai.rinalarm.character.Framing
import io.github.earthkodyai.rinalarm.mission.MissionPlan
import io.github.earthkodyai.rinalarm.mission.MissionProgress
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.QrScanner
import io.github.earthkodyai.rinalarm.mission.ScanVerdict
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Full-screen ring UI over the lock screen (showWhenLocked + turnScreenOn in the manifest): Rin head to toe, the
 * ring's mission, Snooze, and a 3 s hold to stop in an emergency (task 3.1). It only mirrors RingState and sends
 * commands to RingService, so the ring keeps going if this screen is closed or never shows (HyperOS full-screen
 * permission off).
 */
@AndroidEntryPoint
class RingActivity : ComponentActivity() {
  private val viewModel: RingViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContent {
      RinAlarmTheme {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.finished) { if (state.finished) finish() }
        LaunchedEffect(viewModel) {
          viewModel.commands.collect { command ->
            when (command) {
              RingCommand.Snooze -> startService(RingService.snoozeIntent(this@RingActivity))
              is RingCommand.Dismiss -> startService(RingService.dismissIntent(this@RingActivity, command.source))
            }
          }
        }
        RingScreen(
          state = state,
          onSnooze = viewModel::snooze,
          onDismiss = viewModel::dismiss,
          onEmergencyStop = viewModel::emergencyStop,
          onOpenCamera = viewModel::openCamera,
          character = { modifier ->
            CharacterView(RingMoods.mood(state.phase), modifier, framing = Framing.FULL, cues = viewModel.cues)
          },
          scanner = { modifier ->
            QrScanner(viewModel::onScan, modifier, onTorch = viewModel::onTorch, onError = viewModel::onCameraError)
          },
        )
      }
    }
  }
}

@Composable
internal fun RingScreen(
  state: RingUiState,
  onSnooze: () -> Unit,
  onDismiss: () -> Unit,
  onEmergencyStop: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenCamera: () -> Unit = {},
  // Slots, so previews and UI tests run without a WebView or a camera.
  character: @Composable (Modifier) -> Unit = {},
  scanner: @Composable (Modifier) -> Unit = {},
) {
  val ring = state.ring ?: return
  val request = ring.request
  Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
    Column(
      Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        request.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
        style = MaterialTheme.typography.displayMedium,
      )
      Text(
        request.label.ifBlank { stringResource(R.string.ring_default_label) },
        style = MaterialTheme.typography.titleMedium,
      )
      if (request.late) {
        Text(stringResource(R.string.ring_late_explained), style = MaterialTheme.typography.bodyMedium)
      }
      // Rin takes what the controls leave: about half of a phone screen.
      character(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
      when {
        state.passed -> Passed()
        state.plainDismiss -> Unit
        ring.mission?.type == MissionType.QR -> QrCard(state, onOpenCamera, scanner)
        else -> WalkCard(state.progress)
      }
      Spacer(Modifier.height(16.dp))
      // Big, far-apart targets: the user is half asleep.
      if (!state.passed) Controls(state, request, onSnooze, onDismiss, onEmergencyStop)
    }
  }
}

@Composable
private fun Controls(
  state: RingUiState,
  request: RingRequest,
  onSnooze: () -> Unit,
  onDismiss: () -> Unit,
  onEmergencyStop: () -> Unit,
) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    if (request.snoozesLeft > 0) {
      OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Text(
          pluralStringResource(
            R.plurals.ring_snooze_left,
            request.snoozesLeft,
            request.options.snoozeMinutes,
            request.snoozesLeft,
          ),
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }
    if (state.plainDismiss) {
      Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(88.dp)) {
        Text(stringResource(R.string.ring_dismiss), style = MaterialTheme.typography.headlineSmall)
      }
    } else {
      HoldToStopButton(onEmergencyStop, Modifier.fillMaxWidth().height(56.dp))
    }
  }
}

@Composable
private fun WalkCard(progress: MissionProgress?) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      val target = progress?.target ?: RingPolicy.WALK_STEPS
      Text(
        pluralStringResource(R.plurals.mission_walk_title, target, target),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(12.dp))
      LinearProgressIndicator(progress = { progress?.fraction ?: 0f }, modifier = Modifier.fillMaxWidth().height(12.dp))
      Spacer(Modifier.height(8.dp))
      Text(
        stringResource(R.string.mission_progress, progress?.done ?: 0, target),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
  }
}

/**
 * The QR mission (task 3.2): a big "Scan sticker" button until tapped, then the camera with a hint about what it sees.
 * It closes itself after QrScanPolicy.CAMERA_IDLE without a sighting or a step; the button opens it again.
 */
@Composable
private fun QrCard(state: RingUiState, onOpenCamera: () -> Unit, scanner: @Composable (Modifier) -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(
      Modifier.padding(16.dp).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(R.string.mission_qr_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
      if (state.cameraOpen) {
        scanner(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)).testTag(QR_SCANNER_TAG))
        Text(
          stringResource(
            when (state.scanHint) {
              ScanVerdict.TOO_FAR -> R.string.mission_qr_closer
              ScanVerdict.OTHER -> R.string.mission_qr_other
              else -> R.string.mission_qr_aim
            }
          ),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
      } else {
        Text(stringResource(R.string.mission_qr_walk), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth().height(64.dp)) {
          Text(stringResource(R.string.mission_qr_open), style = MaterialTheme.typography.titleMedium)
        }
      }
    }
  }
}

internal const val QR_SCANNER_TAG = "ring_qr_scanner"

@Composable
private fun Passed() {
  Text(
    stringResource(R.string.mission_passed),
    style = MaterialTheme.typography.headlineSmall,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
  )
}

private val PREVIEW_RING =
  ActiveRing(
    RingRequest(1, LocalTime.of(7, 0), "Gym", null, 1, 2, late = false, RingOptions()),
    MissionPlan.Run(MissionType.WALK),
  )

@Preview
@Composable
private fun RingScreenMissionPreview() {
  RinAlarmTheme { RingScreen(RingUiState(PREVIEW_RING, MissionProgress(12, 30), RingPhase.WORKING), {}, {}, {}) }
}

@Preview
@Composable
private fun RingScreenQrPreview() {
  RinAlarmTheme {
    RingScreen(
      RingUiState(PREVIEW_RING.copy(mission = MissionPlan.Run(MissionType.QR)), MissionProgress(0, 1), cameraOpen = true, scanHint = ScanVerdict.TOO_FAR),
      {},
      {},
      {},
    )
  }
}

@Preview
@Composable
private fun RingScreenPlainPreview() {
  RinAlarmTheme { RingScreen(RingUiState(PREVIEW_RING.copy(mission = null)), {}, {}, {}) }
}
