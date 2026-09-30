package io.github.earthkodyai.rinalarm.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * One of Rin's lines floating over her, her subtitle wherever she speaks (task 4.2; the games' scolds before it): bold
 * white text with a soft dark shadow, no box, so it reads over her hair, the table and the screen's colour alike. The
 * box it had covered too much of her (the tester, dev-1 of task 3.4); the user kept this look for every line.
 */
@Composable
fun RinLine(text: String, modifier: Modifier = Modifier, textModifier: Modifier = Modifier) {
  Text(
    text,
    style =
      MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        color = Color.White,
        shadow = Shadow(Color.Black.copy(alpha = 0.85f), offset = Offset(0f, 2f), blurRadius = 10f),
      ),
    textAlign = TextAlign.Center,
    modifier =
      modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().then(textModifier).semantics {
        liveRegion = LiveRegionMode.Polite
      },
  )
}
