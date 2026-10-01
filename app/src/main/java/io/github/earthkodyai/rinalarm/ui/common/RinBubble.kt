package io.github.earthkodyai.rinalarm.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.earthkodyai.rinalarm.theme.RinTheme
import kotlin.math.ceil

/**
 * Rin's line in her speech bubble wherever she speaks (UX.2 home, UX.4 ring screen: the user's pick over the subtitle
 * band). Pops in with the line and fades out after it, keeping the last text while it goes. [dots] says where its two
 * dots go; [textModifier] reaches the text (a test tag).
 */
/**
 * Where the bubble's two dots trail (the user, 2026-10-01): beside it toward her on the home panel, where she stands
 * to its right; under its left end on the ring screen, where she stands in the middle and dots beside it, or under
 * its right end, landed on her face and hair.
 */
enum class BubbleDots(internal val wide: Dp, internal val low: Dp) {
  SIDE(18.dp, 3.dp),
  BELOW_LEFT(0.dp, 21.dp),
}

@Composable
fun RinBubble(
  line: String?,
  modifier: Modifier = Modifier,
  dots: BubbleDots = BubbleDots.SIDE,
  textModifier: Modifier = Modifier,
) {
  var shown by remember { mutableStateOf(line) }
  if (line != null) shown = line
  AnimatedVisibility(
    line != null,
    modifier,
    enter = fadeIn() + scaleIn(initialScale = 0.85f),
    exit = fadeOut() + scaleOut(targetScale = 0.9f),
  ) {
    Bubble(shown.orEmpty(), dots, textModifier)
  }
}

/**
 * Her line in a rounded bubble with two little dots trailing toward her (the user's pick, 2026-10-01, over tails; drafts
 * in docs/ux/bubble-tails.png). As wide as its longest line, not its widest allowed width, so the padding is even on
 * both sides and the text sits centred (the wrapped text had left a gap on the right).
 */
@Composable
private fun Bubble(text: String, dots: BubbleDots, modifier: Modifier) {
  val p = RinTheme.palette
  val style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
  val measurer = rememberTextMeasurer()
  val width =
    with(LocalDensity.current) {
      val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = BUBBLE_TEXT_MAX.roundToPx()))
      val widest = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) } ?: 0f
      ceil(widest).toDp()
    }
  Text(
    text,
    style = style,
    color = p.ink,
    modifier =
      Modifier.drawBehind { bubbleDots(dots, p.bubble, p.hardShadow, if (p.night) p.line else null) }
        .sticker(fill = p.bubble, depth = 3.dp, shape = remember(dots) { BubbleShape(dots) })
        .padding(start = 14.dp, end = 14.dp + dots.wide, top = 11.dp, bottom = 11.dp + dots.low)
        .width(width)
        .semantics { liveRegion = LiveRegionMode.Polite }
        .then(modifier),
  )
}

private val BUBBLE_TEXT_MAX = 140.dp

/** The rounded box, leaving room beside or below it for the [dots]. */
private class BubbleShape(private val dots: BubbleDots) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val d = density.density
    val r = 18f * d
    val box = RoundRect(0f, 0f, size.width - dots.wide.value * d, size.height - dots.low.value * d, CornerRadius(r, r))
    // A path, not Outline.Rounded: border() draws a rounded outline at the node's full size, dots' room included.
    return Outline.Generic(Path().apply { addRoundRect(box) })
  }
}

/**
 * The two dots, a 4.5 dp one and a 2.8 dp one further on, both stepping toward her: beside the box's lower right
 * ([BubbleDots.SIDE]), or under its left end, down and to the right ([BubbleDots.BELOW_LEFT]). Their own light 1.5 dp
 * drop, not the box's 3 dp, which made them look like buttons at night.
 */
private fun DrawScope.bubbleDots(dots: BubbleDots, fill: Color, shadow: Color, outline: Color?) {
  val d = density
  val bw = size.width - dots.wide.value * d
  val bh = size.height - dots.low.value * d
  val at =
    when (dots) {
      BubbleDots.SIDE -> listOf(Offset(bw + 6 * d, bh * 0.78f) to 4.5f * d, Offset(bw + 15 * d, bh * 0.98f) to 2.8f * d)
      BubbleDots.BELOW_LEFT -> listOf(Offset(24 * d, bh + 9 * d) to 4.5f * d, Offset(36 * d, bh + 17 * d) to 2.8f * d)
    }
  for ((centre, radius) in at) {
    drawCircle(shadow, radius, centre + Offset(0f, 1.5f * d))
    drawCircle(fill, radius, centre)
    if (outline != null) drawCircle(outline, radius, centre, style = Stroke(1f * d))
  }
}
