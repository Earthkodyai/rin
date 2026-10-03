package io.github.earthkodyai.rinalarm.ui.common

import android.graphics.Matrix
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.earthkodyai.rinalarm.theme.PatternColors
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.theme.TrophyGold
import io.github.earthkodyai.rinalarm.theme.pattern

/**
 * The ring screen's patterned surfaces (UX.4, the user's reference: a pink, striped, diagonal wallpaper of small cute
 * things, slowly moving): stripes, dashed lines, hearts, little alarm clocks, stars and cups, all our own drawing, in
 * rows turned 22°, pink by day and indigo at night. The pattern hangs from the screen, not from the surface, so two
 * surfaces side by side (the pads' panel and the sheet below it) read as one. One tile is drawn once into a bitmap and
 * repeated by a shader, so a drifting frame costs one draw call.
 */
@Composable
fun Modifier.rinPattern(shape: Shape, drift: Boolean = false, motif: PatternMotif = PatternMotif.ALARM): Modifier {
  val colors = RinTheme.palette.pattern
  val density = LocalDensity.current
  val tile = remember(colors, density, motif) { patternTile(colors, density, motif) }
  val shader = remember(tile) { ImageShader(tile, TileMode.Repeated, TileMode.Repeated) }
  val brush = remember(shader) { ShaderBrush(shader) }
  val tilePx = TILE_DP * density.density
  // The phone's uptime, not an animation of its own: every surface drifts in step, so handing the pattern from one
  // surface to another (the pads coming and going over the sheet) never makes it jump.
  val now: State<Long> =
    if (drift) {
      produceState(SystemClock.uptimeMillis()) { while (true) withFrameMillis { value = SystemClock.uptimeMillis() } }
    } else {
      remember { mutableLongStateOf(0L) }
    }
  val origin = remember { mutableStateOf(Offset.Zero) }
  val matrix = remember { Matrix() }
  val layoutDirection = LocalLayoutDirection.current
  return this.onGloballyPositioned { origin.value = it.positionInRoot() }
    .drawBehind {
      // Screen space, turned, then slid along the rows by the drift.
      matrix.reset()
      matrix.setRotate(ANGLE)
      val shift = (now.value % DRIFT_MS) / DRIFT_MS.toFloat() * tilePx
      matrix.preTranslate(shift, 0f)
      matrix.postTranslate(-origin.value.x, -origin.value.y)
      shader.setLocalMatrix(matrix)
      drawOutline(shape.createOutline(size, layoutDirection, this), brush)
    }
}

/** A tile's side: the pattern repeats every 150 dp along its rows and across them. */
private const val TILE_DP = 150f
private const val ANGLE = -22f

/** One tile per this long: a slow drift, as on the reference, not something to watch. */
private const val DRIFT_MS = 12_000

/** What the wallpaper's rows carry: the alarm's clocks, or the tournament's trophies (the user, 2026-10-03). */
enum class PatternMotif {
  ALARM,
  TROPHY,
}

private fun patternTile(c: PatternColors, density: Density, motif: PatternMotif): ImageBitmap {
  val px = (TILE_DP * density.density).toInt().coerceAtLeast(1)
  val image = ImageBitmap(px, px)
  CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
    val d = density.density
    drawRect(c.base)
    // Stripes every 8 dp, 3 dp wide.
    var y = 0f
    while (y < TILE_DP) {
      drawRect(c.stripe, topLeft = Offset(0f, y * d), size = Size(px.toFloat(), 3 * d))
      y += 8f
    }
    // A dashed line across the middle.
    drawLine(
      c.dash,
      Offset(0f, 73 * d),
      Offset(px.toFloat(), 73 * d),
      strokeWidth = 2 * d,
      cap = StrokeCap.Butt,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(7 * d, 6 * d)),
    )
    when (motif) {
      PatternMotif.ALARM -> {
        motif(HEART, 8f, 14f, 26f, d) { drawPath(it, c.heart, alpha = 0.7f) }
        clock(86f, 6f, 38f, d, c)
        motif(STAR, 52f, 96f, 22f, d) { drawPath(it, c.star, alpha = 0.9f) }
        motif(CUP, 112f, 92f, 26f, d) { drawPath(it, c.cup, alpha = 0.6f) }
        motif(HEART, 16f, 112f, 16f, d) { drawPath(it, c.heart, alpha = 0.55f) }
      }
      // The same rows, with the clocks' places taken by gold trophies and the cups' by a medal.
      PatternMotif.TROPHY -> {
        motif(STAR, 10f, 16f, 22f, d) { drawPath(it, c.star, alpha = 0.9f) }
        motif(TROPHY_BODY, 86f, 6f, 38f, d) { trophy() }
        motif(STAR, 52f, 96f, 18f, d) { drawPath(it, c.star, alpha = 0.9f) }
        motif(MEDAL_RIBBON, 110f, 90f, 30f, d) { medal(c) }
        motif(TROPHY_BODY, 14f, 104f, 22f, d) { trophy() }
      }
    }
  }
  return image
}

