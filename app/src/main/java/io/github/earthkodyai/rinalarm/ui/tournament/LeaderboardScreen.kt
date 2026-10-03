package io.github.earthkodyai.rinalarm.ui.tournament

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.data.TournamentStore
import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.theme.TrophyGold
import io.github.earthkodyai.rinalarm.tournament.formatTime
import io.github.earthkodyai.rinalarm.tournament.online.BoardPage
import io.github.earthkodyai.rinalarm.tournament.online.BoardRow
import io.github.earthkodyai.rinalarm.tournament.online.Leaderboard
import io.github.earthkodyai.rinalarm.ui.common.PatternMotif
import io.github.earthkodyai.rinalarm.ui.common.PillChoiceRow
import io.github.earthkodyai.rinalarm.ui.common.QuietPillButton
import io.github.earthkodyai.rinalarm.ui.common.RinPage
import io.github.earthkodyai.rinalarm.ui.common.RoundIconButton
import io.github.earthkodyai.rinalarm.ui.common.rinCard
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BoardState {
  data object Loading : BoardState

  /** No Firebase project in this build. */
  data object NotSetUp : BoardState

  /** Offline, timed out, or refused. */
  data object Failed : BoardState

  data class Ready(val page: BoardPage) : BoardState
}

data class LeaderboardUi(
  val game: TournamentGame = TournamentGame.PADS,
  val board: BoardState = BoardState.Loading,
  /** Rows this player reported here, as "game/id": shown as Reported, with no menu. */
  val reported: Set<String> = emptySet(),
  val posting: Boolean = false,
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(private val board: Leaderboard, store: TournamentStore) : ViewModel() {
  private val game = MutableStateFlow(TournamentGame.PADS)
  private val boardState = MutableStateFlow<BoardState>(BoardState.Loading)
  private val reported = MutableStateFlow(emptySet<String>())
  private val failures = Channel<Int>(Channel.CONFLATED)
  private var load: Job? = null

  val state: StateFlow<LeaderboardUi> =
    combine(game, boardState, reported, store.entry) { game, board, reported, entry ->
        LeaderboardUi(game, board, reported, entry.online)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaderboardUi())

  /** A string to show once (a report that did not go through). */
  val messages: Flow<Int> = failures.receiveAsFlow()

  init {
    refresh()
  }

  fun pick(next: TournamentGame) {
    if (game.value == next) return
    game.value = next
    refresh()
  }

  fun refresh() {
    load?.cancel()
    if (!board.configured) {
      boardState.value = BoardState.NotSetUp
      return
    }
    boardState.value = BoardState.Loading
    val picked = game.value
    load = viewModelScope.launch { boardState.value = reach { board.page(picked) }?.let(BoardState::Ready) ?: BoardState.Failed }
  }

  fun report(row: BoardRow) {
    val picked = game.value
    viewModelScope.launch {
      if (reach { board.report(picked, row.id) } != null) reported.update { it + key(picked, row.id) }
      else failures.trySend(R.string.leaderboard_report_failed)
    }
  }

  /** [call]'s result, or null when the board could not be reached. */
  private suspend fun <T> reach(call: suspend () -> T): T? =
    try {
      call()
    } catch (_: TimeoutCancellationException) {
      null
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }

  companion object {
    fun key(game: TournamentGame, id: String) = "${game.stored}/$id"
  }
}

/**
 * The online leaderboard (G.6): players one by one in each game, best first, each with their university's badge (the
 * user's pick: no table of universities). The top [io.github.earthkodyai.rinalarm.tournament.online.OnlineLimits.TOP],
 * then this player's own row if it sits lower. Every other row can be reported.
 */
@Composable
fun LeaderboardScreen(onBack: () -> Unit, viewModel: LeaderboardViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  LaunchedEffect(viewModel) { viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
  LeaderboardScreen(state, onBack, onPick = viewModel::pick, onRefresh = viewModel::refresh, onReport = viewModel::report)
}

@Composable
internal fun LeaderboardScreen(
  state: LeaderboardUi,
  onBack: () -> Unit,
  onPick: (TournamentGame) -> Unit,
  onRefresh: () -> Unit,
  onReport: (BoardRow) -> Unit,
) {
  val p = RinTheme.palette
  RinPage(
    stringResource(R.string.leaderboard_title),
    onBack,
    actions = { RoundIconButton(R.drawable.ic_refresh, stringResource(R.string.leaderboard_refresh), onRefresh) },
    pattern = PatternMotif.TROPHY,
  ) {
    Column(Modifier.rinCard()) {
      PillChoiceRow(
        TournamentGame.entries,
        state.game,
        { stringResource(if (it == TournamentGame.PADS) R.string.pads_title else R.string.cups_title) },
        onPick,
      )
    }
    when (val board = state.board) {
      BoardState.Loading ->
        Box(Modifier.rinCard().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = p.primary)
        }
      BoardState.NotSetUp -> Note(stringResource(R.string.tournament_online_not_set_up))
      BoardState.Failed ->
        Column(Modifier.rinCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(R.string.leaderboard_failed), style = MaterialTheme.typography.bodyLarge, color = p.ink)
          QuietPillButton(stringResource(R.string.leaderboard_retry), onRefresh, Modifier.fillMaxWidth())
        }
      is BoardState.Ready -> Board(board.page, state, onReport)
    }
  }
}

