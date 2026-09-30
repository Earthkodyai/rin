package io.github.earthkodyai.rinalarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.ui.diagnostics.DiagnosticsScreen
import io.github.earthkodyai.rinalarm.ui.editor.AlarmEditorScreen
import io.github.earthkodyai.rinalarm.ui.editor.AlarmEditorViewModel
import io.github.earthkodyai.rinalarm.ui.main.MainScreen
import io.github.earthkodyai.rinalarm.ui.onboarding.OnboardingScreen
import io.github.earthkodyai.rinalarm.ui.qrsetup.QrSetupScreen
import io.github.earthkodyai.rinalarm.ui.settings.SettingsScreen
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Whether the first screen is onboarding; null for the frame or two before DataStore answers. */
@HiltViewModel
class StartViewModel @Inject constructor(settings: AppSettings) : ViewModel() {
  val onboardingCompleted: StateFlow<Boolean?> =
    settings.onboardingCompleted.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun MainNavigation(start: StartViewModel = hiltViewModel()) {
  val completed by start.onboardingCompleted.collectAsStateWithLifecycle()
  // Blank until known, so the list never flashes before onboarding. The back stack then remembers where it is, so
  // finishing onboarding (which flips the flag) does not rebuild it.
  completed?.let { AppNavigation(if (it) Main else Onboarding) }
}

@Composable
private fun AppNavigation(first: NavKey) {
  val backStack = rememberNavBackStack(first)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // The ViewModel decorator gives each entry its own ViewModel, cleared when the entry is popped.
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider =
      entryProvider {
        entry<Onboarding> {
          OnboardingScreen(
            onDone = {
              // Replace, so Back from the alarm list leaves the app instead of reopening onboarding.
              if (backStack.lastOrNull() == Onboarding) {
                backStack.add(Main)
                backStack.remove(Onboarding)
              }
            }
          )
        }
        entry<Main> {
          MainScreen(
            onAdd = { backStack.add(AlarmEditor(AlarmEditorViewModel.NEW_ALARM_ID)) },
            onEdit = { backStack.add(AlarmEditor(it)) },
            onDiagnostics = { if (backStack.lastOrNull() == Main) backStack.add(Diagnostics) },
            onSettings = { if (backStack.lastOrNull() == Main) backStack.add(Settings) },
          )
        }
        entry<AlarmEditor> { key ->
          // Guarded: a finished editor can ask to close again while its exit animation runs.
          AlarmEditorScreen(
            key.alarmId,
            onClose = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() },
            onSetUpQr = { if (backStack.lastOrNull() == key) backStack.add(QrSetup) },
          )
        }
        entry<QrSetup> { QrSetupScreen(onClose = { if (backStack.lastOrNull() == QrSetup) backStack.removeLastOrNull() }) }
        entry<Settings> { SettingsScreen(onBack = { if (backStack.lastOrNull() == Settings) backStack.removeLastOrNull() }) }
        entry<Diagnostics> {
          DiagnosticsScreen(onBack = { if (backStack.lastOrNull() == Diagnostics) backStack.removeLastOrNull() })
        }
      },
  )
}
