package io.github.earthkodyai.rinalarm.ui.common

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.github.earthkodyai.rinalarm.theme.RinTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The moving ground of the home screen and the alarm editor (the user, 2026-10-02): breakfast floating up by day
 * (toast, a fried egg, a mug, a strawberry, pancakes, all our own drawing) and a meteor shower over twinkling stars at
 * night. Decoration only: nothing reads it out, it sits under the cards, and it stands still when the phone's
 * animations are off. Every frame comes from the phone's uptime and fixed seeds, so there is no state to keep and the
 * scene carries on, not restarts, between home and the editor. [meteorRate] thins the shower; 0 leaves the night sky
 * with its twinkling stars alone.
 */
@Composable
fun RinBackdrop(modifier: Modifier = Modifier, meteorRate: Float = 1f) {
  val p = RinTheme.palette
  val density = LocalDensity.current
  val foods = remember(density) { FOOD_DRAWINGS.map { foodImage(it, density) } }
  val now = backdropClock()
  Canvas(modifier.clearAndSetSemantics {}) {
    val t = now.value
    if (p.night) {
      stars(t, p.glow)
      if (meteorRate > 0f) meteors(t, p.glow, meteorRate)
    } else {
      breakfast(t, foods)
    }
  }
}

/** Frame time, or a fixed moment when the user has turned animations off (Settings › Remove animations). */
@Composable
private fun backdropClock(): State<Long> {
  val moving = remember { ValueAnimator.areAnimatorsEnabled() }
  return if (moving) {
    decorClock()
  } else {
    remember { mutableLongStateOf(STILL_AT) }
  }
}

/**
 * The phone's uptime for slow decoration (the backdrop, the drifting wallpaper), [DECOR_FPS] times a second. Updated
 * on every frame, it redrew the whole screen, and Rin's WebView with it, 120 times a second on the 14T: 1.1 CPU cores
 * on the home screen with nothing moving faster than a floating mug (6.9, docs/spikes/6.9-optimise.md).
 */
@Composable
internal fun decorClock(): State<Long> =
  produceState(SystemClock.uptimeMillis()) {
    while (true) {
      withFrameMillis { value = SystemClock.uptimeMillis() }
      delay(1000L / DECOR_FPS - FRAME_SLACK_MS)
    }
  }

/** Rin's own cap on the home screen (Framing.STRIP), so the two move at one pace. */
internal const val DECOR_FPS = 30
/** The next frame comes up to one vsync after the delay, so the delay leaves room for it. */
private const val FRAME_SLACK_MS = 4L

/** A moment when, with animations off, the still scene has a meteor and food spread over the screen. */
private const val STILL_AT = 31_400L

// ---- Day: breakfast ----

/** One floating item: which food, where across the screen, how big, how slow; seeded so the scene never repeats soon. */
private class Floater(val food: Int, val x: Float, val sizeDp: Float, val riseMs: Long, val phaseMs: Long, val swayMs: Long, val tilt: Float)

private val FLOATERS =
  List(11) { i ->
    Floater(
      food = i % FOOD_COUNT,
      // Spread across the width in a shuffled order, so two of a kind never rise side by side.
      x = (0.06f + 0.88f * ((i * 7) % 11) / 10f),
      sizeDp = 30f + 14f * unit(i.toLong(), 1),
      riseMs = 22_000L + (12_000 * unit(i.toLong(), 2)).toLong(),
      phaseMs = (40_000 * unit(i.toLong(), 3)).toLong(),
      swayMs = 4_000L + (3_000 * unit(i.toLong(), 4)).toLong(),
      tilt = 8f + 10f * unit(i.toLong(), 5),
    )
  }

/** Light enough that text on the ground stays readable over it (ink stays above 7:1 on the blend). */
private const val FOOD_ALPHA = 0.65f
private const val FOOD_MAX_DP = 44f

private fun DrawScope.breakfast(t: Long, foods: List<ImageBitmap>) {
  val d = density
  for ((i, f) in FLOATERS.withIndex()) {
    val px = f.sizeDp * d
    val rise = ((t + f.phaseMs) % f.riseMs) / f.riseMs.toFloat()
    val swing = sin(2 * PI * ((t + f.phaseMs) % f.swayMs) / f.swayMs + i).toFloat()
    val cx = f.x * size.width + swing * 10 * d
    val cy = size.height + px - rise * (size.height + 2 * px)
    val image = foods[f.food]
    withTransform({
      translate(cx - px / 2, cy - px / 2)
      rotate(swing * f.tilt, pivot = Offset(px / 2, px / 2))
    }) {
      drawImage(
        image,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(px.toInt(), px.toInt()),
        alpha = FOOD_ALPHA,
      )
    }
  }
}

