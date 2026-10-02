package io.github.earthkodyai.rinalarm.ui.main

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.QuietPillButton
import io.github.earthkodyai.rinalarm.ui.common.sticker

/** What the home tour points at. */
enum class TourTarget {
  /** Rin's panel: always left bright, as she tells the tour in her bubble. */
  PANEL,
  TOP_BUTTONS,
  DAY_MODE,
  FIRST_ALARM,
  ADD,
}

/**
 * The home tour (UX.8, D30) after onboarding: one tip per step in Rin's bubble, the thing it is about lit up and the
 * rest dimmed. The tips are text with her mouth still (no new clips, D30; the user's pick over tip cards); the last
 * step has her one clip that fits ("Let's play. Win, and the alarm stops.") and leads to the practice rounds.
 */
enum class TourStep(val target: TourTarget?, @StringRes val tip: Int) {
  HELLO(null, R.string.tour_hello),
  ADD(TourTarget.ADD, R.string.tour_add),
  DAY_MODE(TourTarget.DAY_MODE, R.string.tour_day_mode),
  /** Only with an alarm in the list (a new install has none). */
  ALARM(TourTarget.FIRST_ALARM, R.string.tour_alarm),
  TOP(TourTarget.TOP_BUTTONS, R.string.tour_top),
  GAMES(null, R.string.tour_games),
  ;

  companion object {
    fun steps(hasAlarms: Boolean): List<TourStep> = entries.filter { it != ALARM || hasAlarms }
  }
}

/** Where each target sits on screen, as the home screen lays it out. */
class TourTargets {
  internal val bounds = mutableStateMapOf<TourTarget, Rect>()
}

/** Records where this element is, for the tour's light (a no-op without [targets]). */
fun Modifier.tourTarget(targets: TourTargets?, target: TourTarget): Modifier =
  if (targets == null) this else onGloballyPositioned { targets.bounds[target] = it.boundsInRoot() }

/**
 * The tour over the home screen: a dim layer with holes for Rin's panel and the step's target (ringed in her pink),
 * "Skip tour" at the top and "Next" at the bottom left, clear of the Add button. A tap anywhere also moves on, except
 * in a step's first moment ([STEP_GUARD_MS]): the user's double taps (Phase 2) would skip a tip unread. The last step
 * asks instead: try the games, or not now.
 */
@Composable
internal fun HomeTour(
  step: TourStep,
  number: Int,
  count: Int,
  targets: TourTargets,
  onNext: () -> Unit,
  onSkip: () -> Unit,
  onTryGames: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val p = RinTheme.palette
  var origin by remember { mutableStateOf(Offset.Zero) }
  val readyAt = remember(step) { SystemClock.uptimeMillis() + STEP_GUARD_MS }
  val next = { if (SystemClock.uptimeMillis() >= readyAt) onNext() }
  val last = step == TourStep.GAMES
  Box(
    modifier
      .fillMaxSize()
      .onGloballyPositioned { origin = it.positionInRoot() }
      .pointerInput(step) { detectTapGestures { if (!last) next() } }
      .testTag(HOME_TOUR_TAG)
  ) {
    val scrim = Color.Black.copy(alpha = if (p.night) 0.62f else 0.5f)
    val ring = p.primary
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.clearAndSetSemantics {}) {
      drawRect(scrim)
      val pad = 6.dp.toPx()
      val radius = CornerRadius(24.dp.toPx())
      val lit = step.target?.let { targets.bounds[it] }
      listOfNotNull(targets.bounds[TourTarget.PANEL], lit).forEach { r ->
        val hole = r.translate(-origin).inflate(pad)
        drawRoundRect(Color.Black, hole.topLeft, hole.size, radius, blendMode = BlendMode.Clear)
      }
      lit?.let { r ->
        val hole = r.translate(-origin).inflate(pad)
        drawRoundRect(ring, hole.topLeft, hole.size, radius, style = Stroke(3.dp.toPx()))
      }
    }
    if (!last) {
      QuietPillButton(
        stringResource(R.string.tour_skip),
        onSkip,
        Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 10.dp).testTag(TOUR_SKIP_TAG),
        height = 44.dp,
      )
      PillButton(
        stringResource(R.string.tour_next, number, count),
        next,
        Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 18.dp, bottom = 20.dp).testTag(TOUR_NEXT_TAG),
      )
    } else {
      Column(
        Modifier.align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(16.dp)
          .fillMaxWidth()
          .sticker(radius = 24.dp)
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          stringResource(R.string.practice_title),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = p.ink,
        )
        Text(stringResource(R.string.tour_games_card), style = MaterialTheme.typography.bodyMedium, color = p.muted)
        PillButton(stringResource(R.string.tour_try_games), onTryGames, Modifier.fillMaxWidth().testTag(TOUR_TRY_TAG))
        QuietPillButton(stringResource(R.string.tour_not_now), onSkip, Modifier.fillMaxWidth())
      }
    }
  }
}

/** How long a new step ignores taps on the dim layer. */
private const val STEP_GUARD_MS = 500L

internal const val HOME_TOUR_TAG = "home_tour"
internal const val TOUR_NEXT_TAG = "tour_next"
internal const val TOUR_SKIP_TAG = "tour_skip"
internal const val TOUR_TRY_TAG = "tour_try"
