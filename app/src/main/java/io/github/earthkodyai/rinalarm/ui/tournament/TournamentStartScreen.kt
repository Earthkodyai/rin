package io.github.earthkodyai.rinalarm.ui.tournament

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import io.github.earthkodyai.rinalarm.theme.TrophyGold
import io.github.earthkodyai.rinalarm.tournament.TournamentActivity
import io.github.earthkodyai.rinalarm.tournament.formatTime
import io.github.earthkodyai.rinalarm.tournament.online.Leaderboard
import io.github.earthkodyai.rinalarm.tournament.online.LeaderboardSync
import io.github.earthkodyai.rinalarm.tournament.online.NameFilter
import io.github.earthkodyai.rinalarm.tournament.online.SyncStatus
import io.github.earthkodyai.rinalarm.ui.common.GoldButton
import io.github.earthkodyai.rinalarm.ui.common.PatternMotif
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.PillChoiceRow
import io.github.earthkodyai.rinalarm.ui.common.RinConfirmDialog
import io.github.earthkodyai.rinalarm.ui.common.RinPage
import io.github.earthkodyai.rinalarm.ui.common.goldSticker
import io.github.earthkodyai.rinalarm.ui.common.rinCard
import io.github.earthkodyai.rinalarm.ui.common.rinSwitchColors
import io.github.earthkodyai.rinalarm.ui.common.sticker
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @property configured whether this build has a leaderboard (G.6); without one the Online card says so and nothing else.
 * @property sync where the bests stand with the board, for the line under the switch.
 */
data class TournamentStartState(
  val entry: TournamentEntry = TournamentEntry(),
  val best: Map<TournamentGame, TournamentScore?> = emptyMap(),
  val configured: Boolean = false,
  val sync: SyncStatus = SyncStatus.OFF,
)

