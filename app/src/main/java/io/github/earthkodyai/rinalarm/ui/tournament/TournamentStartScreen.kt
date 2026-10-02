package io.github.earthkodyai.rinalarm.ui.tournament

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.data.TournamentEntry
import io.github.earthkodyai.rinalarm.data.TournamentStore
import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.mission.TournamentScore
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.tournament.TournamentActivity
import io.github.earthkodyai.rinalarm.tournament.formatTime
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.PillChoiceRow
import io.github.earthkodyai.rinalarm.ui.common.RinPage
import io.github.earthkodyai.rinalarm.ui.common.SectionTitle
import io.github.earthkodyai.rinalarm.ui.common.rinCard
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The universities to pick from, by id and short name. Empty until G.7 brings the list (50 Thai universities, campuses
 * as one); until then everyone is "Not listed".
 */
internal val TournamentUniversities: List<Pair<String, String>> = emptyList()

data class TournamentStartState(val entry: TournamentEntry = TournamentEntry(), val best: Map<TournamentGame, TournamentScore?> = emptyMap())

@HiltViewModel
class TournamentStartViewModel @Inject constructor(private val store: TournamentStore) : ViewModel() {
  val state: StateFlow<TournamentStartState?> =
    combine(store.entry, store.best(TournamentGame.PADS), store.best(TournamentGame.CUPS)) { entry, pads, cups ->
        TournamentStartState(entry, mapOf(TournamentGame.PADS to pads, TournamentGame.CUPS to cups))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** Keeps the name, university and agreement, then [then] (the run starts). */
  fun save(name: String, university: String?, then: () -> Unit) {
    viewModelScope.launch {
      store.setEntry(TournamentEntry(name, university, TournamentEntry.RULES_VERSION))
      store.entry.first()
      then()
    }
  }
}

/**
 * The tournament's start page (G.5): pick the game, a name if you like (the only thing typed in the whole app), your
 * university or "Not listed", accept the short rules once (asked again when they change), and play.
 */
@Composable
fun TournamentStartScreen(onBack: () -> Unit, viewModel: TournamentStartViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  state?.let { s ->
    TournamentStartScreen(
      s,
      onBack = onBack,
      onStart = { game, name, university ->
        viewModel.save(name, university) { context.startActivity(TournamentActivity.intent(context, game)) }
      },
    )
  }
}

@Composable
internal fun TournamentStartScreen(
  state: TournamentStartState,
  onBack: () -> Unit,
  onStart: (TournamentGame, String, String?) -> Unit,
) {
  val p = RinTheme.palette
  var game by rememberSaveable { mutableStateOf(TournamentGame.PADS) }
  var name by rememberSaveable { mutableStateOf(state.entry.name) }
  var university by rememberSaveable { mutableStateOf(state.entry.university) }
  var agreed by rememberSaveable { mutableStateOf(state.entry.agreedToCurrent) }
  RinPage(stringResource(R.string.tournament_title), onBack) {
    Text(stringResource(R.string.tournament_intro), style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp))

    SectionTitle(stringResource(R.string.tournament_pick_game))
    PillChoiceRow(
      TournamentGame.entries,
      game,
      { stringResource(if (it == TournamentGame.PADS) R.string.pads_title else R.string.cups_title) },
      { game = it },
    )
    val best = state.best[game]
    Text(
      if (best == null) stringResource(R.string.tournament_no_best)
      else stringResource(R.string.tournament_best_long, best.levels, formatTime(best.timeMs)),
      style = MaterialTheme.typography.bodyMedium,
      color = p.muted,
      modifier = Modifier.padding(horizontal = 4.dp),
    )

    SectionTitle(stringResource(R.string.tournament_you))
    OutlinedTextField(
      name,
      { name = it.take(TournamentEntry.NAME_MAX) },
      Modifier.fillMaxWidth(),
      label = { Text(stringResource(R.string.tournament_name)) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
      supportingText = { Text("${name.length}/${TournamentEntry.NAME_MAX}") },
    )
    UniversityPicker(university) { university = it }

    Column(Modifier.rinCard(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(stringResource(R.string.tournament_rules_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = p.ink)
      Text(stringResource(R.string.tournament_rules), style = MaterialTheme.typography.bodyMedium, color = p.muted)
      Row(
        Modifier.fillMaxWidth().toggleable(agreed, role = Role.Checkbox) { agreed = it },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(agreed, onCheckedChange = null)
        Text(stringResource(R.string.tournament_agree), style = MaterialTheme.typography.bodyLarge, color = p.ink, modifier = Modifier.padding(start = 8.dp))
      }
    }
    PillButton(stringResource(R.string.tournament_start), { onStart(game, name, university) }, Modifier.padding(top = 6.dp).fillMaxWidth(), enabled = agreed)
  }
}

/** The university, or "Not listed": a tap opens the list (only "Not listed" until G.7). */
@Composable
private fun UniversityPicker(selected: String?, onSelect: (String?) -> Unit) {
  val p = RinTheme.palette
  var open by remember { mutableStateOf(false) }
  val notListed = stringResource(R.string.tournament_not_listed)
  val label = TournamentUniversities.firstOrNull { it.first == selected }?.second ?: notListed
  Column(Modifier.fillMaxWidth()) {
    Text(stringResource(R.string.tournament_university), style = MaterialTheme.typography.labelLarge, color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    Text(
      label,
      style = MaterialTheme.typography.titleMedium,
      color = p.ink,
      modifier =
        Modifier.fillMaxWidth()
          .rinCard()
          .clickable(role = Role.DropdownList) { open = true },
    )
    DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
      DropdownMenuItem(text = { Text(notListed) }, onClick = {
        onSelect(null)
        open = false
      })
      TournamentUniversities.forEach { (id, short) ->
        DropdownMenuItem(text = { Text(short) }, onClick = {
          onSelect(id)
          open = false
        })
      }
    }
    if (TournamentUniversities.isEmpty()) {
      Text(stringResource(R.string.tournament_university_soon), style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }
  }
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun TournamentStartPreview() {
  RinAlarmTheme {
    TournamentStartScreen(
      TournamentStartState(best = mapOf(TournamentGame.PADS to TournamentScore(TournamentGame.PADS, 4, 83_400))),
      onBack = {},
      onStart = { _, _, _ -> },
    )
  }
}
