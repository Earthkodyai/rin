package io.github.earthkodyai.rinalarm.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.theme.RinTheme

/**
 * The pages behind home (UX.5: settings, privacy, diagnostics): the editor's look, a round back button and
 * the title over the ground, and the page's own content under it. [scroll] makes the content one scrolling column
 * with the usual side margins; a page with a long list passes false and lays it out itself.
 */
@Composable
fun RinPage(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
  scroll: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(modifier.fillMaxSize().background(RinTheme.palette.ground)) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
      RinTopBar(title, onBack, actions = actions)
      if (scroll) {
        Column(
          Modifier.weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp)
            .navigationBarsPadding(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          content = content,
        )
      } else {
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
      }
    }
  }
}

/** The back button and the page's title (the editor's top row), with room for an action at the end. */
@Composable
fun RinTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
  Row(
    modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.editor_back), onBack)
    Text(
      title,
      style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
      color = RinTheme.palette.ink,
      maxLines = 1,
      modifier = Modifier.weight(1f).padding(start = 12.dp).semantics { heading() },
    )
    actions()
  }
}

/** A section's name over its card. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
    color = RinTheme.palette.ink,
    modifier = modifier.padding(start = 4.dp, top = 10.dp).semantics { heading() },
  )
}

/** A white card on the ground holding one section (the editor's ringing card). */
@Composable
@ReadOnlyComposable
fun Modifier.rinCard(): Modifier = this.fillMaxWidth().sticker(radius = 22.dp).padding(16.dp)

/**
 * The quiet pill: a second choice beside the pink one (Snooze on the ring screen, "Use one I have"), outlined on the
 * card colour so it never competes with the main action.
 */
@Composable
fun QuietPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 54.dp, enabled: Boolean = true) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(height / 2)
  Box(
    modifier
      .height(height)
      .alpha(if (enabled) 1f else 0.6f)
      .clip(shape)
      .background(p.card)
      .border(2.dp, p.line, shape)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = p.ink, textAlign = TextAlign.Center)
  }
}

/** A small pink pill for an action inside a row (a check's Fix). */
@Composable
fun SmallPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  Box(
    modifier
      .height(40.dp)
      .sticker(fill = p.primary, radius = 20.dp, depth = 3.dp, shadow = p.primaryShadow, outline = null)
      .clip(RoundedCornerShape(20.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = p.onPrimary, maxLines = 1)
  }
}

/** A row of pills, one of them filled pink; equal widths so five fit a narrow phone. */
@Composable
fun <T> PillChoiceRow(
  options: List<T>,
  selected: T,
  label: @Composable (T) -> String,
  onSelect: (T) -> Unit,
  enabled: Boolean = true,
) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(22.dp)
  Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    options.forEach { option ->
      val on = option == selected
      Box(
        Modifier.weight(1f)
          .height(44.dp)
          .clip(shape)
          .background(if (on) p.primary else p.card)
          .border(2.dp, if (on) p.primary else p.line, shape)
          .selectable(selected = on, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(option) }),
        contentAlignment = Alignment.Center,
      ) {
        Text(label(option), style = MaterialTheme.typography.labelLarge, color = if (on) p.onPrimary else p.ink, maxLines = 1)
      }
    }
  }
}
