package io.github.earthkodyai.rinalarm.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Phase 5's settings (D25): Pout off. (The 1323 hotline was dropped by the user on 2026-10-01: no chat, D21.) */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: AppSettings) : ViewModel() {
  val poutOff: StateFlow<Boolean> = settings.poutOff.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  fun setPoutOff(off: Boolean) {
    viewModelScope.launch { settings.setPoutOff(off) }
  }

  val themeMode: StateFlow<ThemeMode> =
    settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.AUTO)

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settings.setThemeMode(mode) }
  }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onPrivacy: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val poutOff by viewModel.poutOff.collectAsStateWithLifecycle()
  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  SettingsScreen(
    poutOff = poutOff,
    onPoutOff = viewModel::setPoutOff,
    themeMode = themeMode,
    onThemeMode = viewModel::setThemeMode,
    onPrivacy = onPrivacy,
    onBack = onBack,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
  poutOff: Boolean,
  onPoutOff: (Boolean) -> Unit,
  themeMode: ThemeMode,
  onThemeMode: (ThemeMode) -> Unit,
  onPrivacy: () -> Unit,
  onBack: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.editor_back))
          }
        },
      )
    }
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      // The app's own day or night look (UX phase): Auto follows the time of day, never the phone's dark mode.
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_look)) },
        supportingContent = { Text(stringResource(R.string.settings_look_summary)) },
      )
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        val modes = ThemeMode.entries
        modes.forEachIndexed { i, mode ->
          SegmentedButton(
            selected = themeMode == mode,
            onClick = { onThemeMode(mode) },
            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
            modifier = Modifier.height(48.dp),
          ) {
            Text(
              stringResource(
                when (mode) {
                  ThemeMode.AUTO -> R.string.settings_look_auto
                  ThemeMode.DAY -> R.string.settings_look_day
                  ThemeMode.NIGHT -> R.string.settings_look_night
                }
              )
            )
          }
        }
      }
      HorizontalDivider()
      // The whole row toggles: a bigger target than the switch alone (the user's slips, Phase 2).
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_pout)) },
        supportingContent = { Text(stringResource(R.string.settings_pout_summary)) },
        trailingContent = { Switch(checked = poutOff, onCheckedChange = null) },
        modifier = Modifier.clickable { onPoutOff(!poutOff) },
      )
      HorizontalDivider()
      ListItem(
        headlineContent = { Text(stringResource(R.string.about_rin_title)) },
        supportingContent = { Text(stringResource(R.string.about_rin)) },
      )
      HorizontalDivider()
      ListItem(
        headlineContent = { Text(stringResource(R.string.privacy_title)) },
        supportingContent = { Text(stringResource(R.string.privacy_summary)) },
        modifier = Modifier.clickable(onClick = onPrivacy),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
  RinAlarmTheme {
    SettingsScreen(poutOff = true, onPoutOff = {}, themeMode = ThemeMode.AUTO, onThemeMode = {}, onPrivacy = {}, onBack = {})
  }
}
