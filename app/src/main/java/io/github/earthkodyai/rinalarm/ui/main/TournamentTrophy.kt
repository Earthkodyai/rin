package io.github.earthkodyai.rinalarm.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.earthkodyai.rinalarm.theme.RinRounded
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The tournament's way in on the home panel (G.5, the user's picks, mockup docs/ux/g5-trophy/trophy4.png): a flat gold
 * trophy standing upright with small sparkles twinkling all over it and a big glint on its rim now and then, and an
 * explosion bubble saying TOURNAMENT! that pops up with a wiggle, stays [SHOUT_SHOWN_MS] and is gone [SHOUT_HIDDEN_MS].
 * Both are drawn from simple shapes in a fixed design space (the mockup's), so they scale to any size.
 */

/** How long the bubble stays up, then how long it is gone: up 3 s once every 10 s (the user's pick, 2026-10-03). */
internal const val SHOUT_SHOWN_MS = 3_000L
internal const val SHOUT_HIDDEN_MS = 7_000L

/** The bubble rising out of the cup, and sinking back into it. */
private const val SHOUT_RISE_MS = 520
internal const val SHOUT_SINK_MS = 260L

private val Gold = Color(0xFFFCB918)
private val GoldShade = Color(0xFFEAA012)
private val GoldDeep = Color(0xFFD98C0C)
private val GoldLight = Color(0xFFFFD04A)
private val Ink = Color(0xFF231B24)

/** The trophy's design space: 200 wide, 252 tall, its base's foot on the bottom edge. */
private const val TROPHY_W = 200f
private const val TROPHY_H = 252f

/** Sparkles over the cup: x, y, size, and where in the twinkle cycle each starts, so they never blink together. */
private val Sparkles =
  listOf(
    floatArrayOf(68f, 74f, 5f, 0f),
    floatArrayOf(132f, 74f, 5f, .5f),
    floatArrayOf(100f, 100f, 4f, .25f),
    floatArrayOf(22f, 58f, 3.5f, .75f),
    floatArrayOf(178f, 58f, 3.5f, .1f),
    floatArrayOf(100f, 190f, 3.5f, .6f),
  )

/**
 * [kick] turning true knocks the trophy (the bubble popping up beside it): it rocks on its base, hard at first, and
 * settles as a weighted thing would (an underdamped spring with a starting swing), not on a fixed wobble.
 */
@Composable
internal fun TournamentTrophy(kick: Boolean, modifier: Modifier = Modifier) {
  val rock = remember { Animatable(0f) }
  LaunchedEffect(kick) {
    if (kick) rock.animateTo(0f, spring(dampingRatio = ROCK_DAMPING, stiffness = ROCK_STIFFNESS), initialVelocity = ROCK_KICK)
  }
  val twinkle = rememberInfiniteTransition(label = "trophy")
  val t by twinkle.animateFloat(0f, 1f, infiniteRepeatable(tween(TWINKLE_MS, easing = LinearEasing)), label = "twinkle")
  // The big glint: a quick flash on the rim's right, once every GLINT_MS.
  val glint by
    twinkle.animateFloat(
      0f,
      0f,
      infiniteRepeatable(
        keyframes {
          durationMillis = GLINT_MS
          0f at 0
          0f at GLINT_MS - 600
          1f at GLINT_MS - 350
          0f at GLINT_MS - 100
        },
        RepeatMode.Restart,
      ),
      label = "glint",
    )
  val shapes = remember { TrophyShapes() }
  Canvas(
    modifier.graphicsLayer {
      rotationZ = rock.value
      transformOrigin = TransformOrigin(0.5f, 1f)
    }
  ) {
    val k = minOf(size.width / TROPHY_W, size.height / TROPHY_H)
    translate((size.width - TROPHY_W * k) / 2, size.height - TROPHY_H * k) {
      scale(k, pivot = Offset.Zero) {
        drawTrophy(shapes)
        for ((x, y, s, phase) in Sparkles) {
          // Each sparkle swells and fades once a cycle, off for the rest, from its own phase.
          val u = ((t + phase) % 1f) * 2f
          val a = if (u < 1f) sin(u * PI).toFloat() else 0f
          if (a > 0.02f) sparkle(Offset(x, y), s * (0.5f + 0.5f * a), Color.White.copy(alpha = a))
        }
        if (glint > 0.02f) {
          val c = Offset(146f, 42f)
          drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .9f * glint), Color.Transparent), c, 16f), 16f, c)
          sparkle(c, 14f * glint, Color.White)
        }
      }
    }
  }
}

/** About 19° at the first swing, a few swings each smaller, still within ~1.5 s (to ~10% of the first). */
private const val ROCK_KICK = 260f
private const val ROCK_DAMPING = 0.12f
private const val ROCK_STIFFNESS = 180f
private const val TWINKLE_MS = 2_400
private const val GLINT_MS = 4_200

