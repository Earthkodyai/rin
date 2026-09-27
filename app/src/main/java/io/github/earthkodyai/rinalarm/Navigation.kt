package io.github.earthkodyai.rinalarm

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.earthkodyai.rinalarm.ui.editor.AlarmEditorScreen
import io.github.earthkodyai.rinalarm.ui.editor.AlarmEditorViewModel
import io.github.earthkodyai.rinalarm.ui.main.MainScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // The ViewModel decorator gives each editor entry its own ViewModel, cleared when the entry is popped.
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onAdd = { backStack.add(AlarmEditor(AlarmEditorViewModel.NEW_ALARM_ID)) },
            onEdit = { backStack.add(AlarmEditor(it)) },
          )
        }
        entry<AlarmEditor> { key ->
          // Guarded: a finished editor can ask to close again while its exit animation runs.
          AlarmEditorScreen(key.alarmId, onClose = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() })
        }
      },
  )
}
