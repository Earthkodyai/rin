package io.github.earthkodyai.rinalarm.tournament

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.ring.CupsLayer
import io.github.earthkodyai.rinalarm.alarm.ring.PadsBoard
import io.github.earthkodyai.rinalarm.alarm.ring.RinHand
import io.github.earthkodyai.rinalarm.alarm.ring.RingState
import io.github.earthkodyai.rinalarm.character.CharacterInsets
import io.github.earthkodyai.rinalarm.character.CharacterView
import io.github.earthkodyai.rinalarm.character.CupsView
import io.github.earthkodyai.rinalarm.character.Framing
import io.github.earthkodyai.rinalarm.character.Mood
import io.github.earthkodyai.rinalarm.mission.CupsAct
import io.github.earthkodyai.rinalarm.mission.CupsPhase
import io.github.earthkodyai.rinalarm.mission.Pad
import io.github.earthkodyai.rinalarm.mission.PadsPhase
import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.mission.TournamentScore
import io.github.earthkodyai.rinalarm.theme.RinRounded
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.theme.RinThemedContent
import io.github.earthkodyai.rinalarm.theme.ThemeClock
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.QuietPillButton
import io.github.earthkodyai.rinalarm.ui.common.rinPattern
import io.github.earthkodyai.rinalarm.ui.common.sticker
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A tournament run (G.5) on its own screen, like a practice round's (UX.8) and for the same reasons: a real ring ends
 * it (its screen opens over this one), and so does leaving, uncounted.
 */
@AndroidEntryPoint
class TournamentActivity : ComponentActivity() {
  private val viewModel: TournamentViewModel by viewModels()
  @Inject lateinit var themeClock: ThemeClock
  @Inject lateinit var ringState: RingState

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.CREATED) { ringState.active.collect { if (it != null) finish() } }
    }
    setContent { RinThemedContent(themeClock) { TournamentRoute(viewModel, onFinish = ::finish) } }
  }

  override fun onStop() {
    super.onStop()
    if (!isChangingConfigurations) finish()
  }

  companion object {
    fun intent(context: Context, game: TournamentGame): Intent =
      Intent(context, TournamentActivity::class.java).putExtra(TournamentViewModel.EXTRA_GAME, game.stored)
  }
}

@Composable
private fun TournamentRoute(viewModel: TournamentViewModel, onFinish: () -> Unit) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(state.finished) { if (state.finished) onFinish() }
  var askQuit by rememberSaveable { mutableStateOf(false) }
  BackHandler { if (state.running) askQuit = true else viewModel.done() }
  if (askQuit && state.running) {
    AlertDialog(
      onDismissRequest = { askQuit = false },
      title = { Text(stringResource(R.string.tournament_quit_title)) },
      text = { Text(stringResource(R.string.tournament_quit_body)) },
      confirmButton = { TextButton(onClick = viewModel::done) { Text(stringResource(R.string.tournament_quit)) } },
      dismissButton = { TextButton(onClick = { askQuit = false }) { Text(stringResource(R.string.tournament_keep)) } },
    )
  }
  TournamentScreen(
    state,
    onTapPad = viewModel::tapPad,
    onPickCup = viewModel::pickCup,
    onPlayAgain = viewModel::playAgain,
    onDone = viewModel::done,
    character = { modifier, insets ->
      CharacterView(
        Mood.CHEERFUL,
        modifier,
        framing = Framing.RING,
        insets = insets,
        // Her table from the start (the count waits for it), then the game's acts; none once the 2D board took over.
        cups =
          if (state.game != TournamentGame.CUPS || state.cups2d) null
          else state.cups?.act ?: CupsAct.Rest(ball = 2, at = 0, cups = 5),
        onCups = viewModel::onCupsView,
        greetOnShow = false,
        touchable = false,
        armsStill = true,
      )
    },
  )
}

@Composable
internal fun TournamentScreen(
  state: TournamentUiState,
  onTapPad: (Pad) -> Unit,
  onPickCup: (Int) -> Unit,
  onPlayAgain: () -> Unit,
  onDone: () -> Unit,
  character: @Composable (Modifier, CharacterInsets) -> Unit = { _, _ -> },
) {
  val p = RinTheme.palette
  val density = LocalDensity.current
  var topPx by remember { mutableIntStateOf(0) }
  var bottomPx by remember { mutableIntStateOf(0) }
  val top = with(density) { topPx.toDp() }
  val bottom = with(density) { bottomPx.toDp() }
  val pads = state.pads.takeIf { state.game == TournamentGame.PADS }
  val padsUp =
    pads != null &&
      (state.phase == TournamentPhase.REVEAL || (state.phase == TournamentPhase.PLAYING && (pads.phase == PadsPhase.DEMO || pads.phase == PadsPhase.INPUT)))
  val cups = state.cups?.takeIf { state.game == TournamentGame.CUPS && it.phase != CupsPhase.READY }

  Box(Modifier.fillMaxSize().background(p.ground)) {
    if (topPx > 0 && bottomPx > 0) character(Modifier.fillMaxSize(), CharacterInsets(top, bottom))
    AnimatedVisibility(padsUp, enter = fadeIn(), exit = fadeOut()) {
      Box(Modifier.fillMaxSize().padding(top = top + 4.dp).rinPattern(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), drift = true))
    }
    Column(Modifier.fillMaxSize()) {
      Spacer(Modifier.height(top))
      Box(Modifier.weight(1f).fillMaxWidth()) {
        if (padsUp) PadsBoard(checkNotNull(pads), onTapPad, Modifier.fillMaxSize()) { RinHand(it) }
        if (cups != null) {
          val x = (state.cupsView as? CupsView.Shown)?.x?.takeIf { !state.cups2d && it.size == cups.cups }
          if (x != null || state.cups2d) CupsLayer(cups, x, onPickCup, Modifier.fillMaxSize())
        }
      }
      Spacer(Modifier.height(bottom))
    }

    TopBar(state, Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topPx = it.height }.statusBarsPadding())
    Hint(state, Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomPx = it.height }.navigationBarsPadding())

    Countdown(state, Modifier.align(Alignment.Center))
    LevelBanner(state, Modifier.align(Alignment.Center))
    AnimatedVisibility(
      state.phase == TournamentPhase.RESULTS,
      Modifier.align(Alignment.BottomCenter),
      enter = slideInVertically { it } + fadeIn(),
      exit = fadeOut(),
    ) {
      state.score?.let { Results(it, state.best, state.newBest, onPlayAgain, onDone) }
    }
  }
}