private class TrophyShapes {
  val bowl = path { moveTo(46f, 54f); lineTo(154f, 54f); cubicTo(154f, 100f, 130f, 124f, 100f, 126f); cubicTo(70f, 124f, 46f, 100f, 46f, 54f); close() }
  val bowlShade = path { moveTo(46f, 54f); lineTo(60f, 54f); cubicTo(60f, 96f, 76f, 118f, 100f, 126f); cubicTo(70f, 124f, 46f, 100f, 46f, 54f); close() }
  val gloss = path { moveTo(128f, 74f); cubicTo(134f, 76f, 134f, 92f, 126f, 100f); cubicTo(121f, 104f, 118f, 98f, 121f, 92f); cubicTo(124f, 86f, 124f, 80f, 128f, 74f); close() }
  val knot = path { moveTo(86f, 126f); lineTo(114f, 126f); lineTo(120f, 135f); lineTo(114f, 144f); lineTo(86f, 144f); lineTo(80f, 135f); close() }
  val knotShade = path { moveTo(80f, 135f); lineTo(120f, 135f); lineTo(114f, 144f); lineTo(86f, 144f); close() }
  val stem = path { moveTo(90f, 144f); lineTo(110f, 144f); lineTo(122f, 222f); lineTo(78f, 222f); close() }
  val stemShade = path { moveTo(90f, 144f); lineTo(97f, 144f); lineTo(88f, 222f); lineTo(78f, 222f); close() }
  val stemLight = path { moveTo(104f, 144f); lineTo(110f, 144f); lineTo(122f, 222f); lineTo(114f, 222f); close() }
  /** The left handle; the right one is its mirror. It curls from the rim down to a little scroll by the bowl's foot. */
  val handle = path {
    moveTo(48f, 46f)
    cubicTo(40f, 22f, 14f, 22f, 10f, 44f)
    cubicTo(6f, 70f, 30f, 92f, 56f, 112f)
    cubicTo(64f, 118f, 66f, 126f, 60f, 130f)
    cubicTo(55f, 133f, 50f, 128f, 54f, 124f)
  }
  val handleShade = path { moveTo(14f, 50f); cubicTo(14f, 72f, 34f, 92f, 54f, 108f) }
}

private fun path(build: Path.() -> Unit) = Path().apply(build)

