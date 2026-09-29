package io.github.earthkodyai.rinalarm.mission

import io.github.earthkodyai.rinalarm.time.ElapsedClock
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A mission the ring screen plays as a game: it shows [game] and forwards the user's taps. */
interface PadsMission : Mission {
  val game: StateFlow<PadsState>

  /** "Let's play". */
  fun begin()

  fun tap(pad: Pad)
}

/**
 * Colour pads (D17) on the ring screen: runs [PadsGame] on a timer, plays each pad's note when it lights, and reports
 * [progress] as rounds passed. Starting the game and each right tap count as [MissionProgress.activity], which quiets
 * the tone (plan phase-3: right answers lower it; 30 s without one brings it back). Main-thread only.
 */
class ColourPadsMission(
  private val rules: PadsRules,
  private val seed: Long,
  private val notes: PadNotes,
  private val clock: ElapsedClock,
  private val context: CoroutineContext = Dispatchers.Main.immediate,
) : PadsMission {
  override val type = MissionType.PADS
  private val play = PadsGame(rules, Random(seed))
  private val gameState = MutableStateFlow(play.state)
  override val game: StateFlow<PadsState> = gameState.asStateFlow()
  private val state = MutableStateFlow(MissionProgress(0, rules.lengths.size))
  override val progress: StateFlow<MissionProgress> = state.asStateFlow()

  private var scope: CoroutineScope? = null
  private var timer: Job? = null
  private var rightTaps = 0

  override fun start() {
    if (scope != null || state.value.state != MissionState.RUNNING) return
    scope = CoroutineScope(SupervisorJob() + context)
  }

  override fun stop() {
    scope?.cancel()
    scope = null
    timer = null
    notes.release()
  }

  override fun begin() {
    if (scope == null) return
    apply(play.start(clock.now()), activity = true)
  }

  override fun tap(pad: Pad) {
    if (scope == null) return
    val before = play.state
    val after = play.tap(pad, clock.now())
    val right = after.phase != PadsPhase.SCOLD && after.flash != before.flash
    if (right) rightTaps++
    apply(after, activity = right)
  }

  private fun apply(next: PadsState, activity: Boolean) {
    val before = gameState.value
    gameState.value = next
    if (next.flash != before.flash) next.lit?.let(notes::play)
    val done = if (next.phase == PadsPhase.PASSED) next.rounds else next.round
    state.value =
      MissionProgress(
        done = done,
        target = next.rounds,
        state = if (next.phase == PadsPhase.PASSED) MissionState.PASSED else MissionState.RUNNING,
        activity = state.value.activity + if (activity) 1 else 0,
      )
    schedule(next.nextAt)
  }

  /** One pending tick at a time, at the game's next moment. */
  private fun schedule(at: Long?) {
    timer?.cancel()
    timer =
      at?.let {
        scope?.launch {
          delay((it - clock.now()).coerceAtLeast(0))
          timer = null
          apply(play.tick(clock.now()), activity = false)
        }
      }
  }

  override fun summary(): String {
    val s = play.state
    val rounds = if (s.phase == PadsPhase.PASSED) s.rounds else s.round
    return "game=colour_pads rounds=$rounds/${s.rounds} mistakes=${s.mistakes} timeouts=${s.timeouts} " +
      "rightTaps=$rightTaps seed=$seed"
  }
}
