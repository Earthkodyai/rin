package io.github.earthkodyai.rinalarm.tournament

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.character.CupsView
import io.github.earthkodyai.rinalarm.data.TournamentStore
import io.github.earthkodyai.rinalarm.mission.CupsMission
import io.github.earthkodyai.rinalarm.mission.CupsPhase
import io.github.earthkodyai.rinalarm.mission.CupsState
import io.github.earthkodyai.rinalarm.mission.Mission
import io.github.earthkodyai.rinalarm.mission.MissionFactory
import io.github.earthkodyai.rinalarm.mission.MissionState
import io.github.earthkodyai.rinalarm.mission.Pad
import io.github.earthkodyai.rinalarm.mission.PadsMission
import io.github.earthkodyai.rinalarm.mission.PadsPhase
import io.github.earthkodyai.rinalarm.mission.PadsState
import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.mission.TournamentRun
import io.github.earthkodyai.rinalarm.mission.TournamentScore
import io.github.earthkodyai.rinalarm.time.ElapsedClock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class TournamentPhase {
  /** Cups only: her table coming into view before the count. */
  TABLE,
  /** 3, 2, 1, GO. */
  COUNTDOWN,
  PLAYING,
  /** A level passed: LEVEL n for a moment, then the next one starts by itself. */
  BANNER,
  /** The miss that ends the run: the answer is shown. */
  REVEAL,
  RESULTS,
}

/**
 * @property count during [TournamentPhase.COUNTDOWN]: 3, 2, 1, then 0 for GO.
 * @property startedAt GO on the elapsed clock (the running timer); [endedAt] the miss, where the timer stops.
 * @property score the run's result, from [TournamentPhase.RESULTS] on; [newBest] whether it beat [best].
 */
data class TournamentUiState(
  val game: TournamentGame,
  val phase: TournamentPhase = TournamentPhase.TABLE,
  val count: Int = 3,
  val level: Int = 1,
  val pads: PadsState? = null,
  val cups: CupsState? = null,
  val cupsView: CupsView = CupsView.Pending,
  val cups2d: Boolean = false,
  val startedAt: Long? = null,
  val endedAt: Long? = null,
  val score: TournamentScore? = null,
  val best: TournamentScore? = null,
  val newBest: Boolean = false,
  val finished: Boolean = false,
) {
  /** Whether a run is under way, so leaving needs a word first. */
  val running: Boolean
    get() = phase == TournamentPhase.COUNTDOWN || phase == TournamentPhase.PLAYING || phase == TournamentPhase.BANNER
}

/**
 * The tournament (G.5, plan phase-games): a count of 3, then one level after another of one game, each a single round
 * at [io.github.earthkodyai.rinalarm.mission.TournamentLadder]'s rules, until the first miss; then the answer, then the
 * result, kept as the best when it is one. Rin says nothing in it (the user). Nothing here is an alarm: no tone, no
 * ring log, and leaving the screen ends the run uncounted.
 */