@HiltViewModel
class TournamentStartViewModel
@Inject
constructor(private val store: TournamentStore, private val sync: LeaderboardSync, board: Leaderboard) : ViewModel() {
  private val configured = board.configured

  val state: StateFlow<TournamentStartState?> =
    combine(store.entry, store.best(TournamentGame.PADS), store.best(TournamentGame.CUPS), sync.status) { entry, pads, cups, status ->
        TournamentStartState(entry, mapOf(TournamentGame.PADS to pads, TournamentGame.CUPS to cups), configured, status)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** On showing the page (and coming back from a run): post a new best, or retry what failed offline. */
  fun sync() = sync.sync()

  /**
   * The Online switch, kept at once (it is the user's consent, G.6): on posts the bests; off deletes the rows online,
   * now or at the next sync if the phone is offline.
   */
  fun setOnline(on: Boolean) {
    viewModelScope.launch {
      if (on) store.setEntry(store.entry.first().copy(online = true)) else store.stopPosting()
      sync.sync()
    }
  }

  /**
   * The name or university changed on the page (after a pause in typing): kept, and posted when posting is on, so the
   * board shows it without playing first (the user, 2026-10-03). The agreement is left as it was.
   */
  fun saveDetails(name: String, university: String?) {
    viewModelScope.launch {
      store.setEntry(store.entry.first().copy(name = name, university = university))
      sync.sync()
    }
  }

  /** Keeps the name, university and agreement, then [then] (the run starts). A changed name goes to the board too. */
  fun save(name: String, university: String?, then: () -> Unit) {
    viewModelScope.launch {
      val online = store.entry.first().online
      store.setEntry(TournamentEntry(name, university, TournamentEntry.RULES_VERSION, online))
      store.entry.first()
      sync.sync()
      then()
    }
  }
}

/**
 * The tournament's start page (G.5): pick the game, a name if you like (the only thing typed in the whole app), your
 * university or "Not listed", accept the short rules once (asked again when they change), and play.
 */
@Composable
fun TournamentStartScreen(onBack: () -> Unit, onLeaderboard: () -> Unit, viewModel: TournamentStartViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  LifecycleResumeEffect(viewModel) {
    viewModel.sync()
    onPauseOrDispose {}
  }
  state?.let { s ->
    TournamentStartScreen(
      s,
      onBack = onBack,
      onStart = { game, name, university ->
        viewModel.save(name, university) { context.startActivity(TournamentActivity.intent(context, game)) }
      },
      onOnline = viewModel::setOnline,
      onLeaderboard = onLeaderboard,
      onDetails = viewModel::saveDetails,
    )
  }
}

@Composable
internal fun TournamentStartScreen(
  state: TournamentStartState,
  onBack: () -> Unit,
  onStart: (TournamentGame, String, String?) -> Unit,
  onOnline: (Boolean) -> Unit,
  onLeaderboard: () -> Unit,
  onDetails: (String, String?) -> Unit = { _, _ -> },
) {
  val p = RinTheme.palette
  var game by rememberSaveable { mutableStateOf(TournamentGame.PADS) }
  var name by rememberSaveable { mutableStateOf(state.entry.name) }
  var university by rememberSaveable { mutableStateOf(state.entry.university) }
  var agreed by rememberSaveable { mutableStateOf(state.entry.agreedToCurrent) }
  val nameAllowed = NameFilter.allowed(name.trim())
  // A changed name or university waits for Save (the user, 2026-10-03), or goes with Start.
  val unsaved = name.trim() != state.entry.name || university != state.entry.university
  var saved by rememberSaveable { mutableStateOf(false) }
  // The games' drifting wallpaper with trophies for clocks, and nothing loose on it (the user's pick B, 2026-10-03):
  // a gold banner, then cards. Text straight on the wallpaper blended into it, and faint plates behind it floated.
  RinPage(stringResource(R.string.tournament_title), onBack, pattern = PatternMotif.TROPHY) {
    Banner(state.best[game])

    Column(Modifier.rinCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      CardTitle(stringResource(R.string.tournament_pick_game))
      PillChoiceRow(
        TournamentGame.entries,
        game,
        { stringResource(if (it == TournamentGame.PADS) R.string.pads_title else R.string.cups_title) },
        { game = it },
      )
    }

    Column(Modifier.rinCard(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      CardTitle(stringResource(R.string.tournament_you))
      FieldLabel(stringResource(R.string.tournament_name))
      Row(verticalAlignment = Alignment.Top) {
        OutlinedTextField(
          name,
          {
            name = NameFilter.typed(it)
            saved = false
          },
          Modifier.weight(1f),
          singleLine = true,
          isError = !nameAllowed,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
          supportingText = {
            val count = "${name.length}/${TournamentEntry.NAME_MAX}"
            when {
              !nameAllowed -> Text(stringResource(R.string.tournament_name_not_allowed), color = p.alert)
              unsaved -> Text("$count · ${stringResource(R.string.tournament_not_saved)}", color = p.muted)
              saved -> Text("$count · ${stringResource(R.string.tournament_saved)}", color = p.mintText)
              else -> Text(count, color = p.muted)
            }
          },
          shape = RoundedCornerShape(16.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = p.card,
              unfocusedContainerColor = p.card,
              focusedBorderColor = p.primary,
              unfocusedBorderColor = p.line,
            ),
        )
        if (unsaved) {
          SaveButton(enabled = nameAllowed, modifier = Modifier.padding(start = 8.dp, top = 6.dp)) {
            onDetails(name.trim(), university)
            saved = true
          }
        }
      }
      UniversityPicker(university) {
        university = it
        saved = false
      }
    }

    OnlineCard(state, onOnline, onLeaderboard)

    Column(Modifier.rinCard(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      CardTitle(stringResource(R.string.tournament_rules_title))
      Text(stringResource(R.string.tournament_rules), style = MaterialTheme.typography.bodyMedium, color = p.muted)
      Row(
        Modifier.fillMaxWidth().toggleable(agreed, role = Role.Checkbox) { agreed = it },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(agreed, onCheckedChange = null)
        Text(stringResource(R.string.tournament_agree), style = MaterialTheme.typography.bodyLarge, color = p.ink, modifier = Modifier.padding(start = 8.dp))
      }
    }
    PillButton(stringResource(R.string.tournament_start), { onStart(game, name, university) }, Modifier.padding(top = 6.dp).fillMaxWidth(), enabled = agreed && nameAllowed)
  }
}

/**
 * The gold banner on top, the home button's gold: the trophy, the challenge in two lines, and the best for the game
 * picked below (or none yet) on a white chip. The same by day and night.
 */
@Composable
private fun Banner(best: TournamentScore?) {
  Row(
    Modifier.fillMaxWidth().padding(bottom = 4.dp).goldSticker(radius = 24.dp, depth = 5.dp).padding(horizontal = 14.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Image(painterResource(R.drawable.ic_trophy_badge), contentDescription = null, modifier = Modifier.size(76.dp))
    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        stringResource(R.string.tournament_headline),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp),
        color = TrophyGold.ink,
        modifier = Modifier.semantics { heading() },
      )
      Text(stringResource(R.string.tournament_intro), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TrophyGold.ink)
      Text(
        if (best == null) stringResource(R.string.tournament_no_best)
        else "★ " + stringResource(R.string.tournament_best_short, best.levels, formatTime(best.timeMs)),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = TrophyGold.ink,
        modifier = Modifier.padding(top = 4.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
      )
    }
  }
}

/**
 * The online leaderboard (G.6): the consent switch, what it sends and where, how the bests stand with the board, and
 * the way to the board. Switching off asks first, because it deletes the rows online.
 */
@Composable
private fun OnlineCard(state: TournamentStartState, onOnline: (Boolean) -> Unit, onLeaderboard: () -> Unit) {
  val p = RinTheme.palette
  var confirmOff by remember { mutableStateOf(false) }
  // The user's pick A (2026-10-03, docs/ux/g6-online): the theme's card in a gold frame with a gold header strip and
  // the home button's gold pill, so it stands out from the plain cards without matching the banner's full gold.
  val shape = RoundedCornerShape(22.dp)
  Column(
    Modifier.fillMaxWidth()
      .sticker(fill = p.card, radius = 22.dp, shadow = TrophyGold.deep, outline = null)
      .border(3.dp, TrophyGold.base, shape)
      .clip(shape)
  ) {
    Row(
      Modifier.fillMaxWidth()
        .background(Brush.verticalGradient(listOf(TrophyGold.light, TrophyGold.base)))
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Image(painterResource(R.drawable.ic_trophy_badge), contentDescription = null, modifier = Modifier.size(24.dp))
      Text(
        stringResource(R.string.tournament_online_title).uppercase(),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
        color = TrophyGold.ink,
        modifier = Modifier.padding(start = 8.dp).semantics { heading() },
      )
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OnlineBody(state, onOnline, onLeaderboard) { confirmOff = true }
    }
  }
  if (confirmOff) {
    RinConfirmDialog(
      title = stringResource(R.string.tournament_stop_title),
      icon = R.drawable.ic_cloud_off,
      safeLabel = stringResource(R.string.tournament_keep_posting),
      actionLabel = stringResource(R.string.tournament_stop),
      onSafe = { confirmOff = false },
      onAction = {
        confirmOff = false
        onOnline(false)
      },
      text = stringResource(R.string.tournament_stop_body),
    )
  }
}

/** The Online card's inside: the switch, what it sends, the posting line, and the gold way to the board. */
@Composable
private fun OnlineBody(state: TournamentStartState, onOnline: (Boolean) -> Unit, onLeaderboard: () -> Unit, askOff: () -> Unit) {
  val p = RinTheme.palette
  if (!state.configured) {
    Text(stringResource(R.string.tournament_online_not_set_up), style = MaterialTheme.typography.bodyMedium, color = p.muted)
    return
  }
  val online = state.entry.online
  Row(
    Modifier.fillMaxWidth().toggleable(online, role = Role.Switch) { if (it) onOnline(true) else askOff() },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(stringResource(R.string.tournament_online_switch), style = MaterialTheme.typography.bodyLarge, color = p.ink, modifier = Modifier.weight(1f))
    Switch(checked = online, onCheckedChange = null, colors = rinSwitchColors(), modifier = Modifier.padding(start = 10.dp))
  }
  Text(stringResource(R.string.tournament_online_body), style = MaterialTheme.typography.bodySmall, color = p.muted)
  if (online && state.best.values.any { it != null }) {
    val line =
      when (state.sync) {
        SyncStatus.POSTED -> R.string.tournament_online_posted
        SyncStatus.WAITING -> R.string.tournament_online_waiting
        SyncStatus.OFF -> null
      }
    line?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge, color = p.ink) }
  }
  GoldButton(stringResource(R.string.tournament_see_board), onLeaderboard, Modifier.padding(top = 4.dp), height = 50.dp)
}

