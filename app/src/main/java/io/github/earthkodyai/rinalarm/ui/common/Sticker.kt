package io.github.earthkodyai.rinalarm.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.theme.RinTheme

/**
 * The playful "sticker" look of the UX phase: a rounded fill with a solid drop [depth] below it (no blur), and at
 * night a quiet outline instead of most of the drop, as on the mockups' starry boards. [radius] rounds both.
 */
@Composable
@ReadOnlyComposable
fun Modifier.sticker(
  fill: Color = RinTheme.palette.card,
  radius: Dp = 22.dp,
  depth: Dp = 4.dp,
  shadow: Color = RinTheme.palette.hardShadow,
  outline: Color? = if (RinTheme.palette.night) RinTheme.palette.line else null,
): Modifier {
  val shape = RoundedCornerShape(radius)
  return this.drawBehind {
      val r = radius.toPx()
      drawRoundRect(shadow, topLeft = Offset(0f, depth.toPx()), size = size, cornerRadius = CornerRadius(r, r))
    }
    .background(fill, shape)
    .let { if (outline != null) it.border(1.dp, outline, shape) else it }
}
