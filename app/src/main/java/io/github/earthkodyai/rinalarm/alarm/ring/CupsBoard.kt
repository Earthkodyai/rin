package io.github.earthkodyai.rinalarm.alarm.ring

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameMillis
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.mission.CupsAct
import io.github.earthkodyai.rinalarm.mission.CupsPhase
import io.github.earthkodyai.rinalarm.mission.CupsState
import io.github.earthkodyai.rinalarm.mission.CupsTimeline

/**
 * The cup shuffle (D17, task 3.4) over Rin's half of the ring screen. Her page acts the game out at a table (the cups
 * under her hands), and this layer only takes the picks: each cup's zone runs from halfway to its neighbour, so the
 * whole width is a target and a pick never lands between cups. [cupX] is where her page drew each cup (0..1 across);
 * null draws the native 2D board instead, with the same acts, so the game plays the same when her page is missing.
 * Taps count on touch-down, like the colour pads.
 */
@Composable
internal fun CupsLayer(state: CupsState, cupX: List<Float>?, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
  val x = cupX ?: BOARD_X
  Box(modifier.testTag(CUPS_LAYER_TAG)) {
    if (cupX == null) state.act?.let { CupsBoard2d(it, Modifier.fillMaxSize()) }
    val open = state.phase == CupsPhase.SHOW || state.phase == CupsPhase.SHUFFLE || state.phase == CupsPhase.PICK
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val edges = listOf(0f, (x[0] + x[1]) / 2, (x[1] + x[2]) / 2, 1f)
      for (slot in 0..2) {
        val name = stringResource(R.string.cups_cup, slot + 1)
        Box(
          Modifier.offset(x = maxWidth * edges[slot])
            .width(maxWidth * (edges[slot + 1] - edges[slot]))
            .fillMaxHeight()
            // Taps while the cups move are counted (earlyTaps), not judged: CupsGame ignores them.
            .pointerInput(open) { if (open) detectTapGestures(onPress = { onPick(slot) }) }
            .semantics {
              role = Role.Button
              contentDescription = name
              if (state.phase == CupsPhase.PICK) onClick { onPick(slot); true }
            }
            .testTag(cupTag(slot))
        )
        Text(
          "${slot + 1}",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier =
            Modifier.align(Alignment.BottomStart)
              .offset(x = maxWidth * x[slot] - 14.dp, y = (-4).dp)
              .width(28.dp)
              .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape),
        )
      }
    }
  }
}

/** Where the 2D board draws each slot's cup, across its width. */
private val BOARD_X = listOf(1 / 6f, 1 / 2f, 5 / 6f)

/**
 * The native board: a table seen a little from above, the cup passing in front lower and the one behind higher, a
 * lifted cup showing the ball. Redrawn every frame from the act, so it is where the rules say at every moment.
 */
@Composable
private fun CupsBoard2d(act: CupsAct, modifier: Modifier) {
  val now by
    produceState(SystemClock.elapsedRealtime(), act) {
      while (true) withFrameMillis { value = SystemClock.elapsedRealtime() }
    }
  val background = MaterialTheme.colorScheme.primaryContainer
  Canvas(modifier.background(background)) {
    val frame = CupsTimeline.frameAt(act, now)
    val w = size.width
    val h = size.height
    val tableAt = Offset(w * 0.02f, h * 0.5f)
    val tableSize = Size(w * 0.96f, h * 0.46f)
    drawRoundRect(WOOD, tableAt, tableSize, CornerRadius(w * 0.03f))
    drawRoundRect(WOOD_EDGE, tableAt, tableSize, CornerRadius(w * 0.03f), style = Stroke(w * 0.012f))
    val cupW = minOf(w * 0.22f, h * 0.3f)
    val cupH = cupW * 1.15f
    val base = { x: Float, z: Float -> Offset(w * (1 / 6f + x / 3f), h * 0.8f + z * h * 0.08f) }
    val ball = frame.cups[frame.ballCup]
    base(ball.x, ball.z).let { drawCircle(BALL, cupW * 0.22f, it - Offset(0f, cupW * 0.22f)) }
    // Back to front, so the cup passing in front covers the one behind.
    for (cup in frame.cups.sortedBy { it.z }) {
      val b = base(cup.x, cup.z) - Offset(0f, cup.lift * h * 0.3f)
      val path =
        Path().apply {
          moveTo(b.x - cupW / 2, b.y)
          lineTo(b.x + cupW / 2, b.y)
          lineTo(b.x + cupW * 0.36f, b.y - cupH)
          lineTo(b.x - cupW * 0.36f, b.y - cupH)
          close()
        }
      drawPath(path, CUP)
      drawRect(CUP_RIM, Offset(b.x - cupW / 2, b.y - cupH * 0.1f), Size(cupW, cupH * 0.1f))
    }
  }
}

/**
 * Below the board: the intro and "Let's play", then how the streak stands and whose turn it is. Both layouts are
 * always laid out, one of them invisible, so the card keeps one height: when it changed, Rin's view above resized and
 * her page stalled a frame (58 ms) just as the table came in, and the cups jumped again at the pass (14T, 2026-09-29).
 */
@Composable
internal fun CupsCard(state: CupsState?, staging: Boolean, passed: Boolean, onStart: () -> Unit) {
  val ready = (state == null || state.phase == CupsPhase.READY) && !staging && !passed
  Card(Modifier.fillMaxWidth()) {
    Box(Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
      Column(
        Modifier.fillMaxWidth().alpha(if (ready) 1f else 0f).then(if (ready) Modifier else Modifier.clearAndSetSemantics {}),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(stringResource(R.string.cups_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(stringResource(R.string.cups_intro), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onStart, enabled = ready, modifier = Modifier.fillMaxWidth().height(64.dp).testTag(CUPS_START_TAG)) {
          Text(stringResource(R.string.pads_start), style = MaterialTheme.typography.titleMedium)
        }
      }
      Column(
        Modifier.fillMaxWidth().alpha(if (ready) 0f else 1f).then(if (ready) Modifier.clearAndSetSemantics {} else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          stringResource(R.string.cups_streak, state?.streak ?: 0, state?.target ?: 3),
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          stringResource(
            when {
              passed -> R.string.mission_passed
              state?.phase == CupsPhase.PICK -> R.string.cups_pick
              state?.right == true -> R.string.cups_right
              state?.right == false -> R.string.cups_pick
              else -> R.string.cups_watch
            }
          ),
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
      }
    }
  }
}

/**
 * A dark walnut table, so the red cups stand out by brightness and not only by hue (3.7, the tester: on the lighter
 * wood they blended in; the two were almost equally bright, 1.07:1). CupsBoardColoursTest keeps them at least 3:1 apart.
 */
internal val WOOD = Color(0xFF4A2E1B)
private val WOOD_EDGE = Color(0xFFB07A4F)
internal val CUP = Color(0xFFD9534F)
private val CUP_RIM = Color(0xFFB8403C)
private val BALL = Color(0xFFFFD54A)

internal const val CUPS_LAYER_TAG = "cups_layer"
internal const val CUPS_START_TAG = "cups_start"

internal fun cupTag(slot: Int) = "cup_$slot"
