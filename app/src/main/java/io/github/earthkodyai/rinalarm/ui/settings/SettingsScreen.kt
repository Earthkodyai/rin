package io.github.earthkodyai.rinalarm.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.data.ThemeMode
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.PillChoiceRow
import io.github.earthkodyai.rinalarm.ui.common.RinPage
import io.github.earthkodyai.rinalarm.ui.common.rinCard
import io.github.earthkodyai.rinalarm.ui.common.sticker
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app's settings: the look, the games and the tour (UX.8). Phase 5's "No pouting" became each alarm's scold switch
 * in G.1; the 1323 hotline was dropped by the user on 2026-10-01 (no chat, D21).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: AppSettings) : ViewModel() {
  val themeMode: StateFlow<ThemeMode> =
    settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.AUTO)

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settings.setThemeMode(mode) }
  }

  /** "Show the tour again" (UX.8): the home screen runs it as soon as it shows; then [onDone]. */
  fun replayTour(onDone: () -> Unit) {
    viewModelScope.launch {
      settings.setTutorialPending(true)
      onDone()
    }
  }
}

@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onPrivacy: () -> Unit,
  onPractice: () -> Unit,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  SettingsScreen(
    themeMode = themeMode,
    onThemeMode = viewModel::setThemeMode,
    onPrivacy = onPrivacy,
    onBack = onBack,
    onTour = { viewModel.replayTour(onBack) },
    onPractice = onPractice,
  )
}

@Composable
internal fun SettingsScreen(
  themeMode: ThemeMode,
  onThemeMode: (ThemeMode) -> Unit,
  onPrivacy: () -> Unit,
  onBack: () -> Unit,
  onTour: () -> Unit = {},
  onPractice: () -> Unit = {},
) {
  RinPage(stringResource(R.string.settings_title), onBack) {
    // The app's own day or night look (UX phase): Auto follows the time of day, never the phone's dark mode.
    Column(Modifier.rinCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      CardText(stringResource(R.string.settings_look), stringResource(R.string.settings_look_summary))
      PillChoiceRow(
        options = ThemeMode.entries,
        selected = themeMode,
        label = { mode ->
          stringResource(
            when (mode) {
              ThemeMode.AUTO -> R.string.settings_look_auto
              ThemeMode.DAY -> R.string.settings_look_day
              ThemeMode.NIGHT -> R.string.settings_look_night
            }
          )
        },
        onSelect = onThemeMode,
      )
    }
    // UX.8: the games without an alarm, and the home screen's tour again.
    LinkCard(stringResource(R.string.practice_title), stringResource(R.string.settings_practice_summary), onPractice)
    LinkCard(stringResource(R.string.settings_tour), stringResource(R.string.settings_tour_summary), onTour)
    Column(Modifier.rinCard()) { CardText(stringResource(R.string.about_rin_title), stringResource(R.string.about_rin)) }
    LinkCard(stringResource(R.string.privacy_title), stringResource(R.string.privacy_summary), onPrivacy)
  }
}

/** A card that opens something: its name, what it means, and an arrow. */
@Composable
private fun LinkCard(title: String, text: String, onClick: () -> Unit) {
  val p = RinTheme.palette
  Row(
    Modifier.fillMaxWidth()
      .sticker(radius = 22.dp)
      .clip(RoundedCornerShape(22.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CardText(title, text, Modifier.weight(1f))
    // The back arrow turned around: "opens a page".
    Icon(
      painterResource(R.drawable.ic_arrow_back),
      contentDescription = null,
      tint = p.muted,
      modifier = Modifier.padding(start = 12.dp).size(20.dp).rotate(180f),
    )
  }
}

/** A card's name and what it means. */
@Composable
private fun CardText(title: String, text: String, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = p.ink)
    Text(text, style = MaterialTheme.typography.bodyMedium, color = p.muted)
  }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
  RinAlarmTheme {
    SettingsScreen(themeMode = ThemeMode.AUTO, onThemeMode = {}, onPrivacy = {}, onBack = {})
  }
}