/** Each food drawn once into a bitmap at its largest size; a frame only places them. */
private fun foodImage(draw: DrawScope.() -> Unit, density: Density): ImageBitmap {
  val px = (FOOD_MAX_DP * density.density).toInt().coerceAtLeast(1)
  val image = ImageBitmap(px, px)
  CanvasDrawScope().draw(density, LayoutDirection.Ltr, ImageCanvas(image), Size(px.toFloat(), px.toFloat())) {
    scale(px / 24f, pivot = Offset.Zero) { draw() }
  }
  return image
}

// The foods, each in a 24-unit square.

private val FOOD_DRAWINGS: List<DrawScope.() -> Unit> =
  listOf(
    { // Toast with butter.
      drawPath(TOAST_CRUST, Color(0xFFD9954A))
      drawPath(TOAST_BREAD, Color(0xFFF7D89A))
      drawRoundRect(Color(0xFFFFE680), topLeft = Offset(9.5f, 10.5f), size = Size(5f, 4f), cornerRadius = CornerRadius(1f))
    },
    { // A fried egg.
      drawPath(EGG_WHITE, Color.White)
      drawPath(EGG_WHITE, Color(0xFFE9CDB8), style = Stroke(1f))
      drawCircle(Color(0xFFFFB420), radius = 4.2f, center = Offset(12.5f, 11.8f))
      drawCircle(Color.White, radius = 1.2f, center = Offset(11.2f, 10.4f), alpha = 0.85f)
    },
    { // A pink mug of coffee, steaming.
      drawPath(MUG_STEAM, Color(0xFFD8A898), style = Stroke(1.4f, cap = StrokeCap.Round))
      drawCircle(Color(0xFFF07A9A), radius = 3f, center = Offset(18f, 13.5f), style = Stroke(2f))
      drawPath(MUG_BODY, Color(0xFFFF8FAB))
      drawOval(Color(0xFF8A5434), topLeft = Offset(5.4f, 7f), size = Size(12.2f, 2.4f))
      translate(9f, 12f) { scale(5f / 24f, pivot = Offset.Zero) { drawPath(HEART, Color.White, alpha = 0.9f) } }
    },
    { // A strawberry.
      drawPath(BERRY, Color(0xFFFF4D6D))
      drawPath(BERRY_LEAVES, Color(0xFF3DBE9A))
      for ((x, y) in BERRY_SEEDS) drawCircle(Color(0xFFFFE08A), radius = 0.55f, center = Offset(x, y))
    },
    { // Pancakes with butter and syrup.
      val cake = Color(0xFFE39A4C)
      drawRoundRect(cake, topLeft = Offset(3f, 16f), size = Size(18f, 4f), cornerRadius = CornerRadius(2f))
      drawRoundRect(Color(0xFFEBA95E), topLeft = Offset(3.5f, 12.5f), size = Size(17f, 4f), cornerRadius = CornerRadius(2f))
      drawRoundRect(Color(0xFFF2BE73), topLeft = Offset(4f, 9f), size = Size(16f, 4f), cornerRadius = CornerRadius(2f))
      drawPath(SYRUP, Color(0xFFA85A24))
      drawRoundRect(Color(0xFFFFE68A), topLeft = Offset(10f, 6.4f), size = Size(4f, 3f), cornerRadius = CornerRadius(0.6f))
    },
  )

private const val FOOD_COUNT = 5

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private val TOAST_CRUST = path("M4 21V10.2C2.2 9.4 1.8 7.4 2.6 5.8 3.6 3.8 6 3.2 8 3.2h8c2 0 4.4.6 5.4 2.6.8 1.6.4 3.6-1.4 4.4V21z")
private val TOAST_BREAD = path("M6 19.3V9.2C4.6 8.7 4.2 7.6 4.6 6.6 5.2 5.4 6.6 5 8 5h8c1.4 0 2.8.4 3.4 1.6.4 1 0 2.1-1.4 2.6V19.3z")
private val EGG_WHITE = path("M12 3C17 2.5 21.5 6 21 11 20.8 15 22 19 17 20.5 13 21.8 9 21 6 19.5 2.5 17.5 2 13 3.5 9.5 5 5.5 8 3.3 12 3z")
private val MUG_STEAM = path("M9.5 5.6c-1.2-1.2 1.2-2.3 0-3.6M13.5 5.6c-1.2-1.2 1.2-2.3 0-3.6")
private val MUG_BODY = path("M5 8h13v9a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4z")
private val HEART =
  path("M12 21s-7.5-4.6-9.6-9.2C.9 8.4 2.9 4.5 6.6 4.5c2.2 0 3.7 1.3 5.4 3.2 1.7-1.9 3.2-3.2 5.4-3.2 3.7 0 5.7 3.9 4.2 7.3C19.5 16.4 12 21 12 21z")