/** The level being played, the running time (stopped at the miss), and the best so far. */
@Composable
private fun TopBar(state: TournamentUiState, modifier: Modifier) {
  val p = RinTheme.palette
  val started = state.startedAt
  val now by
    produceState(0L, started, state.endedAt) {
      while (started != null && state.endedAt == null) {
        value = android.os.SystemClock.elapsedRealtime()
        delay(100)
      }
    }
  val elapsed = if (started == null) 0L else ((state.endedAt ?: now) - started).coerceAtLeast(0)
  Row(
    modifier.background(p.ground.copy(alpha = 0.92f)).padding(horizontal = 20.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      stringResource(R.string.tournament_level, state.level),
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
      color = p.ink,
    )
    Column(horizontalAlignment = Alignment.End) {
      Text(formatTime(elapsed), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = p.primary)
      state.best?.let {
        Text(stringResource(R.string.tournament_best_short, it.levels, formatTime(it.timeMs)), style = MaterialTheme.typography.labelMedium, color = p.muted)
      }
    }
  }
}

/** One line of whose turn it is, in a fixed-height panel so her framing never moves. */
@Composable
private fun Hint(state: TournamentUiState, modifier: Modifier) {
  val p = RinTheme.palette
  val text =
    when {
      state.phase != TournamentPhase.PLAYING -> ""
      state.game == TournamentGame.PADS -> stringResource(if (state.pads?.phase == PadsPhase.INPUT) R.string.pads_your_turn else R.string.pads_watch)
      else -> stringResource(if (state.cups?.phase == CupsPhase.PICK) R.string.cups_pick else R.string.cups_watch)
    }
  Box(modifier.background(p.ground.copy(alpha = 0.92f)).padding(vertical = 18.dp).height(36.dp), contentAlignment = Alignment.Center) {
    Text(
      text,
      style = MaterialTheme.typography.headlineSmall,
      color = p.ink,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
  }
}

/** 3, 2, 1, GO!: each number pops in big. */
@Composable
private fun Countdown(state: TournamentUiState, modifier: Modifier) {
  val p = RinTheme.palette
  AnimatedVisibility(state.phase == TournamentPhase.COUNTDOWN, modifier, enter = fadeIn(), exit = fadeOut()) {
    AnimatedContent(
      state.count,
      transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 2.2f) + fadeIn()) togetherWith fadeOut() },
      label = "countdown",
    ) { count ->
      Text(
        if (count == 0) stringResource(R.string.tournament_go) else count.toString(),
        style = BigShout.copy(color = p.primary),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
      )
    }
  }
}

/** LEVEL n between levels, for a moment. */
@Composable
private fun LevelBanner(state: TournamentUiState, modifier: Modifier) {
  val p = RinTheme.palette
  AnimatedVisibility(
    state.phase == TournamentPhase.BANNER,
    modifier,
    enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) + fadeIn(),
    exit = scaleOut(targetScale = 1.3f) + fadeOut(),
  ) {
    Text(
      stringResource(R.string.tournament_level, state.level),
      style = BigShout.copy(fontSize = 56.sp, color = p.onPrimary),
      modifier = Modifier.sticker(fill = p.primary, radius = 24.dp).padding(horizontal = 28.dp, vertical = 12.dp),
    )
  }
}

@Composable
private fun Results(score: TournamentScore, best: TournamentScore?, newBest: Boolean, onPlayAgain: () -> Unit, onDone: () -> Unit) {
  val p = RinTheme.palette
  Column(
    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).sticker(radius = 28.dp).padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      stringResource(if (newBest) R.string.tournament_new_best else R.string.tournament_game_over),
      style = BigShout.copy(fontSize = 34.sp, color = if (newBest) p.primary else p.ink),
    )
    Text(
      stringResource(R.string.tournament_cleared, score.levels),
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
      color = p.ink,
    )
    Text(stringResource(R.string.tournament_time, formatTime(score.timeMs)), style = MaterialTheme.typography.titleMedium, color = p.ink)
    best?.let {
      Text(stringResource(R.string.tournament_best_long, it.levels, formatTime(it.timeMs)), style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
    Spacer(Modifier.height(6.dp))
    PillButton(stringResource(R.string.tournament_play_again), onPlayAgain, Modifier.fillMaxWidth())
    QuietPillButton(stringResource(R.string.tournament_done), onDone, Modifier.fillMaxWidth())
  }
}

private val BigShout = TextStyle(fontFamily = RinRounded, fontSize = 96.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)

/** 83.4 s as "1:23.4". */
internal fun formatTime(ms: Long): String {
  val tenths = ms / 100
  return "%d:%02d.%d".format(tenths / 600, (tenths / 10) % 60, tenths % 10)
}