@Composable
private fun Board(page: BoardPage, state: LeaderboardUi, onReport: (BoardRow) -> Unit) {
  val p = RinTheme.palette
  Column(Modifier.rinCard(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    if (page.top.isEmpty()) {
      Text(stringResource(R.string.leaderboard_empty), style = MaterialTheme.typography.bodyLarge, color = p.ink)
    }
    page.top.forEachIndexed { i, row ->
      if (i > 0) HorizontalDivider(color = p.line.copy(alpha = 0.5f))
      Entry(i + 1, row, state, onReport)
    }
    Text(
      stringResource(R.string.leaderboard_note),
      style = MaterialTheme.typography.bodySmall,
      color = p.muted,
      modifier = Modifier.padding(top = 10.dp),
    )
  }
  val me = page.me
  val rank = page.myRank
  if (me != null && rank != null && rank > page.top.size) {
    Column(Modifier.rinCard()) { Entry(rank, me, state, onReport) }
  } else if (me == null && !state.posting) {
    Note(stringResource(R.string.leaderboard_not_posting))
  }
}

@Composable
private fun Note(text: String) {
  Text(text, style = MaterialTheme.typography.bodyLarge, color = RinTheme.palette.ink, modifier = Modifier.rinCard())
}

/** One row: rank, name and university, levels and time, and a menu to report the name (not on your own row). */
@Composable
private fun Entry(rank: Int, row: BoardRow, state: LeaderboardUi, onReport: (BoardRow) -> Unit) {
  val p = RinTheme.palette
  val page = state.board as? BoardState.Ready
  val mine = page?.page?.me?.id == row.id
  val reported = LeaderboardViewModel.key(state.game, row.id) in state.reported
  val name =
    when {
      reported -> stringResource(R.string.leaderboard_reported)
      row.name.isNotEmpty() -> row.name
      else -> stringResource(R.string.leaderboard_player, row.id.take(4).uppercase())
    }
  val shown = if (mine) "$name · ${stringResource(R.string.leaderboard_you)}" else name
  val university = row.university?.let(::universityShort) ?: stringResource(R.string.tournament_not_listed)
  val spoken = stringResource(R.string.leaderboard_row, rank, shown, university, row.levels, formatTime(row.timeMs))
  Row(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(if (mine) p.primary.copy(alpha = 0.16f) else Color.Transparent)
      .padding(horizontal = 6.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = spoken },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RankMark(rank)
      Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          shown,
          style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
          color = if (reported) p.muted else p.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        UniversityBadge(row.university)
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          stringResource(R.string.leaderboard_levels, row.levels),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = p.ink,
        )
        Text(formatTime(row.timeMs), style = MaterialTheme.typography.bodySmall, color = p.muted)
      }
    }
    if (!mine && !reported) ReportMenu(name) { onReport(row) } else Box(Modifier.size(40.dp))
  }
}

@Composable
private fun ReportMenu(name: String, onReport: () -> Unit) {
  var open by remember { mutableStateOf(false) }
  val description = stringResource(R.string.leaderboard_more, name)
  Box {
    Box(
      Modifier.size(40.dp)
        .clip(CircleShape)
        .clickable(role = Role.Button) { open = true }
        .semantics { contentDescription = description },
      contentAlignment = Alignment.Center,
    ) {
      Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null, tint = RinTheme.palette.muted, modifier = Modifier.size(20.dp))
    }
    DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
      DropdownMenuItem(text = { Text(stringResource(R.string.leaderboard_report)) }, onClick = {
        open = false
        onReport()
      })
    }
  }
}

/** 1-3 as gold, silver and bronze medals; the rest as "#n". */
@Composable
private fun RankMark(rank: Int) {
  val medal =
    when (rank) {
      1 -> TrophyGold.base
      2 -> Color(0xFFC9CED6)
      3 -> Color(0xFFD9925B)
      else -> null
    }
  Box(
    Modifier.size(34.dp).then(if (medal != null) Modifier.background(medal, CircleShape) else Modifier),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      if (medal != null) "$rank" else stringResource(R.string.leaderboard_rank, rank),
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
      color = if (medal != null) TrophyGold.ink else RinTheme.palette.muted,
      maxLines = 1,
    )
  }
}

/** A university's short name, or its id in capitals when the list (G.7) does not have it. */
internal fun universityShort(id: String): String = TournamentUniversities.firstOrNull { it.first == id }?.second ?: id.uppercase()

/**
 * The university on a coloured pill, its colour fixed by the id. G.7 brings the logos (outside the repo) and keeps this
 * as the fallback when a logo file is missing. Nothing for "Not listed".
 */
@Composable
internal fun UniversityBadge(id: String?, modifier: Modifier = Modifier) {
  if (id == null) return
  val hue = id.hashCode().mod(360).toFloat()
  Text(
    universityShort(id),
    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
    color = Color.White,
    maxLines = 1,
    modifier = modifier.background(Color.hsv(hue, 0.55f, 0.62f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
  )
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun LeaderboardPreview() {
  val rows =
    listOf(
      BoardRow("aaaa1", "Mint", "ku", 14, 212_300),
      BoardRow("bbbb2", "", "chula", 12, 190_100),
      BoardRow("cccc3", "Earth", null, 11, 171_000),
      BoardRow("dddd4", "Ploy", "mu", 9, 150_500),
    )
  RinAlarmTheme {
    LeaderboardScreen(
      LeaderboardUi(board = BoardState.Ready(BoardPage(rows, rows[2], 3)), posting = true),
      onBack = {},
      onPick = {},
      onRefresh = {},
      onReport = {},
    )
  }
}
