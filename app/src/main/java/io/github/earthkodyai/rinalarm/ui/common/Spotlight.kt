package io.github.earthkodyai.rinalarm.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.theme.RinTheme
import kotlin.math.roundToInt

/**
 * One thing a spotlight can shine on: its [bounds] in root coordinates, its own corner [radius] ([PILL]: half its
 * height) and the [depth] of its sticker's drop shadow under it, so the light hugs the shape the user sees.
 */
data class Spot(val bounds: Rect, val radius: Dp, val depth: Dp = 0.dp) {
  companion object {
    /** A pill or a circle: the radius is half the height. */
    val PILL = Dp.Infinity
  }
}

/** Where each [K] sits on screen, as its screen lays it out: what a tour's spotlight shines on (UX.8). */
class SpotTargets<K> {
  val bounds = mutableStateMapOf<K, Spot>()
}

/**
 * Records where this element is, for a spotlight (a no-op without [targets]); updates as the page scrolls. [radius]
 * and [depth] are the element's own (its sticker's), so the frame around it runs parallel to its edge.
 */
fun <K> Modifier.spotTarget(targets: SpotTargets<K>?, key: K, radius: Dp, depth: Dp = 0.dp): Modifier =
  if (targets == null) this else onGloballyPositioned { targets.bounds[key] = Spot(it.boundsInRoot(), radius, depth) }

/**
 * A dim layer over the screen with holes for [holes], the [lit] one ringed in Rin's pink. Each hole is its spot's
 * shape grown by the same gap on every side, shadow included, with its corners the spot's radius plus that gap: the
 * frame is concentric with the element's own (the user, 2026-10-02: one radius for all looked off).
 *
 * Taps: with no [open] hole, the whole screen is one blocker that calls [onTap]. With an [open] hole the user works
 * the real control under it (the hands-on walkthrough, the user 2026-10-02), so the layer blocks only the four
 * strips around it: Compose sends a touch to the topmost node it hits, and the drawn layer itself takes no input.
 */
@Composable
fun Spotlight(holes: List<Spot>, lit: Spot?, open: Spot?, onTap: () -> Unit, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  val tap by rememberUpdatedState(onTap)
  var origin by remember { mutableStateOf(Offset.Zero) }
  var size by remember { mutableStateOf(Rect.Zero) }
  val scrim = Color.Black.copy(alpha = if (p.night) 0.62f else 0.5f)
  val ring = p.primary
  val density = LocalDensity.current
  val pad = with(density) { HOLE_PAD.toPx() }
  /** The hole around [spot] in this layer's coordinates, and its corner radius. */
  fun hole(spot: Spot): Pair<Rect, Float> {
    val b = spot.bounds.translate(-origin)
    val depth = with(density) { spot.depth.toPx() }
    val radius = if (spot.radius == Spot.PILL) b.height / 2 else with(density) { spot.radius.toPx() }
    return Rect(b.left, b.top, b.right, b.bottom + depth).inflate(pad) to radius + pad
  }
  Box(
    modifier.fillMaxSize().onGloballyPositioned {
      origin = it.positionInRoot()
      size = Rect(0f, 0f, it.size.width.toFloat(), it.size.height.toFloat())
    }
  ) {
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.clearAndSetSemantics {}) {
      drawRect(scrim)
      holes.forEach { spot ->
        val (r, radius) = hole(spot)
        drawRoundRect(Color.Black, r.topLeft, r.size, CornerRadius(radius), blendMode = BlendMode.Clear)
      }
      lit?.let { spot ->
        val (r, radius) = hole(spot)
        drawRoundRect(ring, r.topLeft, r.size, CornerRadius(radius), style = Stroke(3.dp.toPx()))
      }
    }
    val gap = open?.let { hole(it).first }
    val blockers =
      if (gap == null) {
        listOf(size)
      } else {
        listOf(
          Rect(0f, 0f, size.width, gap.top),
          Rect(0f, gap.bottom, size.width, size.height),
          Rect(0f, gap.top, gap.left, gap.bottom),
          Rect(gap.right, gap.top, size.width, gap.bottom),
        )
      }
    blockers.filter { it.width > 0f && it.height > 0f }.forEach { r ->
      Box(
        Modifier.absoluteOffset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
          .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
          .pointerInput(Unit) { detectTapGestures { tap() } }
      )
    }
  }
}

/** The even gap between an element and its frame. */
private val HOLE_PAD = 6.dp
