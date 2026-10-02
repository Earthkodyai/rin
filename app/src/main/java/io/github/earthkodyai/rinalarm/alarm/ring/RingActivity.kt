package io.github.earthkodyai.rinalarm.alarm.ring

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import io.github.earthkodyai.rinalarm.mission.RepeatPhase
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.theme.pattern
import io.github.earthkodyai.rinalarm.theme.RinThemedContent
import io.github.earthkodyai.rinalarm.theme.ThemeClock
import io.github.earthkodyai.rinalarm.ui.common.BubbleDots
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.QuietPillButton
import io.github.earthkodyai.rinalarm.ui.common.RinBubble
import io.github.earthkodyai.rinalarm.ui.common.rememberClockText
import io.github.earthkodyai.rinalarm.ui.common.rinPattern
import io.github.earthkodyai.rinalarm.ui.common.rinSwitchColors
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
        RingRoute(viewModel, onFinish = ::finish) { command ->
          when (command) {
            RingCommand.Snooze -> startService(RingService.snoozeIntent(this@RingActivity))
            is RingCommand.Dismiss -> startService(RingService.dismissIntent(this@RingActivity, command.source))
          }
        }
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

/** The ring screen on its view model: a ring's (RingActivity), or a practice round's (PracticeActivity, UX.8). */
@Composable
internal fun RingRoute(viewModel: RingViewModel, onFinish: () -> Unit, onCommand: (RingCommand) -> Unit) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(state.finished) { if (state.finished) onFinish() }
  LaunchedEffect(viewModel) { viewModel.commands.collect(onCommand) }
  RingScreen(
    state = state,
    onSnooze = viewModel::snooze,
    onDismiss = viewModel::dismiss,
    onEmergencyStop = viewModel::emergencyStop,
    onStartGame = viewModel::startGame,
    onTapPad = viewModel::tapPad,
    onPickCup = viewModel::pickCup,
    onHearAgain = viewModel::hearAgain,
    onTapWord = viewModel::tapWord,
    onCantTalk = viewModel::cantTalk,
    onClose = viewModel::close,
    onSkip = viewModel::skip,
    onScold = viewModel::setScold,
    onPlayAgain = viewModel::playAgain,
    onEndPractice = viewModel::endPractice,
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
            state.cupsStaging && state.cups?.act == null -> CupsAct.Rest(ball = 1, at = 0, cups = state.cups?.cups ?: 3)
            else -> state.cups?.act
          },
        onCups = viewModel::onCupsView,
        speech = viewModel.speaking,
        onVisible = viewModel::onCharacterVisible,
        // No head taps here: a tap on her is a tap on the screen (skip her, or close once the ring is over).
        touchable = false,
        armsStill = state.inGame,
      )
    },
    hand = { modifier -> RinHand(modifier) },
  )
}

