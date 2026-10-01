package io.github.earthkodyai.rinalarm.alarm.ring

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import javax.inject.Inject
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.character.CharacterView
import io.github.earthkodyai.rinalarm.character.CupsView
import io.github.earthkodyai.rinalarm.character.Framing
import io.github.earthkodyai.rinalarm.character.Mood
import io.github.earthkodyai.rinalarm.dialogue.Line
import io.github.earthkodyai.rinalarm.mission.CupsAct
import io.github.earthkodyai.rinalarm.mission.CupsPhase
import io.github.earthkodyai.rinalarm.mission.CupsState
import io.github.earthkodyai.rinalarm.mission.MissionPlan
import io.github.earthkodyai.rinalarm.mission.MissionProgress
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.Miss
import io.github.earthkodyai.rinalarm.mission.Pad
import io.github.earthkodyai.rinalarm.mission.PadsPhase
import io.github.earthkodyai.rinalarm.mission.PadsState
import io.github.earthkodyai.rinalarm.mission.QrScanner
import io.github.earthkodyai.rinalarm.mission.ScanVerdict
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinThemedContent
import io.github.earthkodyai.rinalarm.theme.ThemeClock
import io.github.earthkodyai.rinalarm.ui.common.RinLine
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Full-screen ring UI over the lock screen (showWhenLocked + turnScreenOn in the manifest): Rin head to toe, the
 * ring's mission (a game with her since task 3.3: colour pads, the cup shuffle at her table since 3.4, and repeat
 * after Rin since 3.5), Snooze, and a 3 s hold to stop in an emergency (task 3.1). It only mirrors RingState and sends
 * commands to RingService, so the ring keeps going if this screen is closed or never shows (HyperOS full-screen
 * permission off).
 */
@AndroidEntryPoint
class RingActivity : ComponentActivity() {
  private val viewModel: RingViewModel by viewModels()
  @Inject lateinit var themeClock: ThemeClock

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContent {
      RinThemedContent(themeClock) {
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
          onStartGame = viewModel::startGame,
          onTapPad = viewModel::tapPad,
          onPickCup = viewModel::pickCup,
          onHearAgain = viewModel::hearAgain,
          onTapWord = viewModel::tapWord,
          onCantTalk = viewModel::cantTalk,
          onClose = viewModel::close,
          character = { modifier ->
            CharacterView(
              state.mood,
              modifier,
              framing = Framing.FULL,
              cues = viewModel.cues,
              // Her page acts the cups out until the 2D board takes over (then the table goes away behind it).
              cups =
                when {
                  state.cups2d -> null
                  state.cupsStaging && state.cups?.act == null -> CupsAct.Rest(ball = 1, at = 0)
                  else -> state.cups?.act
                },
              onCups = viewModel::onCupsView,
              speech = viewModel.speaking,
              onVisible = viewModel::onCharacterVisible,
            )
          },
          hand = { modifier -> RinHand(modifier) },
          scanner = { modifier ->
            QrScanner(viewModel::onScan, modifier, onTorch = viewModel::onTorch, onError = viewModel::onCameraError)
          },
        )
      }
    }
  }

  // A plain startService: the service is already in the foreground while it rings; after the ring it ignores these.
  override fun onStart() {
    super.onStart()
    runCatching { startService(RingService.screenIntent(this, shown = true)) }
  }

  override fun onStop() {
    runCatching { startService(RingService.screenIntent(this, shown = false)) }
    super.onStop()
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
  onStartGame: () -> Unit = {},
  onTapPad: (Pad) -> Unit = {},
  onPickCup: (Int) -> Unit = {},
  onHearAgain: () -> Unit = {},
  onTapWord: (Int) -> Unit = {},
  onCantTalk: () -> Unit = {},
  onClose: () -> Unit = {},
  // Slots, so previews and UI tests run without a WebView or a camera.
  character: @Composable (Modifier) -> Unit = {},
  scanner: @Composable (Modifier) -> Unit = {},
  hand: @Composable (Modifier) -> Unit = { DrawnHand(it) },
) {
  val ring = state.ring ?: return
  val request = ring.request
  // Once the ring is over (a pass, a snooze, the emergency stop) a tap anywhere closes the screen, cutting her short.
  val closable = state.passed || state.leaving
  Surface(
    modifier
      .fillMaxSize()
      .clickable(enabled = closable, interactionSource = null, indication = null, onClick = onClose)
      .testTag(RING_SCREEN_TAG),
    color = MaterialTheme.colorScheme.primaryContainer,
  ) {
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
      // The game being played: the planned one, or the one "Can't talk right now" switched to.
      val game = state.missionType ?: ring.mission?.type
      val playing = !state.plainDismiss && !state.passed && !state.leaving
      val pads = state.pads.takeIf { game == MissionType.PADS && playing }
      val cups = state.cups.takeIf { game == MissionType.CUPS && playing }
      // Rin takes what the controls leave: about half of a phone screen. The colour pads cover her while the game is
      // on (only her hand shows), and step aside when she scolds (D17, the user's pick).
      Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
        character(Modifier.fillMaxSize())
        when (pads?.phase) {
          PadsPhase.DEMO,
          PadsPhase.INPUT -> PadsBoard(pads, onTapPad, Modifier.fillMaxSize(), hand)
          else -> Unit
        }
        if (cups != null && cups.phase != CupsPhase.READY) {
          // Her page's cups once it shows them; until then (or if it never does) the native board.
          val x = (state.cupsView as? CupsView.Shown)?.x?.takeIf { !state.cups2d }
          if (x != null || state.cups2d) CupsLayer(cups, x, onPickCup, Modifier.fillMaxSize())
        }
      }
      // Her line in its own band under her (4.3): it covers neither her, the cup table nor the pads.
      RinLine(state.line?.text, textModifier = Modifier.testTag(RIN_LINE_TAG))
      // After a pass, a snooze or the emergency stop, the game card and the controls stay as invisible, inert space, so
      // Rin's view keeps its size while she claps or says her last line: when they went, her view grew ~250 dp in one
      // frame and her page reframed, which the user saw as a shake at the end (4.3; the cups had this since 3.4).
      val over = state.passed || state.leaving
      val inert = Modifier.alpha(0f).clearAndSetSemantics {}
      Box(contentAlignment = Alignment.Center) {
        Box(if (over && game != MissionType.CUPS) inert else Modifier) {
          when {
            state.plainDismiss -> Unit
            game == MissionType.QR -> QrCard(state, onOpenCamera, scanner)
            game == MissionType.PADS -> PadsCard(state.pads, onStartGame)
            // The cups keep their card visible through the pass: she claps behind the table.
            game == MissionType.CUPS -> CupsCard(state.cups, state.cupsStaging, state.passed, onStartGame)
            game == MissionType.SPEECH ->
              RepeatCard(state.repeat, state.rinSpeaking, state.micLevel, onStartGame, onHearAgain, onTapWord, onCantTalk)
          }
        }
        if (state.passed && game != MissionType.CUPS) Passed()
      }
      Spacer(Modifier.height(16.dp))
      // Big, far-apart targets: the user is half asleep. Same size, invisible and inert once the ring is over.
      Box(if (over) inert else Modifier) {
        Controls(state, request, if (over) ({}) else onSnooze, if (over) ({}) else onDismiss, if (over) ({}) else onEmergencyStop)
      }
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
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      stringResource(R.string.mission_passed),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(stringResource(R.string.ring_tap_to_close), style = MaterialTheme.typography.bodyMedium)
  }
}

