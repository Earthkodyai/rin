package io.github.earthkodyai.rinalarm.ui.editor

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.AppLocale
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * The alarm time as scroll wheels (UX.3, the user's pick over the clock face): hours and minutes that loop, plus an
 * AM/PM wheel on a 12-hour phone. A flick snaps to a number and a tap on a nearby number scrolls to it; no vibration
 * (the user, 2026-10-01: not needed, and the 14T's ticks were too faint to feel). [onChange] hears each number as it
 * reaches the band, so "Rings in" keeps up with a flick. Screen readers get one control per wheel with Next and Previous actions instead of a list of sixty numbers.
 */
@Composable
fun TimeWheels(time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
  val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
  val current by rememberUpdatedState(time)
  val p = RinTheme.palette
  Box(modifier.fillMaxWidth().height(ITEM * VISIBLE), contentAlignment = Alignment.Center) {
    // The band the chosen numbers sit in.
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(ITEM + 12.dp).background(p.rinCard, RoundedCornerShape(18.dp)))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
      if (is24Hour) {
        Wheel(
          count = 24,
          selected = time.hour,
          onSelect = { onChange(current.withHour(it)) },
          label = { "%02d".format(it) },
          description = stringResource(R.string.editor_wheel_hour),
          modifier = Modifier.testTag(HOUR_WHEEL_TAG),
        )
      } else {
        // 12, 1, 2 ... 11: index 0 is 12 o'clock.
        Wheel(
          count = 12,
          selected = time.hour % 12,
          onSelect = { onChange(current.withHour(it + if (current.hour >= 12) 12 else 0)) },
          label = { if (it == 0) "12" else it.toString() },
          description = stringResource(R.string.editor_wheel_hour),
          modifier = Modifier.testTag(HOUR_WHEEL_TAG),
        )
      }
      Text(
        ":",
        style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold),
        color = p.primary,
        modifier = Modifier.padding(horizontal = 4.dp).clearAndSetSemantics {},
      )
      Wheel(
        count = 60,
        selected = time.minute,
        onSelect = { onChange(current.withMinute(it)) },
        label = { "%02d".format(it) },
        description = stringResource(R.string.editor_wheel_minute),
        modifier = Modifier.testTag(MINUTE_WHEEL_TAG),
      )
      if (!is24Hour) {
        val amPm = remember { DateTimeFormatter.ofPattern("a", AppLocale) }
        Wheel(
          count = 2,
          selected = if (time.hour >= 12) 1 else 0,
          onSelect = { onChange(current.withHour(current.hour % 12 + 12 * it)) },
          label = { LocalTime.of(12 * it, 0).format(amPm) },
          description = stringResource(R.string.editor_wheel_am_pm),
          loop = false,
          width = 84.dp,
          fontScale = 0.5f,
        )
      }
    }
  }
}

/**
 * One wheel of [count] values, [selected] in the band. A looping wheel repeats its values [LOOPS] times and starts in
 * the middle, so it never runs out in a morning's worth of flicks.
 */
@Composable
private fun Wheel(
  count: Int,
  selected: Int,
  onSelect: (Int) -> Unit,
  label: (Int) -> String,
  description: String,
  modifier: Modifier = Modifier,
  loop: Boolean = true,
  width: Dp = 104.dp,
  fontScale: Float = 1f,
) {
  val p = RinTheme.palette
  val total = if (loop) count * LOOPS else count
  val state = rememberLazyListState(initialFirstVisibleItemIndex = if (loop) count * (LOOPS / 2) + selected else selected)
  val itemPx = with(LocalDensity.current) { ITEM.toPx() }
  val selectedNow by rememberUpdatedState(selected)
  val onSelectNow by rememberUpdatedState(onSelect)
  val centre by remember { derivedStateOf { state.centreIndex(itemPx) } }
  // How many turns to a choice made elsewhere are running (a new tap can start one before the last has ended): the
  // numbers a turn passes are not choices.
  var turns by remember { mutableIntStateOf(0) }

  // Each number that reaches the band under a finger or a fling is the choice at once; the last one when it settles
  // stays chosen.
  LaunchedEffect(state) {
    snapshotFlow { state.centreIndex(itemPx) }
      .distinctUntilChanged()
      .collect { index ->
        if (!state.isScrollInProgress || turns > 0) return@collect
        if (index % count != selectedNow) onSelectNow(index % count)
      }
  }
  // Settled (also after a drag that never crossed half a number): the number in the band is the choice.
  LaunchedEffect(state) {
    snapshotFlow { state.isScrollInProgress }
      .filter { !it }
      .collect {
        val value = state.centreIndex(itemPx) % count
        if (turns == 0 && value != selectedNow) onSelectNow(value)
      }
  }
  // A choice from elsewhere (loading the alarm, a tap, a screen reader's action): turn the shortest way to it.
  LaunchedEffect(selected) {
    if (state.isScrollInProgress && turns == 0) return@LaunchedEffect // the user's own scroll made this choice
    val now = state.centreIndex(itemPx)
    if (now % count == selected) return@LaunchedEffect
    val target =
      if (loop) {
        var delta = (selected - now % count + count) % count
        if (delta > count / 2) delta -= count
        now + delta
      } else {
        selected
      }
    turns++
    try {
      state.animateScrollToItem(target)
    } finally {
      turns--
    }
  }

  val step = { by: Int -> onSelectNow(if (loop) (selectedNow + by + count) % count else (selectedNow + by).coerceIn(0, count - 1)) }
  val next = stringResource(R.string.editor_wheel_next)
  val previous = stringResource(R.string.editor_wheel_previous)
  LazyColumn(
    state = state,
    flingBehavior = rememberSnapFlingBehavior(state),
    contentPadding = PaddingValues(vertical = ITEM * (VISIBLE / 2)),
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      modifier.width(width).height(ITEM * VISIBLE).semantics {
        contentDescription = description
        stateDescription = label(selected)
        customActions =
          listOf(
            CustomAccessibilityAction(next) { step(1).let { true } },
            CustomAccessibilityAction(previous) { step(-1).let { true } },
          )
      },
  ) {
    items(total) { index ->
      Box(
        Modifier.height(ITEM)
          .fillMaxWidth()
          .clickable(enabled = index != centre) { onSelectNow(index % count) }
          .clearAndSetSemantics {}
          .graphicsLayer {
            // Shrinks and fades with distance from the band, continuously as it scrolls.
            val d = abs(index - (state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx)).coerceAtMost(2f)
            val scale = if (d <= 1f) 1f - 0.4f * d else 0.6f - 0.14f * (d - 1f)
            scaleX = scale
            scaleY = scale
            alpha = if (d <= 1f) 1f - 0.55f * d else 0.45f - 0.25f * (d - 1f)
          },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label(index % count),
          style = MaterialTheme.typography.displayMedium.copy(fontSize = 46.sp * fontScale, lineHeight = 50.sp * fontScale),
          color = p.ink,
          maxLines = 1,
        )
      }
    }
  }
}

/** The item in the band: the first visible one, or the next once it is more than half scrolled away. */
private fun LazyListState.centreIndex(itemPx: Float): Int =
  firstVisibleItemIndex + if (firstVisibleItemScrollOffset > itemPx / 2) 1 else 0

private val ITEM = 52.dp
private const val VISIBLE = 5
private const val LOOPS = 1000

internal const val HOUR_WHEEL_TAG = "editor_hour_wheel"
internal const val MINUTE_WHEEL_TAG = "editor_minute_wheel"