/** A gold trophy in the 24-unit box, outlined in deep gold, with a white shine on its bowl. */
private fun DrawScope.trophy() {
  drawPath(TROPHY_HANDLES, TrophyGold.deep, style = Stroke(1.6f, cap = StrokeCap.Round))
  drawPath(TROPHY_BODY, TrophyGold.base)
  drawPath(TROPHY_BODY, TrophyGold.deep, style = Stroke(1.2f, join = StrokeJoin.Round))
  drawPath(TROPHY_SHINE, Color.White, alpha = 0.75f, style = Stroke(1.4f, cap = StrokeCap.Round))
}

/** A gold medal on a ribbon in the hearts' pink. */
private fun DrawScope.medal(c: PatternColors) {
  drawPath(MEDAL_RIBBON, c.heart, alpha = 0.85f)
  drawCircle(TrophyGold.base, radius = 6f, center = Offset(12f, 15.5f))
  drawCircle(TrophyGold.deep, radius = 6f, center = Offset(12f, 15.5f), style = Stroke(1.2f))
  drawCircle(TrophyGold.light, radius = 3.4f, center = Offset(12f, 15.5f), style = Stroke(1f))
}

/** Draws a 24-unit icon path at (x, y) dp, `size` dp across. */
private fun DrawScope.motif(path: Path, x: Float, y: Float, size: Float, d: Float, draw: DrawScope.(Path) -> Unit) {
  translate(x * d, y * d) { scale(size * d / 24f, pivot = Offset.Zero) { draw(path) } }
}

private fun DrawScope.clock(x: Float, y: Float, size: Float, d: Float, c: PatternColors) {
  translate(x * d, y * d) {
    scale(size * d / 24f, pivot = Offset.Zero) {
      drawCircle(c.clockFill, radius = 8f, center = Offset(12f, 13f))
      drawCircle(c.clockLine, radius = 8f, center = Offset(12f, 13f), style = Stroke(2f))
      drawPath(CLOCK_HANDS, c.clockLine, style = Stroke(2f, cap = StrokeCap.Round))
    }
  }
}

private fun icon(d: String): Path = PathParser().parsePathString(d).toPath()

private val HEART =
  icon("M12 21s-7.5-4.6-9.6-9.2C.9 8.4 2.9 4.5 6.6 4.5c2.2 0 3.7 1.3 5.4 3.2 1.7-1.9 3.2-3.2 5.4-3.2 3.7 0 5.7 3.9 4.2 7.3C19.5 16.4 12 21 12 21z")
private val STAR = icon("M12 2.5l2.8 6 6.5.7-4.9 4.4 1.4 6.4L12 16.8 6.2 20l1.4-6.4L2.7 9.2l6.5-.7z")
private val CUP = icon("M6 4h12l2 16H4z M4.5 18h15a1.5 1.5 0 0 1 0 3h-15a1.5 1.5 0 0 1 0-3z")
private val CLOCK_HANDS = icon("M12 9v4l2.5 2 M4.5 6.5l3-2.5 M19.5 6.5l-3-2.5")
private val TROPHY_BODY = icon("M7 3.5h10v5.5a5 5 0 0 1-10 0z M10.8 13.6h2.4v3.6h-2.4z M7.5 17.2h9l.8 3.3H6.7z")
private val TROPHY_HANDLES = icon("M7.2 5.5H4.5V7a3.2 3.2 0 0 0 3.3 3.2 M16.8 5.5h2.7V7a3.2 3.2 0 0 1-3.3 3.2")
private val TROPHY_SHINE = icon("M9.6 5.6v3")
private val MEDAL_RIBBON = icon("M6.5 2h4l2.5 6.5-2.6 2z M17.5 2h-4L11 8.5l2.6 2z")
