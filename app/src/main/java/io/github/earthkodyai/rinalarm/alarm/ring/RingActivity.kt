package io.github.earthkodyai.rinalarm.alarm.ring

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.character.CharacterInsets
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
import io.github.earthkodyai.rinalarm.mission.RepeatPhase
import io.github.earthkodyai.rinalarm.mission.ScanVerdict
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.theme.RinThemedContent
import io.github.earthkodyai.rinalarm.theme.ThemeClock
import io.github.earthkodyai.rinalarm.ui.common.BubbleDots
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.RinBubble
import io.github.earthkodyai.rinalarm.ui.common.rememberClockText
import io.github.earthkodyai.rinalarm.ui.common.sticker
import java.time.LocalTime

/**
 * Full-screen ring UI over the lock screen (showWhenLocked + turnScreenOn in the manifest): Rin waist up (UX.4; head
 * to toe until then), the ring's mission (a game with her since task 3.3: colour pads, the cup shuffle at her table
 * since 3.4, and repeat after Rin since 3.5), Snooze, and a 3 s hold to stop in an emergency (task 3.1). It only
 * mirrors RingState and sends commands to RingService, so the ring keeps going if this screen is closed or never shows
 * (HyperOS full-screen permission off).
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
          character = { modifier, insets ->
            CharacterView(
              state.mood,
              modifier,
              framing = Framing.RING,
              insets = insets,
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
  character: @Composable (Modifier, CharacterInsets) -> Unit = { _, _ -> },
  scanner: @Composable (Modifier) -> Unit = {},
  hand: @Composable (Modifier) -> Unit = { DrawnHand(it) },
) {
  val ring = state.ring ?: return
  val request = ring.request
  val p = RinTheme.palette
  // Once the ring is over (a pass, a snooze, the emergency stop) a tap anywhere closes the screen, cutting her short.
  val closable = state.passed || state.leaving
  // The game being played: the planned one, or the one "Can't talk right now" switched to.
  val game = state.missionType ?: ring.mission?.type
  val playing = !state.plainDismiss && !state.passed && !state.leaving
  val pads = state.pads.takeIf { game == MissionType.PADS && playing }
  val cups = state.cups.takeIf { game == MissionType.CUPS && playing }
  // From "Let's play" on, the time steps aside to a compact bar with the score, and stays so to the end.
  val started = !state.plainDismiss && gameStarted(state, game)

  // Rin's view fills the screen and never resizes (a resize reframed her mid-ring: the "shake at the end", 4.3); the
  // top bar and the sheet lie over it, and her page frames her, or the cup table, into the open part between them.
  val density = LocalDensity.current
  var topPx by remember { mutableIntStateOf(0) }
  var sheetPx by remember { mutableIntStateOf(0) }
  val top = with(density) { topPx.toDp() }
  val bottom = with(density) { sheetPx.toDp() }
  Box(
    modifier
      .fillMaxSize()
      .background(p.ground)
      .clickable(enabled = closable, interactionSource = null, indication = null, onClick = onClose)
      .testTag(RING_SCREEN_TAG)
  ) {
    Glow(Modifier.align(Alignment.TopEnd).padding(top = top * 0.6f))
    if (p.night) Stars(Modifier.fillMaxSize())
    // Created once both are measured, so her page starts framed for them.
    if (topPx > 0 && sheetPx > 0) character(Modifier.fillMaxSize(), CharacterInsets(top, bottom))

    Column(Modifier.fillMaxSize()) {
      Spacer(Modifier.height(top))
      Box(Modifier.weight(1f).fillMaxWidth()) {
        // The colour pads cover her while the game is on (only her hand shows), and step aside when she scolds (D17).
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
        RinBubble(
          state.line?.text,
          Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 12.dp),
          dots = BubbleDots.BELOW_LEFT,
          textModifier = Modifier.testTag(RIN_LINE_TAG),
        )
      }
      Spacer(Modifier.height(bottom))
    }

    TopBar(
      request,
      started,
      state,
      game,
      Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topPx = it.height }.statusBarsPadding(),
    )
    Sheet(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { sheetPx = it.height },
    ) {
      // After a pass, a snooze or the emergency stop, the game section and the controls stay as invisible, inert
      // space, so the sheet keeps its height while she claps or says her last line.
      val over = state.passed || state.leaving
      val inert = Modifier.alpha(0f).clearAndSetSemantics {}
      Box(contentAlignment = Alignment.Center) {
        Box(if (over && game != MissionType.CUPS) inert else Modifier) {
          when {
            state.plainDismiss -> Unit
            game == MissionType.QR -> QrSection(state, onOpenCamera, scanner)
            game == MissionType.PADS -> PadsCard(state.pads, onStartGame)
            // The cups keep their line visible through the pass: she claps behind the table.
            game == MissionType.CUPS -> CupsCard(state.cups, state.cupsStaging, state.passed, onStartGame)
            game == MissionType.SPEECH ->
              RepeatCard(state.repeat, state.rinSpeaking, state.micLevel, onStartGame, onHearAgain, onTapWord, onCantTalk)
          }
        }
        if (state.passed && game != MissionType.CUPS) Passed()
      }
      // Big targets: the user is half asleep. Same size, invisible and inert once the ring is over.
      Box(if (over) inert else Modifier) {
        Controls(state, request, if (over) ({}) else onSnooze, if (over) ({}) else onDismiss, if (over) ({}) else onEmergencyStop)
      }
    }
  }
}

/** Whether "Let's play" has been pressed: the game is under way, or over. */
private fun gameStarted(state: RingUiState, game: MissionType?): Boolean =
  when (game) {
    MissionType.CUPS -> state.cupsStaging || (state.cups?.phase ?: CupsPhase.READY) != CupsPhase.READY
    MissionType.PADS -> (state.pads?.phase ?: PadsPhase.READY) != PadsPhase.READY
    MissionType.SPEECH -> (state.repeat?.phase ?: RepeatPhase.READY) != RepeatPhase.READY
    else -> false
  }

