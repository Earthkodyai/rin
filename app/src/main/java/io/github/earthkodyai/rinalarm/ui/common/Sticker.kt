package io.github.earthkodyai.rinalarm.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.theme.TrophyGold

/**
 * The playful "sticker" look of the UX phase: a rounded fill with a solid drop [depth] below it (no blur), and at
 * night a quiet outline instead of most of the drop, as on the mockups' starry boards. [radius] rounds both, or
 * [shape] gives another outline (Rin's bubble with its tail), which the drop follows.
 */
@Composable
@ReadOnlyComposable
fun Modifier.sticker(
  fill: Color = RinTheme.palette.card,
  radius: Dp = 22.dp,
  depth: Dp = 4.dp,
  shadow: Color = RinTheme.palette.hardShadow,
  outline: Color? = if (RinTheme.palette.night) RinTheme.palette.line else null,
  shape: Shape = RoundedCornerShape(radius),
): Modifier =
  this.drawBehind {
      val drop = shape.createOutline(size, layoutDirection, this)
      translate(top = depth.toPx()) { drawOutline(drop, shadow) }
    }
    .background(fill, shape)
    .let { if (outline != null) it.border(1.dp, outline, shape) else it }

/** A round sticker button with one icon (the top bars' back, settings, diagnostics); [description] is read out. */
@Composable
fun RoundIconButton(icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  Box(
    modifier
      .size(48.dp)
      .sticker(radius = 24.dp, depth = 3.dp)
      .clip(CircleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Icon(painterResource(icon), contentDescription = null, tint = p.ink, modifier = Modifier.size(22.dp))
  }
}

/** The big pink pill: a screen's main action (Add alarm, Save alarm). */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Int? = null, enabled: Boolean = true) {
  val p = RinTheme.palette
  Row(
    modifier
      .height(58.dp)
      .alpha(if (enabled) 1f else 0.6f)
      .sticker(fill = p.primary, radius = 29.dp, depth = 5.dp, shadow = p.primaryShadow, outline = null)
      .clip(RoundedCornerShape(29.dp))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(start = if (icon != null) 20.dp else 24.dp, end = 24.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = p.onPrimary)
    Text(
      text,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp),
      color = p.onPrimary,
      modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp),
    )
  }
}

/** Switches in the app's look: a pink track when on, a quiet one when off, in either look. */
@Composable
fun rinSwitchColors(): SwitchColors {
  val p = RinTheme.palette
  return SwitchDefaults.colors(
    checkedThumbColor = p.onPrimary,
    checkedTrackColor = p.primary,
    checkedBorderColor = p.primary,
    uncheckedThumbColor = if (p.night) p.line else p.card,
    uncheckedTrackColor = if (p.night) p.ground else p.line,
    uncheckedBorderColor = p.line,
  )
}

/**
 * The tournament's gold sticker (its home button and its start page's banner, 2026-10-03): a trophy's gold lit from
 * above, a deep gold drop, and a white shine along the top edge. The same by day and night.
 */
@Composable
@ReadOnlyComposable
fun Modifier.goldSticker(radius: Dp, depth: Dp = 4.dp): Modifier {
  val shape = RoundedCornerShape(radius)
  return sticker(fill = TrophyGold.base, radius = radius, depth = depth, shadow = TrophyGold.deep, outline = null)
    .background(Brush.verticalGradient(listOf(TrophyGold.light, TrophyGold.base, TrophyGold.shade)), shape)
    .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.8f), TrophyGold.deep.copy(alpha = 0.6f))), shape)
}
