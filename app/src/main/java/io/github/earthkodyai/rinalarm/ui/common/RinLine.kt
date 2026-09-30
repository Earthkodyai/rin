package io.github.earthkodyai.rinalarm.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Rin's subtitle, in its own band under her wherever she speaks (task 4.3, the user's pick), so it never covers her or
 * the game. Before it the line floated over her in large bold white (4.2), which the user found too big once she had
 * a voice. With [reserve] the band keeps two lines' height while she is quiet, so nothing below it moves when she
 * speaks (the ring screen); without it the band is only there while she has a line (the home strip). Larger font
 * scales may take more lines rather than cut her line short.
 */
@Composable
fun RinLine(text: String?, modifier: Modifier = Modifier, textModifier: Modifier = Modifier, reserve: Boolean = true) {
  if (text == null && !reserve) return
  val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
  val band = with(LocalDensity.current) { (style.lineHeight * LINES).toDp() } + 8.dp
  Box(modifier.fillMaxWidth().heightIn(min = band).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
    if (text != null) {
      Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = textModifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
  }
}

private const val LINES = 2