private fun DrawScope.drawTrophy(p: TrophyShapes) {
  val rope = Stroke(9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  for (mirror in listOf(false, true)) {
    scale(if (mirror) -1f else 1f, 1f, pivot = Offset(TROPHY_W / 2, 0f)) {
      drawPath(p.handle, Gold, style = rope)
      drawPath(p.handleShade, GoldShade, style = Stroke(3.5f, cap = StrokeCap.Round))
    }
  }
  drawPath(p.bowl, Gold)
  drawPath(p.bowlShade, GoldShade)
  // The flat rim: a band, its shaded underside and a lit top edge.
  drawRect(Gold, Offset(40f, 40f), Size(120f, 16f))
  drawRect(GoldShade, Offset(40f, 52f), Size(120f, 5f))
  drawRect(GoldLight, Offset(40f, 40f), Size(120f, 4f))
  drawPath(p.gloss, Color.White.copy(alpha = .92f))
  drawCircle(Color.White.copy(alpha = .92f), 3.6f, Offset(117f, 108f))
  drawPath(p.knot, Gold)
  drawPath(p.knotShade, GoldShade)
  drawPath(p.stem, Gold)
  drawPath(p.stemShade, GoldShade)
  drawPath(p.stemLight, GoldLight.copy(alpha = .55f))
  drawRect(Gold, Offset(70f, 222f), Size(60f, 16f))
  drawRect(GoldShade, Offset(70f, 222f), Size(10f, 16f))
  drawRect(GoldShade, Offset(62f, 238f), Size(76f, 14f))
  drawRect(GoldDeep, Offset(62f, 248f), Size(76f, 4f))
}

/** A four-point star with concave sides. */
private fun DrawScope.sparkle(c: Offset, s: Float, color: Color) {
  val q = s * 0.16f
  val star = path {
    moveTo(c.x, c.y - s)
    quadraticTo(c.x + q, c.y - q, c.x + s, c.y)
    quadraticTo(c.x + q, c.y + q, c.x, c.y + s)
    quadraticTo(c.x - q, c.y + q, c.x - s, c.y)
    quadraticTo(c.x - q, c.y - q, c.x, c.y - s)
    close()
  }
  drawPath(star, color)
}

/**
 * The bubble's right half, from the top valley round to the bottom one, in a space where its body is about 200 × 100
 * centred on 0,0; the left half is its mirror (the user: nothing lopsided). Valleys and spike tips alternate.
 */
private val BubbleRightHalf =
  listOf(
    0.0f to -44.1f, 27.7f to -58.1f, 44.5f to -38.5f, 93.0f to -44.4f, 77.9f to -22.5f, 124.9f to -13.2f, 88.6f to 0.0f,
    125.7f to 16.5f, 78.6f to 22.7f, 87.4f to 45.0f, 45.0f to 39.0f, 25.4f to 57.7f, 0.0f to 46.0f,
  )

/** The bubble's design space: 300 × 140 around its centre. */
private const val BUBBLE_W = 300f
private const val BUBBLE_H = 140f

private fun bubblePath(): Path {
  val ring = BubbleRightHalf + BubbleRightHalf.drop(1).dropLast(1).reversed().map { (x, y) -> -x to y }
  return path {
    moveTo(ring[0].first, ring[0].second)
    for (i in ring.indices) {
      val (px, py) = ring[i]
      val (qx, qy) = ring[(i + 1) % ring.size]
      // Each side bows inward a little, which gives the spikes their curved, inked look.
      quadraticTo((px + qx) / 2 * .9f, (py + qy) / 2 * .9f, qx, qy)
    }
    close()
  }
}

/**
 * The TOURNAMENT! bubble: pink by day, white by night, halftone dots that grow toward its edge, a thick ink outline and
 * a soft drop shadow. [shown] springs it out of the trophy with a wiggle; hiding sinks it back in, faster as it goes
 * (the user: it should come out of the cup and go back into it). [from] is that point in the cup, as a fraction of
 * this box (below it, so past 1); drawn behind the trophy, the bubble is hidden by the cup while it is small.
 */
@Composable
internal fun ShoutBubble(shown: Boolean, night: Boolean, from: TransformOrigin, modifier: Modifier = Modifier) {
  val pop = remember { Animatable(0f) }
  val wiggle = remember { Animatable(0f) }
  LaunchedEffect(shown) {
    if (shown) {
      wiggle.snapTo(0f)
      coroutineScope {
        // It peeks out of the cup first, slowly enough to be seen there, then is flung up past its place and settles.
        launch {
          pop.animateTo(
            1f,
            keyframes {
              durationMillis = SHOUT_RISE_MS
              0.16f at 170 using FastOutSlowInEasing
              1.08f at 400 using FastOutSlowInEasing
            },
          )
        }
        launch {
          delay(SHOUT_RISE_MS * 2L / 3)
          wiggle.animateTo(
            0f,
            keyframes {
              durationMillis = 700
              -9f at 90
              8f at 200
              -6f at 320
              4f at 440
              -2f at 560
            },
          )
        }
      }
    } else {
      pop.animateTo(0f, tween(SHOUT_SINK_MS.toInt(), easing = FastOutLinearInEasing))
    }
  }
  val fill = if (night) Color.White else Color(0xFFEC5A8C)
  val dots = if (night) Color(0xFFD3CFE6) else Color(0xFFB8306A)
  val text = if (night) Color(0xFFEC5A8C) else Color.White
  val outline = remember { bubblePath() }
  Box(
    modifier
      // Out of the cup: grows from [from], so it also rises from there.
      .graphicsLayer {
        scaleX = pop.value
        scaleY = pop.value
        transformOrigin = from
        // Solid until it is nearly back in, so it is seen going into the cup rather than fading.
        alpha = (pop.value * 4f).coerceIn(0f, 1f)
      }
      // The wiggle turns it about its own middle.
      .graphicsLayer { rotationZ = wiggle.value },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.matchParentSize()) {
      val k = minOf(size.width / BUBBLE_W, size.height / BUBBLE_H)
      translate(size.width / 2, size.height / 2) {
        scale(k, pivot = Offset.Zero) {
          translate(6f, 6f) { drawPath(outline, Color.Black.copy(alpha = .22f)) }
          drawPath(outline, fill)
          clipPath(outline, ClipOp.Intersect) {
            var y = -84f
            var row = 0
            while (y <= 84f) {
              var x = -168f + if (row % 2 == 1) 3f else 0f
              while (x <= 168f) {
                val r = ((hypot(x / 100f, y / 50f) - .45f) * 2.4f).coerceAtMost(2.3f)
                if (r > .15f) drawCircle(dots, r, Offset(x, y))
                x += 6f
              }
              y += 6f
              row++
            }
          }
          drawPath(outline, Ink, style = Stroke(5.5f, join = StrokeJoin.Round))
        }
      }
    }
    val style =
      TextStyle(
        fontFamily = RinRounded,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.5.sp,
        letterSpacing = (-0.6).sp,
        // A forward lean for the comic shout.
        textGeometricTransform = TextGeometricTransform(skewX = -0.18f),
      )
    Text("TOURNAMENT!", style = style.copy(color = Ink, drawStyle = Stroke(width = 7f, join = StrokeJoin.Round)))
    Text("TOURNAMENT!", style = style.copy(color = text))
  }
}

/** The trophy's size on the home panel, and the bubble's. */
internal val TrophySize = Modifier.size(width = 67.dp, height = 84.dp)
internal val ShoutSize = Modifier.size(width = 164.dp, height = 76.dp)