@HiltViewModel
class TournamentViewModel
@Inject
constructor(
  private val missions: MissionFactory,
  private val store: TournamentStore,
  private val clock: ElapsedClock,
  savedState: SavedStateHandle,
) : ViewModel() {
  private val game: TournamentGame = TournamentGame.fromStored(savedState.get<String>(EXTRA_GAME)) ?: TournamentGame.PADS

  private val state = MutableStateFlow(TournamentUiState(game))
  val uiState: StateFlow<TournamentUiState> = state.asStateFlow()

  private var run = TournamentRun(game)
  private var mission: Mission? = null
  private var missionJobs: List<Job> = emptyList()
  /** What happens next (the count, a banner, the reveal): one at a time. */
  private var next: Job? = null

  init {
    viewModelScope.launch { store.best(game).collect { best -> state.update { it.copy(best = best) } } }
    begin()
  }

  /** Her page about the cup table (cups only). */
  fun onCupsView(view: CupsView) {
    state.update { it.copy(cupsView = view, cups2d = it.cups2d || view == CupsView.Unavailable) }
  }

  fun tapPad(pad: Pad) {
    if (state.value.phase == TournamentPhase.PLAYING) (mission as? PadsMission)?.tap(pad)
  }

  fun pickCup(slot: Int) {
    if (state.value.phase == TournamentPhase.PLAYING) (mission as? CupsMission)?.pick(slot)
  }

  /** "Play again" on the result: a new run of the same game. */
  fun playAgain() {
    if (state.value.phase != TournamentPhase.RESULTS) return
    stopMission()
    run = TournamentRun(game)
    state.update {
      TournamentUiState(game, best = it.best, cupsView = it.cupsView, cups2d = it.cups2d)
    }
    begin()
  }

  /** "Done", or leaving: the screen closes, and a run still going is not counted. */
  fun done() {
    stopMission()
    next?.cancel()
    state.update { it.copy(finished = true) }
  }

  private fun begin() {
    next?.cancel()
    next =
      viewModelScope.launch {
        if (game == TournamentGame.CUPS && !state.value.cups2d) {
          // Her table first; a page that never shows it gives way to the 2D board.
          val shown = withTimeoutOrNull(TABLE_WAIT_MS) { state.first { it.cupsView is CupsView.Shown || it.cups2d } }
          if (shown == null) state.update { it.copy(cups2d = true) }
        }
        for (count in 3 downTo 0) {
          state.update { it.copy(phase = TournamentPhase.COUNTDOWN, count = count) }
          delay(if (count == 0) GO_MS else COUNT_MS)
        }
        val go = clock.now()
        run.start(go)
        state.update { it.copy(startedAt = go) }
        startLevel(1)
      }
  }

  private fun startLevel(level: Int) {
    stopMission()
    state.update { it.copy(phase = TournamentPhase.PLAYING, level = level) }
    val m = runCatching { missions.tournament(game, level).also(Mission::start) }.getOrElse { return done() }
    mission = m
    val jobs = mutableListOf<Job>()
    jobs += viewModelScope.launch { m.progress.collect { if (it.state == MissionState.PASSED) onPass() } }
    when (m) {
      is PadsMission -> {
        jobs += viewModelScope.launch { m.game.collect(::onPads) }
        m.begin()
      }
      is CupsMission -> {
        jobs += viewModelScope.launch { m.game.collect(::onCups) }
        m.begin()
      }
    }
    missionJobs = jobs
  }

  private fun onPads(pads: PadsState) {
    if (state.value.phase != TournamentPhase.PLAYING) return
    if (pads.phase == PadsPhase.SCOLD) {
      // Her hand goes to the pad that was due and presses it: the answer.
      val due = pads.sequence.getOrNull(pads.entered)
      onMiss(pads.copy(phase = PadsPhase.DEMO, hand = due, pressing = due != null, lit = due))
    } else {
      state.update { it.copy(pads = pads) }
    }
  }

  private fun onCups(cups: CupsState) {
    if (state.value.phase != TournamentPhase.PLAYING) return
    state.update { it.copy(cups = cups) }
    // The wrong pick's act already lifts the ball's cup: that is the answer.
    if (cups.phase == CupsPhase.REVEAL && cups.right == false) onMiss(null)
  }

  private fun onPass() {
    if (state.value.phase != TournamentPhase.PLAYING) return
    run.pass(clock.now())
    next =
      viewModelScope.launch {
        // The last pad's flash, or the cup up over the ball, before the banner.
        delay(PASS_HOLD_MS)
        state.update { it.copy(phase = TournamentPhase.BANNER, level = run.level) }
        delay(BANNER_MS)
        startLevel(run.level)
      }
  }

  private fun onMiss(reveal: PadsState?) {
    run.miss()
    // No next try: the game's timers stop here (the reveal stays as it is drawn).
    stopMission()
    state.update { it.copy(phase = TournamentPhase.REVEAL, endedAt = clock.now(), pads = reveal ?: it.pads) }
    next =
      viewModelScope.launch {
        delay(REVEAL_MS)
        val score = run.score()
        val newBest = score.levels > 0 && store.record(score)
        state.update { it.copy(phase = TournamentPhase.RESULTS, score = score, newBest = newBest) }
      }
  }

  private fun stopMission() {
    missionJobs.forEach(Job::cancel)
    missionJobs = emptyList()
    mission?.stop()
    mission = null
  }

  override fun onCleared() {
    stopMission()
  }

  companion object {
    /** The intent extra (and saved-state key) naming the game by [TournamentGame.stored]. */
    const val EXTRA_GAME = "tournament.game"

    const val TABLE_WAIT_MS = 4_000L
    const val COUNT_MS = 700L
    const val GO_MS = 450L
    const val PASS_HOLD_MS = 450L
    const val BANNER_MS = 1_000L
    const val REVEAL_MS = 1_800L
  }
}
