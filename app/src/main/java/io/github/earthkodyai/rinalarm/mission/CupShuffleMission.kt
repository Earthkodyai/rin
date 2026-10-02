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

/** The cup shuffle as the ring screen sees it: the game to draw, "Let's play", and a pick. */
interface CupsMission : Mission {
  val game: StateFlow<CupsState>

  fun begin()

  fun pick(slot: Int)

  /** A tap while Rin scolds a miss (G.1): the next try starts now; ignored at any other time. */
  fun skipScold()
}

/**
 * The cup shuffle (D17, task 3.4) on the ring screen: runs [CupsGame] on a timer and reports [progress] as the
 * streak (a wrong pick takes it back to 0). Starting and each right pick count as [MissionProgress.activity], which
 * quiets the tone, like the colour pads. Main-thread only.
 */
class CupShuffleMission(
  private val rules: CupsRules,
  private val seed: Long,
  private val clock: ElapsedClock,
  private val context: CoroutineContext = Dispatchers.Main.immediate,
  /** Suspends until Rin's line is over; a new shuffle after a wrong pick waits for her scold (D23 amended in 4.3). */
  private val quiet: suspend () -> Unit = {},
) : CupsMission {
  override val type = MissionType.CUPS
  private val play = CupsGame(rules, Random(seed))
  private val gameState = MutableStateFlow(play.state)
  override val game: StateFlow<CupsState> = gameState.asStateFlow()
  private val state = MutableStateFlow(MissionProgress(0, rules.streak))
  override val progress: StateFlow<MissionProgress> = state.asStateFlow()

  private var scope: CoroutineScope? = null
  private var timer: Job? = null

  override fun start() {
    if (scope != null || state.value.state != MissionState.RUNNING) return
    scope = CoroutineScope(SupervisorJob() + context)
  }

  override fun stop() {
    scope?.cancel()
    scope = null
    timer = null
  }

  override fun begin() {
    if (scope == null) return
    val before = play.state.phase
    apply(play.start(clock.now()), activity = before == CupsPhase.READY)
  }

  override fun pick(slot: Int) {
    if (scope == null) return
    val after = play.pick(slot, clock.now())
    apply(after, activity = after.right == true && after.picks != gameState.value.picks)
  }

  override fun skipScold() {
    if (scope == null) return
    val after = play.skipScold(clock.now())
    if (after != gameState.value) apply(after, activity = false)
  }

  private fun apply(next: CupsState, activity: Boolean) {
    gameState.value = next
    state.value =
      MissionProgress(
        done = next.streak,
        target = next.target,
        state = if (next.phase == CupsPhase.PASSED) MissionState.PASSED else MissionState.RUNNING,
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
          // A right pick has no line (D23), so this only waits after a wrong one; the shuffle is timed from this tick.
          if (play.state.phase == CupsPhase.REVEAL) quiet()
          timer = null
          apply(play.tick(clock.now()), activity = false)
        }
      }
  }

  override fun summary(): String {
    val s = play.state
    return "game=cup_shuffle streak=${s.streak}/${s.target} picks=${s.picks} mistakes=${s.mistakes} " +
      "earlyTaps=${s.earlyTaps} seed=$seed" +
      play.trace().let { if (it.isEmpty()) "" else " picks_trace=$it" }
  }
}