/**
 * The time: big and centred while she greets the user (the mockup's "Let's play"), then, once a game is under way, a
 * compact bar with the score, so the cups and her table get the room (the user's ask: the cup scene bigger).
 */
@Composable
private fun TopBar(request: RingRequest, started: Boolean, state: RingUiState, game: MissionType?, modifier: Modifier) {
  val p = RinTheme.palette
  val time = rememberClockText()(request.time).toString()
  if (started) {
    Row(modifier.padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(time, style = MaterialTheme.typography.headlineMedium, color = p.ink, modifier = Modifier.weight(1f))
      Score(state, game)
    }
  } else {
    Column(modifier.padding(top = 18.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(time, style = MaterialTheme.typography.displayLarge.copy(fontSize = 60.sp, lineHeight = 64.sp), color = p.ink)
      Text(
        request.label.ifBlank { stringResource(R.string.ring_default_label) },
        style = MaterialTheme.typography.titleMedium,
        color = p.muted,
      )
      if (request.late) {
        Text(
          stringResource(R.string.ring_late_explained),
          style = MaterialTheme.typography.bodyMedium,
          color = p.ink,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
      }
    }
  }
}

/** How far the game is: one dot per round (filled mint when done) and the words for it, as a white sticker. */
@Composable
private fun Score(state: RingUiState, game: MissionType?) {
  val p = RinTheme.palette
  val (done, of, text) =
    when (game) {
      MissionType.CUPS -> state.cups?.let { Triple(it.streak, it.target, stringResource(R.string.cups_streak, it.streak, it.target)) }
      MissionType.PADS -> state.pads?.let { Triple(it.round, it.rounds, stringResource(R.string.pads_round, (it.round + 1).coerceAtMost(it.rounds), it.rounds)) }
      MissionType.SPEECH ->
        state.repeat?.let { Triple(it.index, it.count, stringResource(R.string.repeat_sentence, (it.index + 1).coerceAtMost(it.count), it.count)) }
      else -> null
    } ?: return
  Row(
    Modifier.sticker(radius = 20.dp, depth = 3.dp).padding(horizontal = 14.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    repeat(of) { Box(Modifier.size(12.dp).background(if (it < done) p.mint else p.line, CircleShape).clearAndSetSemantics {}) }
    Text(text, style = MaterialTheme.typography.labelLarge, color = p.ink, modifier = Modifier.padding(start = 2.dp))
  }
}

/** The sun by day and the moon at night, behind her, as on the home panel. */
@Composable
private fun Glow(modifier: Modifier) {
  val p = RinTheme.palette
  Box(modifier.offset(x = 60.dp).size(220.dp).background(if (p.night) p.glow.copy(alpha = 0.85f) else p.glow, CircleShape))
}

/** A few fixed stars on the night ground: decoration, so nothing reads them out. */
@Composable
private fun Stars(modifier: Modifier) {
  Canvas(modifier.clearAndSetSemantics {}) {
    STARS.forEachIndexed { i, (x, y) ->
      drawCircle(Color.White, radius = (if (i % 2 == 0) 1.5f else 1f).dp.toPx(), center = Offset(x * size.width, y * size.height), alpha = 0.6f)
    }
  }
}

private val STARS = listOf(0.08f to 0.06f, 0.9f to 0.04f, 0.16f to 0.32f, 0.82f to 0.4f, 0.06f to 0.55f, 0.5f to 0.02f)

/** The white sheet at the bottom: the game's part and the controls, over her view. */
@Composable
private fun Sheet(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
  Column(
    modifier
      .background(p.card, shape)
      .let { if (p.night) it.border(1.dp, p.line, shape) else it }
      .navigationBarsPadding()
      .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    content = content,
  )
}

@Composable
private fun Controls(
  state: RingUiState,
  request: RingRequest,
  onSnooze: () -> Unit,
  onDismiss: () -> Unit,
  onEmergencyStop: () -> Unit,
) {
  if (state.plainDismiss) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (request.snoozesLeft > 0) SnoozeButton(request, onSnooze, Modifier.fillMaxWidth())
      PillButton(stringResource(R.string.ring_dismiss), onDismiss, Modifier.fillMaxWidth().height(72.dp))
    }
    return
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    if (request.snoozesLeft > 0) SnoozeButton(request, onSnooze, Modifier.weight(1f))
    HoldToStopButton(
      onEmergencyStop,
      (if (request.snoozesLeft > 0) Modifier.width(HOLD_WIDTH) else Modifier.fillMaxWidth()).height(52.dp),
    )
  }
}

/** Snooze: an outlined pill, quieter than the game's "Let's play". */
@Composable
private fun SnoozeButton(request: RingRequest, onSnooze: () -> Unit, modifier: Modifier) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(26.dp)
  Box(
    modifier
      .height(52.dp)
      .clip(shape)
      .background(p.card)
      .border(2.dp, p.line, shape)
      .clickable(role = Role.Button, onClick = onSnooze),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      pluralStringResource(R.plurals.ring_snooze_left, request.snoozesLeft, request.options.snoozeMinutes, request.snoozesLeft),
      style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
      color = p.ink,
      textAlign = TextAlign.Center,
      maxLines = 2,
      modifier = Modifier.padding(horizontal = 10.dp),
    )
  }
}