@Composable
internal fun RingScreen(
  state: RingUiState,
  onSnooze: () -> Unit,
  onDismiss: () -> Unit,
  onEmergencyStop: () -> Unit,
  modifier: Modifier = Modifier,
  onStartGame: () -> Unit = {},
  onTapPad: (Pad) -> Unit = {},
  onPickCup: (Int) -> Unit = {},
  onHearAgain: () -> Unit = {},
  onTapWord: (Int) -> Unit = {},
  onCantTalk: () -> Unit = {},
  onClose: () -> Unit = {},
  onSkip: () -> Unit = {},
  onScold: (Boolean) -> Unit = {},
  onPlayAgain: () -> Unit = {},
  onEndPractice: () -> Unit = {},
  // Slots, so previews and UI tests run without a WebView.
  character: @Composable (Modifier, CharacterInsets) -> Unit = { _, _ -> },
  hand: @Composable (Modifier) -> Unit = { DrawnHand(it) },
) {
  val ring = state.ring ?: return
  val request = ring.request
  val p = RinTheme.palette
  // Once the ring is over (a pass, a snooze, the emergency stop) a tap anywhere closes the screen, cutting her short.
  // Only a tap that starts after that: the finger still on the emergency button when its 3 s hold ends, lifted a
  // moment later, closed the screen and cut her line off (the user, 2026-10-02).
  // A practice round's win keeps the screen for "Play again" or "Done".
  val closable = (state.passed && !state.practice) || state.leaving
  // Before that, a tap outside the buttons skips her: her line, her intro (the game starts) or her scold (G.1).
  val skippable = !closable && !state.passed && (state.line != null || state.scolding)
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
      .pointerInput(closable) { detectTapGestures { if (closable) onClose() else onSkip() } }
      .semantics {
        if (closable) {
          onClick {
            onClose()
            true
          }
        } else if (skippable) {
          onClick {
            onSkip()
            true
          }
        }
      }
      .testTag(RING_SCREEN_TAG)
  ) {
    Glow(Modifier.align(Alignment.TopEnd).padding(top = top * 0.6f))
    if (p.night) Stars(Modifier.fillMaxSize())
    // Created once both are measured, so her page starts framed for them.
    if (topPx > 0 && sheetPx > 0) character(Modifier.fillMaxSize(), CharacterInsets(top, bottom))
    // The pads game: one patterned surface from under the time down through the sheet, the pads on it (the user:
    // the pads' panel and the sheet joined). It covers her while the pads are up and fades when they step aside.
    // A quiet miss (scolding off) keeps the pads up: Rin does not come out for it (the user).
    val padsUp = pads?.phase == PadsPhase.DEMO || pads?.phase == PadsPhase.INPUT || (pads?.phase == PadsPhase.SCOLD && state.quietMiss)
    AnimatedVisibility(padsUp, enter = fadeIn(), exit = fadeOut()) {
      Box(
        Modifier.fillMaxSize()
          .padding(top = top + 4.dp)
          .rinPattern(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), drift = true)
      )
    }

    Column(Modifier.fillMaxSize()) {
      Spacer(Modifier.height(top))
      Box(Modifier.weight(1f).fillMaxWidth()) {
        // The colour pads cover her while the game is on (only her hand shows), and step aside when she scolds (D17).
        if (padsUp) PadsBoard(checkNotNull(pads), onTapPad, Modifier.fillMaxSize(), hand)
        if (cups != null && cups.phase != CupsPhase.READY) {
          // Her page's cups once it shows them; until then (or if it never does) the native board.
          val x = (state.cupsView as? CupsView.Shown)?.x?.takeIf { !state.cups2d && it.size == cups.cups }
          if (x != null || state.cups2d) CupsLayer(cups, x, onPickCup, Modifier.fillMaxSize())
        }
        // Repeat after Rin: the sentence floats over her chest, so the sheet stays small and she stays big (the user).
        if (game == MissionType.SPEECH && playing && started) {
          RepeatPanel(
            state.repeat,
            state.rinSpeaking,
            state.micLevel,
            onTapWord,
            Modifier.align(BiasAlignment(0f, 0.45f)).padding(horizontal = 20.dp),
          )
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

    // Over everything but the bars: the red glow at the edges and the cross, for a moment.
    MissFlash(state.quietMiss, Modifier.fillMaxSize())
    TopBar(
      request,
      started,
      state,
      game,
      Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topPx = it.height }.statusBarsPadding(),
    )
    Sheet(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { sheetPx = it.height },
      joined = padsUp,
    ) {
      // After a pass, a snooze or the emergency stop, the game section and the controls stay as invisible, inert
      // space, so the sheet keeps its height while she claps or says her last line.
      val over = state.passed || state.leaving
      val inert = Modifier.alpha(0f).clearAndSetSemantics {}
      // While she scolds, the scold switch takes the game's place in the sheet (the user: in the open it floated). The
      // row keeps one height through the game, so the switch coming and going never moves the sheet.
      val scoldRow = state.showScoldSwitch
      val playRow = if (started && !state.plainDismiss) Modifier.heightIn(min = GAME_ROW_MIN) else Modifier
      Box(playRow, contentAlignment = Alignment.Center) {
        Box(if ((over && game != MissionType.CUPS) || scoldRow) inert else Modifier) {
          when {
            state.plainDismiss -> Unit
            game == MissionType.PADS -> PadsCard(state.pads, onStartGame, started)
            // The cups keep their line visible through the pass: she claps behind the table.
            game == MissionType.CUPS -> CupsCard(state.cups, state.cupsStaging, state.passed, onStartGame, state.practice)
            // A practice round has no other game to switch to.
            game == MissionType.SPEECH ->
              RepeatCard(state.repeat, onStartGame, onHearAgain, onCantTalk.takeUnless { state.practice }, started)
          }
        }
        if (state.passed && game != MissionType.CUPS) Passed(state.practice)
        if (scoldRow) ScoldSwitch(state.scold, onScold, Modifier.fillMaxWidth())
      }
      if (state.practice) {
        PracticeControls(state.passed, onPlayAgain, onEndPractice)
      } else {
        // Big targets: the user is half asleep. Same size, invisible and inert once the ring is over.
        Box(if (over) inert else Modifier) {
          Controls(state, request, if (over) ({}) else onSnooze, if (over) ({}) else onDismiss, if (over) ({}) else onEmergencyStop)
        }
      }
    }
  }
}

