package io.github.earthkodyai.rinalarm.mission

import io.github.earthkodyai.rinalarm.character.Speaking
import io.github.earthkodyai.rinalarm.time.ElapsedClock
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * How much the ring tone must step back for the game (plan phase-3: quieter while Rin speaks so she is heard, silent
 * while the mic listens so the tone and the vibration stay out of it). RingService applies it on top of its own rules.
 */
enum class Hush {
  NONE,
  QUIET,
  SILENT,
}

/** Rin's voice for the game's sentences. [say] returns once she has finished, false when there is no clip to play. */
interface RinVoice {
  /** The line playing now, for her mouth on the character page. */
  val speaking: StateFlow<Speaking?>

  suspend fun say(sentence: Sentence): Boolean

  fun release()
}

/** Offline speech recognition limited to a grammar (S3: Vosk). */
interface SpeechListener {
  /** The mic level while listening, 0..1, for the screen's mic indicator. */
  val level: StateFlow<Float>

  /** Loads the model; throws when speech cannot work on this phone right now. */
  suspend fun prepare()

  /**
   * Listens once, up to [maxMs], and returns what was said; throws when the mic cannot be used. Ends early once what
   * was heard is [enough] (the sentence passed), or after a pause that follows speech.
   */
  suspend fun listen(grammar: List<String>, maxMs: Long, enough: (Heard) -> Boolean): Heard

  fun release()
}

/** The game the ring screen shows for [MissionType.SPEECH]. */
interface RepeatMission : Mission {
  val game: StateFlow<RepeatState>
  val hush: StateFlow<Hush>
  val speaking: StateFlow<Speaking?>
  val micLevel: StateFlow<Float>

  /** "Let's play". */
  fun begin()

  fun hearAgain()

  fun tapWord(chip: Int)
}

/**
 * Repeat after Rin (task 3.5): runs [RepeatGame], plays each sentence in Rin's voice, opens the mic for each try, and
 * reports [progress] as sentences done. The model loads as the ring screen opens, so it is ready by the time Rin
 * finishes her first line. If the mic or the model fails, the game carries on with the chips (never a stuck ring).
 * Starting the game, a try in which words were heard, and each right chip count as activity. Main-thread only.
 */
