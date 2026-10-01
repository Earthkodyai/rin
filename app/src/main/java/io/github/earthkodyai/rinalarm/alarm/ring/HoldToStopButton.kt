package io.github.earthkodyai.rinalarm.alarm.ring

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.sticker
import kotlinx.coroutines.launch

/**
 * The emergency stop (plan 05c: always available, hold 3 s, logged as EMERGENCY_STOP for a Phase 5 Bond cost). The
 * fill grows while held and drains when let go early, so a brush against it does nothing. Accessibility services
 * get it as a long-press action.
 */
@Composable
internal fun HoldToStopButton(onStop: () -> Unit, modifier: Modifier = Modifier) {
  val stop by rememberUpdatedState(onStop)
  val fill = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  val shape = RoundedCornerShape(24.dp)
  val label = stringResource(R.string.ring_hold_to_stop)
  val p = RinTheme.palette
  // Bright red with warning stripes and a warning sign (the user, 2026-10-02): it should look dangerous to press.
  Box(
    modifier
      .sticker(fill = p.stop, radius = 24.dp, depth = 3.dp, shadow = p.stopShadow, outline = null)
      .clip(shape)
      .drawBehind { warningStripes(p.stopHeld) }
      .semantics {
        role = Role.Button
        onLongClick(label) {
          stop()
          true
        }
      }
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown()
          val hold =
            scope.launch {
              fill.animateTo(1f, tween((HOLD_MS * (1 - fill.value)).toInt(), easing = LinearEasing))
              stop()
            }
          waitForUpOrCancellation()
          if (fill.value < 1f) {
            hold.cancel()
            scope.launch { fill.animateTo(0f, tween(RELEASE_MS)) }
          }
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier.align(Alignment.CenterStart)
        .fillMaxHeight()
        .layout { measurable, constraints ->
          val width = (constraints.maxWidth * fill.value).toInt()
          val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
          layout(width, placeable.height) { placeable.place(0, 0) }
        }
        .background(p.stopHeld)
    )
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = p.onStop, modifier = Modifier.size(18.dp))
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = p.onStop,
        maxLines = 1,
        modifier = Modifier.padding(start = 6.dp),
      )
    }
  }
}

/** Faint diagonal bands, as on warning tape, in the held red: they show the button is not an ordinary one. */
private fun DrawScope.warningStripes(color: Color) {
  val band = 10.dp.toPx()
  var x = -size.height
  while (x < size.width) {
    val stripe =
      Path().apply {
        moveTo(x, size.height)
        lineTo(x + band, size.height)
        lineTo(x + band + size.height, 0f)
        lineTo(x + size.height, 0f)
        close()
      }
    drawPath(stripe, color, alpha = 0.22f)
    x += band * 2.4f
  }
}

/** Plan 05c: hold for 3 seconds. */
internal const val HOLD_MS = 3_000
private const val RELEASE_MS = 250