private val HOLD_WIDTH = 150.dp

/**
 * The QR mission (task 3.2): a big "Scan sticker" button until tapped, then the camera with a hint about what it sees.
 * It closes itself after QrScanPolicy.CAMERA_IDLE without a sighting or a step; the button opens it again.
 */
@Composable
private fun QrSection(state: RingUiState, onOpenCamera: () -> Unit, scanner: @Composable (Modifier) -> Unit) {
  val p = RinTheme.palette
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(stringResource(R.string.mission_qr_title), style = MaterialTheme.typography.titleLarge, color = p.ink, textAlign = TextAlign.Center)
    if (state.cameraOpen) {
      scanner(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)).testTag(QR_SCANNER_TAG))
      Text(
        stringResource(
          when (state.scanHint) {
            ScanVerdict.TOO_FAR -> R.string.mission_qr_closer
            ScanVerdict.OTHER -> R.string.mission_qr_other
            else -> R.string.mission_qr_aim
          }
        ),
        style = MaterialTheme.typography.titleMedium,
        color = p.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
    } else {
      Text(stringResource(R.string.mission_qr_walk), style = MaterialTheme.typography.bodyMedium, color = p.muted, textAlign = TextAlign.Center)
      PillButton(stringResource(R.string.mission_qr_open), onOpenCamera, Modifier.fillMaxWidth())
    }
  }
}

internal const val QR_SCANNER_TAG = "ring_qr_scanner"

@Composable
private fun Passed() {
  val p = RinTheme.palette
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      stringResource(R.string.mission_passed),
      style = MaterialTheme.typography.headlineSmall,
      color = p.ink,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(stringResource(R.string.ring_tap_to_close), style = MaterialTheme.typography.bodyMedium, color = p.muted)
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

@Preview
@Composable
private fun RingScreenNightPreview() {
  RinAlarmTheme(night = true) { RingScreen(RingUiState(PREVIEW_RING, MissionProgress(0, 3), pads = PadsState()), {}, {}, {}) }
}