/** Whether "Let's play" has been pressed: the game is under way, or over. */
private fun gameStarted(state: RingUiState, game: MissionType?): Boolean =
  state.inGame ||
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
        if (state.practice) stringResource(R.string.practice_label) else request.label.ifBlank { stringResource(R.string.ring_default_label) },
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
  // Every dot is filled once the game is won (pads count finished rounds, so the last one never was).
  val filled = if (state.passed) of else done
  Row(
    Modifier.sticker(radius = 20.dp, depth = 3.dp).padding(horizontal = 14.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    repeat(of) { Box(Modifier.size(12.dp).background(if (it < filled) p.mint else p.line, CircleShape).clearAndSetSemantics {}) }
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

/**
 * The sheet at the bottom, compact and on the ring screen's patterned wallpaper (the user: the white one was too big
 * and flat; their reference, a pink striped wallpaper of small cute things): the game's part and the controls, over
 * her view. It sets the text colour itself:
 * the games' texts once took it from the Material cards they sat in, and without them fell back to black, which
 * vanished on the night sheet (Repeat after Rin's sentence, UX.4 test ring).
 */
@Composable
private fun Sheet(modifier: Modifier, joined: Boolean, content: @Composable ColumnScope.() -> Unit) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
  CompositionLocalProvider(LocalContentColor provides p.ink) {
    Column(
      modifier
        // Joined: the pads' surface behind already runs through here, so the sheet draws nothing of its own.
        .then(if (joined) Modifier else Modifier.rinPattern(shape, drift = true))
        .let { if (p.night && !joined) it.border(1.dp, p.line, shape) else it }
        .navigationBarsPadding()
        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
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
  if (state.plainDismiss) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (request.snoozesLeft > 0) SnoozeButton(request, onSnooze, Modifier.fillMaxWidth())
      PillButton(stringResource(R.string.ring_dismiss), onDismiss, Modifier.fillMaxWidth().height(64.dp))
    }
    return
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    if (request.snoozesLeft > 0) SnoozeButton(request, onSnooze, Modifier.weight(1f))
    HoldToStopButton(
      onEmergencyStop,
      (if (request.snoozesLeft > 0) Modifier.width(HOLD_WIDTH) else Modifier.fillMaxWidth()).height(CONTROL_HEIGHT),
    )
  }
}

/**
 * A quiet miss (scolding off, the user's ask): a red glow along the screen's edges and a red cross, gone in a moment
 * as the game goes on. Bright stop red with a white outline, so it reads on the pads, the cups, by day and at night.
 */
@Composable
private fun MissFlash(shown: Boolean, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  val missed = stringResource(R.string.ring_quiet_miss)
  AnimatedVisibility(shown, modifier, enter = fadeIn(tween(90)), exit = fadeOut(tween(220))) {
    Box(Modifier.fillMaxSize().semantics { contentDescription = missed; liveRegion = LiveRegionMode.Polite }) {
      Canvas(Modifier.fillMaxSize()) {
        val band = 44.dp.toPx()
        val red = p.stop.copy(alpha = 0.6f)
        val clear = p.stop.copy(alpha = 0f)
        drawRect(Brush.verticalGradient(listOf(red, clear), endY = band), size = Size(size.width, band))
        drawRect(
          Brush.verticalGradient(listOf(clear, red), startY = size.height - band, endY = size.height),
          topLeft = Offset(0f, size.height - band),
          size = Size(size.width, band),
        )
        drawRect(Brush.horizontalGradient(listOf(red, clear), endX = band), size = Size(band, size.height))
        drawRect(
          Brush.horizontalGradient(listOf(clear, red), startX = size.width - band, endX = size.width),
          topLeft = Offset(size.width - band, 0f),
          size = Size(band, size.height),
        )
      }
      Canvas(Modifier.align(BiasAlignment(0f, -0.25f)).size(120.dp).testTag(MISS_FLASH_TAG)) {
        val inset = 18.dp.toPx()
        val a = Offset(inset, inset)
        val b = Offset(size.width - inset, size.height - inset)
        val c = Offset(size.width - inset, inset)
        val d = Offset(inset, size.height - inset)
        for ((width, colour) in listOf(30.dp.toPx() to Color.White, 20.dp.toPx() to p.stop)) {
          drawLine(colour, a, b, strokeWidth = width, cap = StrokeCap.Round)
          drawLine(colour, c, d, strokeWidth = width, cap = StrokeCap.Round)
        }
      }
    }
  }
}

/**
 * "Rin scolds" (G.1) in the sheet while she scolds a miss, with what it does: off, she is quiet and calm at once, and
 * later misses are quiet, for this alarm and new ones (the editor turns it back on). The whole card toggles, a bigger
 * target than the switch alone.
 */
@Composable
private fun ScoldSwitch(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(18.dp)
  Row(
    modifier
      .sticker(radius = 18.dp, depth = 3.dp)
      .clip(shape)
      .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
      .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
      .testTag(SCOLD_SWITCH_TAG),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(stringResource(R.string.ring_scold_switch), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = p.ink)
      Text(stringResource(R.string.ring_scold_switch_summary), style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 2)
    }
    Switch(checked = on, onCheckedChange = null, colors = rinSwitchColors(), modifier = Modifier.padding(start = 10.dp))
  }
}

