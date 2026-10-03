package io.github.earthkodyai.rinalarm.ui.main

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.QuietPillButton
import io.github.earthkodyai.rinalarm.ui.common.SpotTargets
import io.github.earthkodyai.rinalarm.ui.common.Spotlight

/** What the home tour points at. */
enum class TourTarget {
  /** Rin's panel: always left bright, as she tells the tour in her bubble. */
  PANEL,
  TOP_BUTTONS,
  TOURNAMENT,
  FIRST_ALARM,
  ADD,
}

typealias TourTargets = SpotTargets<TourTarget>

/**
 * The home tour (UX.8, D30) after onboarding: one tip per step in Rin's bubble, the thing it is about lit up and the
 * rest dimmed. The tips are text with her mouth still (no new clips, D30). The last step is hands-on (the user,
 * 2026-10-02): she says her one clip that fits ("Let's play. Win, and the alarm stops."), and the user taps the real
 * Add alarm, which opens the editor's walkthrough of their first alarm ([handsOn]).
 */
enum class TourStep(val target: TourTarget?, @StringRes val tip: Int, val handsOn: Boolean = false) {
  HELLO(null, R.string.tour_hello),
  TOURNAMENT(TourTarget.TOURNAMENT, R.string.tour_tournament),
  /** Only with an alarm in the list (a new install has none). */
  ALARM(TourTarget.FIRST_ALARM, R.string.tour_alarm),
  TOP(TourTarget.TOP_BUTTONS, R.string.tour_top),
  ADD(TourTarget.ADD, R.string.tour_add, handsOn = true),
  ;

  companion object {
    fun steps(hasAlarms: Boolean): List<TourStep> = entries.filter { it != ALARM || hasAlarms }
  }
}

/**
 * The tour over the home screen: a dim layer with holes for Rin's panel and the step's target, "Skip tour" at the
 * top and "Next" at the bottom left, clear of the Add button. A tap anywhere also moves on, except in a step's first
 * moment ([STEP_GUARD_MS]): the user's double taps (Phase 2) would skip a tip unread. The hands-on step has no Next:
 * its target is the way on.
 */
@Composable
internal fun HomeTour(
  step: TourStep,
  number: Int,
  count: Int,
  targets: TourTargets,
  onNext: () -> Unit,
  onSkip: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val readyAt = remember(step) { SystemClock.uptimeMillis() + STEP_GUARD_MS }
  val next = { if (SystemClock.uptimeMillis() >= readyAt) onNext() }
  val lit = step.target?.let { targets.bounds[it] }
  Box(modifier.fillMaxSize().testTag(HOME_TOUR_TAG)) {
    Spotlight(
      holes = listOfNotNull(targets.bounds[TourTarget.PANEL], lit),
      lit = lit,
      open = lit.takeIf { step.handsOn },
      onTap = { if (!step.handsOn) next() },
    )
    QuietPillButton(
      stringResource(R.string.tour_skip),
      onSkip,
      Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 10.dp).testTag(TOUR_SKIP_TAG),
      height = 44.dp,
    )
    if (!step.handsOn) {
      PillButton(
        stringResource(R.string.tour_next, number, count),
        next,
        Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 18.dp, bottom = 20.dp).testTag(TOUR_NEXT_TAG),
      )
    }
  }
}

/** How long a new step ignores taps on the dim layer. */
private const val STEP_GUARD_MS = 500L

internal const val HOME_TOUR_TAG = "home_tour"
internal const val TOUR_NEXT_TAG = "tour_next"
internal const val TOUR_SKIP_TAG = "tour_skip"
