package io.github.earthkodyai.rinalarm.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme

/** What the second button does: [DANGER] loses something (red), [WARNING] only skips a safeguard. */
enum class ConfirmKind {
  DANGER,
  WARNING,
}

/**
 * Every "are you sure?" in the app (the user's pick A, 2026-10-03, docs/ux/dialogs): the app's sticker card with a
 * round badge over its top edge, the question, and two stacked pills. The safe choice is the pink one on top, so a
 * hurried tap keeps things as they were; the other is the quiet pill below, red when it deletes or throws away.
 * Tapping outside or Back counts as the safe choice ([onSafe]).
 */
@Composable
fun RinConfirmDialog(
  title: String,
  icon: Int,
  safeLabel: String,
  actionLabel: String,
  onSafe: () -> Unit,
  onAction: () -> Unit,
  text: String? = null,
  kind: ConfirmKind = ConfirmKind.DANGER,
) {
  Dialog(onDismissRequest = onSafe, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    ConfirmCard(title, icon, safeLabel, actionLabel, onSafe, onAction, text, kind)
  }
}

@Composable
private fun ConfirmCard(
  title: String,
  icon: Int,
  safeLabel: String,
  actionLabel: String,
  onSafe: () -> Unit,
  onAction: () -> Unit,
  text: String?,
  kind: ConfirmKind,
) {
  val p = RinTheme.palette
  // The alert red is too dark on the night card; a lighter coral reads there.
  val red = if (p.night) Color(0xFFFF8A80) else p.alert
  val badgeFill =
    when (kind) {
      ConfirmKind.DANGER -> if (p.night) Color(0xFF3A2350) else Color(0xFFFDE3E1)
      ConfirmKind.WARNING -> p.tag
    }
  val badgeInk = if (kind == ConfirmKind.DANGER) red else p.onTag
  Box(Modifier.padding(horizontal = 22.dp).widthIn(max = 420.dp).fillMaxWidth().padding(top = BADGE / 2)) {
    Column(
      Modifier.fillMaxWidth().sticker(radius = 28.dp, depth = 5.dp).padding(start = 20.dp, end = 20.dp, top = BADGE / 2 + 14.dp, bottom = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
        color = p.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
      )
      if (text != null) {
        Text(
          text,
          style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
          color = p.muted,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 6.dp),
        )
      }
      Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton(safeLabel, onSafe, Modifier.fillMaxWidth())
        QuietPillButton(
          actionLabel,
          onAction,
          Modifier.fillMaxWidth(),
          textColor = if (kind == ConfirmKind.DANGER) red else p.ink,
        )
      }
    }
    Box(
      Modifier.align(Alignment.TopCenter)
        .offset(y = -BADGE / 2)
        .size(BADGE)
        .sticker(fill = badgeFill, radius = BADGE / 2, depth = 4.dp, outline = null)
        .border(4.dp, p.card, CircleShape)
        .background(badgeFill, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(painterResource(icon), contentDescription = null, tint = badgeInk, modifier = Modifier.size(30.dp))
    }
  }
}

/** The badge sits half over the card's top edge: the box is padded down by half of it, and the badge moved up. */
private val BADGE = 68.dp

@Preview(widthDp = 390, heightDp = 420)
@Composable
private fun ConfirmCardPreview() {
  RinAlarmTheme {
    Box(Modifier.background(RinTheme.palette.ground).padding(vertical = 40.dp)) {
      ConfirmCard("Delete this alarm?", R.drawable.ic_delete, "Cancel", "Delete", {}, {}, "The 07:00 alarm will be removed.", ConfirmKind.DANGER)
    }
  }
}
