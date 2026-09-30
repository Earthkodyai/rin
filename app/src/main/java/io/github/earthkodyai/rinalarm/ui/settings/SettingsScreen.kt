package io.github.earthkodyai.rinalarm.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Phase 5's settings (D25): Pout off, and the 1323 hotline (05e: a fixed line in the app, D21). */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: AppSettings) : ViewModel() {
  val poutOff: StateFlow<Boolean> = settings.poutOff.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  fun setPoutOff(off: Boolean) {
    viewModelScope.launch { settings.setPoutOff(off) }
  }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val poutOff by viewModel.poutOff.collectAsStateWithLifecycle()
  val context = LocalContext.current
  SettingsScreen(
    poutOff = poutOff,
    onPoutOff = viewModel::setPoutOff,
    onHotline = {
      // ACTION_DIAL: the dialer opens with the number filled in, and the user presses call. No permission needed.
      runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$HOTLINE"))) }
        .onFailure { if (it !is ActivityNotFoundException) throw it }
    },
    onBack = onBack,
  )
}

/** Thailand's mental health hotline (Department of Mental Health): free, 24 hours. */
private const val HOTLINE = "1323"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(poutOff: Boolean, onPoutOff: (Boolean) -> Unit, onHotline: () -> Unit, onBack: () -> Unit) {
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
      // The whole row toggles: a bigger target than the switch alone (the user's slips, Phase 2).
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_pout)) },
        supportingContent = { Text(stringResource(R.string.settings_pout_summary)) },
        trailingContent = { Switch(checked = poutOff, onCheckedChange = null) },
        modifier = Modifier.clickable { onPoutOff(!poutOff) },
      )
      HorizontalDivider()
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_hotline_title)) },
        supportingContent = { Text(stringResource(R.string.settings_hotline)) },
        modifier = Modifier.clickable(onClick = onHotline),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
  RinAlarmTheme { SettingsScreen(poutOff = true, onPoutOff = {}, onHotline = {}, onBack = {}) }
}
