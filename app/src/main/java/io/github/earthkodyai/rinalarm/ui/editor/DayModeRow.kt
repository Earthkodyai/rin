package io.github.earthkodyai.rinalarm.ui.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.data.DayModeKind
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.theme.onSick
import io.github.earthkodyai.rinalarm.theme.sickFill
import io.github.earthkodyai.rinalarm.ui.common.sticker

/**
 * Rest day and sick day (Phase 5): one tap each, for the next ring; a tap on the one that is on cancels it. Buttons
 * only, nothing to type (the user's slips, Phase 2). On the editor since 2026-10-03 (the user: the tournament took
 * their place on home).
 */
@Composable
internal fun DayModeRow(dayMode: DayModeKind?, onTap: (DayModeKind) -> Unit, modifier: Modifier = Modifier) {
  Column(Modifier.fillMaxWidth()) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      DayModeKind.entries.forEach { kind -> DayModeButton(kind, dayMode == kind, { onTap(kind) }, Modifier.weight(1f)) }
    }
    if (dayMode != null) {
      Text(
        stringResource(if (dayMode == DayModeKind.REST) R.string.day_rest_on else R.string.day_sick_on),
        style = MaterialTheme.typography.bodySmall,
        color = RinTheme.palette.muted,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
      )
    }
  }
}

@Composable
private fun DayModeButton(kind: DayModeKind, selected: Boolean, onTap: () -> Unit, modifier: Modifier) {
  val p = RinTheme.palette
  val rest = kind == DayModeKind.REST
  val fill = if (!selected) p.card else if (rest) p.primary else p.sickFill
  val content = if (!selected) p.ink else if (rest) p.onPrimary else p.onSick
  val accent = if (rest) p.primary else p.sickFill
  val shape = RoundedCornerShape(24.dp)
  Row(
    modifier
      .height(48.dp)
      .sticker(fill = fill, radius = 24.dp, depth = 3.dp, outline = null)
      .border(2.dp, if (rest) p.candy else p.mint, shape)
      .clip(shape)
      .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onTap() }),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painterResource(if (rest) R.drawable.ic_moon else R.drawable.ic_thermometer),
      contentDescription = null,
      tint = if (selected) content else accent,
      modifier = Modifier.size(18.dp),
    )
    Text(
      stringResource(if (rest) R.string.day_rest else R.string.day_sick),
      style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
      color = content,
      modifier = Modifier.padding(start = 8.dp),
    )
  }
}