/** The game's row in the sheet once it is under way: room for the scold switch with its line, so nothing jumps. */
private val GAME_ROW_MIN = 60.dp

/** Snooze: an outlined pill, quieter than the game's "Let's play". */
@Composable
private fun SnoozeButton(request: RingRequest, onSnooze: () -> Unit, modifier: Modifier) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(24.dp)
  Box(
    modifier
      .height(CONTROL_HEIGHT)
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

/**
 * A practice round's controls in place of Snooze and the emergency hold (UX.8): "Done" leaves at any point; after the
 * win "Play again" comes first. The row keeps the controls' height, so the sheet does not jump at the win.
 */
@Composable
private fun PracticeControls(passed: Boolean, onPlayAgain: () -> Unit, onDone: () -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    if (passed) {
      PillButton(stringResource(R.string.practice_again), onPlayAgain, Modifier.weight(1f).height(CONTROL_HEIGHT).testTag(PRACTICE_AGAIN_TAG))
    }
    QuietPillButton(
      stringResource(R.string.practice_done),
      onDone,
      Modifier.weight(1f).testTag(PRACTICE_DONE_TAG),
      height = CONTROL_HEIGHT,
    )
  }
}

/** Room for the warning sign and "Hold 3 s to stop" on one line (150 dp cut "stop" off). */
private val HOLD_WIDTH = 176.dp

/** Snooze and the emergency hold: big enough for a sleepy thumb, small enough to leave her the screen. */
private val CONTROL_HEIGHT = 48.dp

/** Compact, so it fits in the game row it covers and the sheet keeps its height at the pass. */
@Composable
private fun Passed(practice: Boolean) {
  val p = RinTheme.palette
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      stringResource(if (practice) R.string.practice_passed else R.string.mission_passed),
      style = MaterialTheme.typography.titleLarge,
      color = p.ink,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (!practice) Text(stringResource(R.string.ring_tap_to_close), style = MaterialTheme.typography.bodySmall, color = p.pattern.muted)
  }
}

internal const val RING_SCREEN_TAG = "ring_screen"
internal const val RIN_LINE_TAG = "rin_line"
internal const val PRACTICE_AGAIN_TAG = "practice_again"
internal const val PRACTICE_DONE_TAG = "practice_done"
internal const val SCOLD_SWITCH_TAG = "scold_switch"
internal const val MISS_FLASH_TAG = "miss_flash"

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
private fun RingScreenPlainPreview() {
  RinAlarmTheme { RingScreen(RingUiState(PREVIEW_RING.copy(mission = null)), {}, {}, {}) }
}

@Preview
@Composable
private fun RingScreenPracticeWonPreview() {
  val practice = PREVIEW_RING.copy(request = PREVIEW_RING.request.copy(label = ""))
  RinAlarmTheme {
    RingScreen(RingUiState(practice, MissionProgress(1, 1), RingPhase.PASSED, passed = true, pads = PadsState(), practice = true), {}, {}, {})
  }
}

@Preview
@Composable
private fun RingScreenNightPreview() {
  RinAlarmTheme(night = true) { RingScreen(RingUiState(PREVIEW_RING, MissionProgress(0, 3), pads = PadsState()), {}, {}, {}) }
}
