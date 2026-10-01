package io.github.earthkodyai.rinalarm.alarm.ring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.theme.pattern
import io.github.earthkodyai.rinalarm.ui.common.PillButton

/**
 * A game's start in the ring screen's sheet (UX.4, the mockup's "Let's play"): its name, one line on how it goes, and
 * the big pink button. [startTag] tags the button for tests.
 */
@Composable
internal fun GameIntro(title: Int, intro: Int, onStart: () -> Unit, startTag: String, enabled: Boolean = true) {
  val p = RinTheme.palette
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = p.ink)
    Text(stringResource(intro), style = MaterialTheme.typography.bodySmall, color = p.pattern.muted)
    PillButton(
      stringResource(R.string.pads_start),
      onStart,
      Modifier.padding(top = 4.dp).fillMaxWidth().height(52.dp).testTag(startTag),
      enabled = enabled,
    )
  }
}