class RepeatAfterRinMission(
  private val rules: RepeatRules,
  pool: List<Sentence>,
  private val seed: Long,
  private val voice: RinVoice,
  private val listener: SpeechListener,
  private val clock: ElapsedClock,
  private val context: CoroutineContext = Dispatchers.Main.immediate,
) : RepeatMission {
  override val type = MissionType.SPEECH
  private val play = RepeatGame(rules, pool, Random(seed))
  private val gameState = MutableStateFlow(play.state)
  override val game: StateFlow<RepeatState> = gameState.asStateFlow()
  private val state = MutableStateFlow(MissionProgress(0, rules.sentences))
  override val progress: StateFlow<MissionProgress> = state.asStateFlow()
  private val hushState = MutableStateFlow(Hush.NONE)
  override val hush: StateFlow<Hush> = hushState.asStateFlow()
  override val speaking: StateFlow<Speaking?>
    get() = voice.speaking

  override val micLevel: StateFlow<Float>
    get() = listener.level

  private var scope: CoroutineScope? = null
  private var ready: Deferred<Unit>? = null
  /** What the current phase is doing (speaking, listening, the feedback pause); replaced on every phase change. */
  private var effect: Job? = null
  private var replay: Job? = null
  private var micFailure: String? = null
  private var listens = 0
  private val lags = mutableListOf<Long>()

  override fun start() {
    if (scope != null || state.value.state != MissionState.RUNNING) return
    val s = CoroutineScope(SupervisorJob() + context)
    scope = s
    ready = s.async { listener.prepare() }
  }

  override fun stop() {
    scope?.cancel()
    scope = null
    effect = null
    replay = null
    hushState.value = Hush.NONE
    voice.release()
    listener.release()
  }

  override fun begin() {
    if (scope == null) return
    apply(play.start(), activity = true)
  }

  override fun hearAgain() {
    if (scope == null) return
    val phase = play.state.phase
    if (phase != RepeatPhase.LISTENING && phase != RepeatPhase.TAPPING) return
    if (phase == RepeatPhase.TAPPING) {
      apply(play.hearAgain(), activity = false, restart = false)
      replay?.cancel()
      replay =
        scope?.launch {
          speak()
          hushState.value = Hush.NONE
        }
    } else {
      apply(play.hearAgain(), activity = false)
    }
  }

  override fun tapWord(chip: Int) {
    if (scope == null) return
    val before = play.state
    val after = play.tap(chip)
    apply(after, activity = after.placed > before.placed || after.phase != before.phase, restart = after.phase != before.phase)
  }

  /** Publishes [next]; a new phase replaces what the old one was doing. */
  private fun apply(next: RepeatState, activity: Boolean, restart: Boolean = true) {
    val before = gameState.value
    gameState.value = next
    val done = if (next.phase == RepeatPhase.PASSED) next.count else next.index
    state.value =
      MissionProgress(
        done = done,
        target = next.count,
        state = if (next.phase == RepeatPhase.PASSED) MissionState.PASSED else MissionState.RUNNING,
        activity = state.value.activity + if (activity) 1 else 0,
      )
    if (!restart && next.phase == before.phase) return
    effect?.cancel()
    if (next.phase != RepeatPhase.TAPPING) {
      replay?.cancel()
      replay = null
    }
    effect = scope?.launch { run(next) }
  }

  private suspend fun run(s: RepeatState) {
    when (s.phase) {
      RepeatPhase.READY,
      RepeatPhase.PASSED -> hushState.value = Hush.NONE
      RepeatPhase.SPEAKING -> {
        speak()
        apply(play.spoken(), activity = false)
      }
      RepeatPhase.LISTENING -> listen(s)
      RepeatPhase.FEEDBACK -> {
        hushState.value = Hush.QUIET
        delay(if (s.feedback == Feedback.RIGHT) rules.feedbackMs else rules.missMs)
        apply(play.advance(), activity = false)
      }
      RepeatPhase.TAPPING -> hushState.value = Hush.NONE
    }
  }

  /** Rin says the sentence; with no clip (release builds until Phase 4) the user gets time to read it instead. */
  private suspend fun speak() {
    val sentence = play.state.sentence ?: return
    hushState.value = Hush.QUIET
    if (!voice.say(sentence)) delay(rules.readingMs(sentence))
  }

  private suspend fun listen(s: RepeatState) {
    val sentence = s.sentence ?: return
    val heard =
      try {
        ready?.await()
        hushState.value = Hush.SILENT
        listens++
        listener.listen(RepeatMatcher.grammar(sentence, rules.match), rules.listenMs) {
          RepeatMatcher.match(sentence, it, rules.match).accepted
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        micFailure = micFailure ?: e.javaClass.simpleName
        hushState.value = Hush.NONE
        apply(play.micFailed(), activity = false)
        return
      }
    hushState.value = Hush.QUIET
    heard.lagMs?.let(lags::add)
    apply(play.heard(heard), activity = heard.words.isNotEmpty())
  }

  override fun summary(): String {
    val s = play.state
    val done = if (s.phase == RepeatPhase.PASSED) s.count else s.index
    val lag = lags.sorted().let { if (it.isEmpty()) "" else " lagMsP50=${it[it.size / 2]}" }
    return "game=repeat sentences=${play.sentences.joinToString(",") { it.id }} done=$done/${s.count} " +
      "voice=${s.voicePasses} tap=${s.tapPasses} listens=$listens wrongTaps=${s.wrongTaps} hearAgain=${s.hearAgains}" +
      lag + (micFailure?.let { " mic=failed:$it" } ?: "") + " seed=$seed" +
      play.trace().let { if (it.isEmpty()) "" else " tries=$it" }
  }
}