internal const val RING_SCREEN_TAG = "ring_screen"
internal const val RIN_LINE_TAG = "rin_line"

private val PREVIEW_RING =
  ActiveRing(
    RingRequest(1, LocalTime.of(7, 0), "Gym", null, 1, 2, late = false, RingOptions()),
    MissionPlan.Run(MissionType.PADS),
  )

@Preview
@Composable
private fun RingScreenPadsReadyPreview() {
  RinAlarmTheme { RingScreen(RingUiState(PREVIEW_RING, MissionProgress(0, 3), pads = PadsState()), {}, {}, {}) }
}

@Preview
@Composable
private fun RingScreenPadsDemoPreview() {
  val pads =
    PadsState(PadsPhase.DEMO, round = 1, sequence = listOf(Pad.RED, Pad.GREEN, Pad.BLUE, Pad.YELLOW), hand = Pad.GREEN, pressing = true, lit = Pad.GREEN)
  RinAlarmTheme { RingScreen(RingUiState(PREVIEW_RING, MissionProgress(1, 3), RingPhase.WORKING, pads = pads), {}, {}, {}) }
}

@Preview
@Composable
private fun RingScreenPadsScoldPreview() {
  val pads = PadsState(PadsPhase.SCOLD, round = 0, sequence = listOf(Pad.RED, Pad.GREEN, Pad.BLUE), miss = Miss.SLOW)
  val line = Line("pads.slow.01", "pads.slow", "Too slow~ Is the blanket helping you?", Mood.POUTY, null)
  RinAlarmTheme { RingScreen(RingUiState(PREVIEW_RING, MissionProgress(0, 3), pads = pads, line = line), {}, {}, {}) }
}

@Preview
@Composable
private fun RingScreenCups2dPreview() {
  val act = CupsAct.Shuffle(ball = 1, at = 0, swaps = listOf(0 to 2), leadMs = 0, swapMs = 450, gapMs = 150, exitMs = 250)
  val cups = CupsState(CupsPhase.SHUFFLE, streak = 1, act = act)
  RinAlarmTheme {
    RingScreen(
      RingUiState(PREVIEW_RING.copy(mission = MissionPlan.Run(MissionType.CUPS)), MissionProgress(1, 3), cups = cups, cups2d = true),
      {},
      {},
      {},
    )
  }
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
