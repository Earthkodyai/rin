package io.github.earthkodyai.rinalarm.ui.tournament

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.tournament.Universities
import io.github.earthkodyai.rinalarm.tournament.University
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded logos by id, null when the build has none (`rin.unis` unset). Touched only on the main thread. */
private val logoCache = mutableMapOf<String, ImageBitmap?>()

/** A university's logo from the app's assets (G.7), decoded once off the main thread; null without the file. */
@Composable
private fun rememberLogo(id: String): ImageBitmap? {
  val assets = LocalContext.current.assets
  val logo by
    produceState(logoCache[id], id) {
      if (id !in logoCache) {
        logoCache[id] =
          withContext(Dispatchers.IO) {
            runCatching { assets.open(Universities.logoAsset(id)).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
          }
      }
      value = logoCache[id]
    }
  return logo
}

/** The badge's colour, fixed by the id. */
private fun universityColor(id: String) = Color.hsv(id.hashCode().mod(360).toFloat(), 0.55f, 0.62f)

/** A university's short name, or its id in capitals for one the list does not have (an older row on the board). */
internal fun universityShort(id: String): String = Universities[id]?.short ?: id.uppercase()

/** The logo on a white disc, or the short name's first letters on the university's colour when there is no file. */
@Composable
private fun UniversityMark(id: String, size: Dp) {
  val logo = rememberLogo(id)
  Box(
    Modifier.size(size).clip(CircleShape).background(if (logo != null) Color.White else universityColor(id)),
    contentAlignment = Alignment.Center,
  ) {
    if (logo != null) {
      Image(logo, contentDescription = null, Modifier.size(size * 0.86f))
    } else {
      Text(
        universityShort(id).take(if (size < 24.dp) 1 else 3),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
        color = Color.White,
        maxLines = 1,
      )
    }
  }
}

/**
 * The university on a leaderboard row: its logo when the build has the file, then the short name on a coloured pill.
 * Nothing for "Not listed".
 */
@Composable
internal fun UniversityBadge(id: String?, modifier: Modifier = Modifier) {
  if (id == null) return
  val logo = rememberLogo(id)
  Row(
    modifier.background(universityColor(id), RoundedCornerShape(50)).padding(start = if (logo != null) 2.dp else 8.dp, end = 8.dp, top = 1.dp, bottom = 1.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    if (logo != null) UniversityMark(id, 16.dp)
    Text(
      universityShort(id),
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = Color.White,
      maxLines = 1,
    )
  }
}

/** The university, or "Not listed": boxed like the name; a tap opens the list, and a pick is kept at once ([saved]). */
@Composable
internal fun UniversityPicker(selected: String?, saved: Boolean, onSelect: (String?) -> Unit) {
  val p = RinTheme.palette
  var open by remember { mutableStateOf(false) }
  val university = Universities[selected]
  val shape = RoundedCornerShape(16.dp)
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(stringResource(R.string.tournament_university), style = MaterialTheme.typography.labelLarge, color = p.muted, modifier = Modifier.padding(start = 2.dp))
    Row(
      Modifier.fillMaxWidth()
        .clip(shape)
        .background(p.card, shape)
        .border(1.dp, p.line, shape)
        .clickable(role = Role.DropdownList) { open = true }
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (university != null) UniversityMark(university.id, 32.dp)
      Text(
        university?.english ?: stringResource(R.string.tournament_not_listed),
        style = MaterialTheme.typography.bodyLarge,
        color = p.ink,
        modifier = Modifier.weight(1f).padding(vertical = if (university == null) 4.dp else 0.dp),
      )
      Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, tint = p.muted, modifier = Modifier.size(22.dp))
    }
    if (saved) {
      Text(stringResource(R.string.tournament_saved), style = MaterialTheme.typography.bodySmall, color = p.mintText, modifier = Modifier.padding(start = 16.dp))
    }
  }
  if (open) {
    UniversitySheet(selected, onDismiss = { open = false }) {
      onSelect(it)
      open = false
    }
  }
}

/**
 * The list (the user's pick, 2026-10-03): "Not listed" first, then A to Z by English name, each row with the logo,
 * the English name and the Thai one under it, and the source and "not affiliated" note at the foot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UniversitySheet(selected: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
  val p = RinTheme.palette
  ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.card) {
    Text(
      stringResource(R.string.tournament_university),
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
      color = p.ink,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    LazyColumn(Modifier.fillMaxWidth()) {
      item { SheetRow(null, selected == null) { onSelect(null) } }
      items(Universities.alphabetical, key = { it.id }) { university ->
        HorizontalDivider(Modifier.padding(start = 72.dp), color = p.line.copy(alpha = 0.5f))
        SheetRow(university, selected == university.id) { onSelect(university.id) }
      }
      item {
        Text(
          stringResource(R.string.tournament_universities_note),
          style = MaterialTheme.typography.bodySmall,
          color = p.muted,
          modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        )
      }
    }
  }
}

@Composable
private fun SheetRow(university: University?, chosen: Boolean, onClick: () -> Unit) {
  val p = RinTheme.palette
  Row(
    Modifier.fillMaxWidth()
      .background(if (chosen) p.primary.copy(alpha = 0.14f) else Color.Transparent)
      .clickable(role = Role.RadioButton, onClick = onClick)
      .semantics { this.selected = chosen }
      .padding(horizontal = 20.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (university != null) UniversityMark(university.id, 36.dp) else Box(Modifier.size(36.dp))
    Column(Modifier.weight(1f)) {
      Text(
        university?.english ?: stringResource(R.string.tournament_not_listed),
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = p.ink,
      )
      if (university != null) Text(university.thai, style = MaterialTheme.typography.bodySmall, color = p.muted)
    }
    if (chosen) Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = p.primary, modifier = Modifier.size(22.dp))
  }
}