/** Save beside the name box, in the main pink, while the name or university differs from what is kept. */
@Composable
private fun SaveButton(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val p = RinTheme.palette
  Box(
    modifier
      .height(50.dp)
      .alpha(if (enabled) 1f else 0.5f)
      .sticker(fill = p.primary, radius = 25.dp, shadow = p.primaryShadow, outline = null)
      .clip(RoundedCornerShape(25.dp))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(stringResource(R.string.tournament_save), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp), color = p.onPrimary)
  }
}

/** A card's title, inside it (the page's own section titles sat loose on the wallpaper). */
@Composable
private fun CardTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
    color = RinTheme.palette.ink,
    modifier = Modifier.semantics { heading() },
  )
}

/** A field's label above its box, as on the mockup (a floating label cut a gap in the box's border). */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.labelLarge, color = RinTheme.palette.muted, modifier = modifier.padding(start = 2.dp))
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun TournamentStartPreview() {
  RinAlarmTheme {
    TournamentStartScreen(
      TournamentStartState(
        TournamentEntry(university = "ku", online = true),
        best = mapOf(TournamentGame.PADS to TournamentScore(TournamentGame.PADS, 4, 83_400)),
        configured = true,
        sync = SyncStatus.POSTED,
      ),
      onBack = {},
      onStart = { _, _, _ -> },
      onOnline = {},
      onLeaderboard = {},
    )
  }
}