private val BERRY = path("M12 21.5C6 18.5 3.5 13 4.5 9.5 5.5 6.5 9 6.5 12 7.5 15 6.5 18.5 6.5 19.5 9.5 20.5 13 18 18.5 12 21.5z")
private val BERRY_LEAVES = path("M12 8.6L7.6 5.6 10.4 5.5 12 2.8 13.6 5.5 16.4 5.6z")
private val BERRY_SEEDS = listOf(8f to 11f, 12f to 10.6f, 16f to 11f, 10f to 14.4f, 14f to 14.4f, 12f to 17.8f, 7.6f to 14f, 16.4f to 14f)
private val SYRUP = path("M4.6 9.6h14.8v1.4c0 .9-1.3.9-1.3 0v-.2h-2.9v2c0 1-1.4 1-1.4 0v-2H8.3v.9c0 .9-1.3.9-1.3 0v-.9H4.6z")

// ---- Night: a meteor shower ----

private const val STAR_COUNT = 18

private fun DrawScope.stars(t: Long, glow: Color) {
  val d = density
  for (i in 0 until STAR_COUNT) {
    val k = 1_000L + i
    val period = 2_400L + (2_600 * unit(k, 3)).toLong()
    val wave = sin(2 * PI * ((t + (period * unit(k, 4)).toLong()) % period) / period).toFloat()
    drawCircle(
      if (i % 3 == 2) glow else Color.White,
      radius = (1f + 0.8f * unit(k, 5)) * d,
      center = Offset(unit(k, 1) * size.width, unit(k, 2) * size.height),
      alpha = 0.5f + 0.35f * wave,
    )
  }
}

/** A meteor may start in each slot of this length; nearly three a second over the whole screen. */
private const val SLOT_MS = 300L
private const val METEOR_CHANCE = 0.85f

private fun DrawScope.meteors(t: Long, glow: Color, rate: Float) {
  val d = density
  val slot = t / SLOT_MS
  // A meteor lives up to 1.6 s and starts up to a slot late, so the last seven slots can still be in flight.
  for (k in slot - 7..slot) {
    if (unit(k, 0) > METEOR_CHANCE * rate) continue
    val life = 900f + 700f * unit(k, 1)
    val age = t - k * SLOT_MS - (SLOT_MS * unit(k, 2)).toLong()
    if (age < 0 || age > life) continue
    val p = age / life
    // Down and to the left, 25–40° under the horizon. Starts spread over the whole sky, a little past its top and right
    // edges for the ones that fly in, so the bottom of the screen gets as many as the top (the user, 2026-10-02).
    val angle = (25f + 15f * unit(k, 3)) * PI.toFloat() / 180f
    val dir = Offset(-cos(angle), sin(angle))
    val from = Offset((0.1f + 1.1f * unit(k, 4)) * size.width, (-0.1f + 1.0f * unit(k, 5)) * size.height)
    val travel = (240f + 160f * unit(k, 6)) * d
    val head = from + dir * (travel * (1 - (1 - p) * (1 - p)))
    val tail = head - dir * ((90f + 70f * unit(k, 7)) * d * min(1f, p * 4))
    val alpha = min(1f, p / 0.15f) * min(1f, (1 - p) / 0.35f)
    drawLine(
      Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = alpha)), start = tail, end = head),
      tail,
      head,
      strokeWidth = 2.6f * d,
      cap = StrokeCap.Round,
    )
    drawCircle(glow, radius = 7f * d, center = head, alpha = 0.35f * alpha)
    drawCircle(Color.White, radius = 2.2f * d, center = head, alpha = alpha)
  }
}

/** A fixed pseudo-random number in [0, 1) for (key, salt): SplitMix64, so the scene needs no stored randomness. */
private fun unit(key: Long, salt: Int): Float {
  var z = key * -7046029254386353131L + salt * 7146167797367308313L
  z = (z xor (z ushr 30)) * -4658895280553007687L
  z = (z xor (z ushr 27)) * -7723592293110705685L
  z = z xor (z ushr 31)
  return (z ushr 40).toFloat() / (1 shl 24)
}
