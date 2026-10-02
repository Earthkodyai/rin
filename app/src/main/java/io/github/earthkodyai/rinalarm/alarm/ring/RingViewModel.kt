package io.github.earthkodyai.rinalarm.alarm.ring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmWriter
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType
import io.github.earthkodyai.rinalarm.alarm.log.RingLog
import io.github.earthkodyai.rinalarm.character.CupsView
import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.character.Mood
import io.github.earthkodyai.rinalarm.character.Speaking
import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.di.AppScope
import io.github.earthkodyai.rinalarm.dialogue.Line
import io.github.earthkodyai.rinalarm.dialogue.LineBook
import io.github.earthkodyai.rinalarm.dialogue.LineVoiceFactory
import io.github.earthkodyai.rinalarm.dialogue.Pools
import io.github.earthkodyai.rinalarm.dialogue.PoutFilter
import io.github.earthkodyai.rinalarm.dialogue.pouty
import io.github.earthkodyai.rinalarm.dialogue.RinSpeaker
import io.github.earthkodyai.rinalarm.mission.CupsMission
import io.github.earthkodyai.rinalarm.mission.CupsPhase
import io.github.earthkodyai.rinalarm.mission.CupsState
import io.github.earthkodyai.rinalarm.mission.Difficulty
import io.github.earthkodyai.rinalarm.mission.Feedback
import io.github.earthkodyai.rinalarm.mission.Hush
import io.github.earthkodyai.rinalarm.mission.Mission
import io.github.earthkodyai.rinalarm.mission.MissionFactory
import io.github.earthkodyai.rinalarm.mission.MissionPlanner
import io.github.earthkodyai.rinalarm.mission.MissionProgress
import io.github.earthkodyai.rinalarm.mission.MissionReadiness
import io.github.earthkodyai.rinalarm.mission.MissionState
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.Miss
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionPlan
import io.github.earthkodyai.rinalarm.mission.Pad
import io.github.earthkodyai.rinalarm.mission.PadsMission
import io.github.earthkodyai.rinalarm.mission.PadsPhase
import io.github.earthkodyai.rinalarm.mission.PadsState
import io.github.earthkodyai.rinalarm.mission.Readiness
import io.github.earthkodyai.rinalarm.mission.RepeatMission
import io.github.earthkodyai.rinalarm.mission.RepeatPhase
import io.github.earthkodyai.rinalarm.mission.RepeatState
import io.github.earthkodyai.rinalarm.mission.hasLevels
import io.github.earthkodyai.rinalarm.time.ElapsedClock
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The ring screen's side of a ring (task 3.1): runs the mission RingService planned, reports each bit of progress to
 * [RingState] (the service quiets the tone), and asks the service to stop once the mission passes. It never stops
 * the ring by itself otherwise: the service owns the ring, and this screen may never show (08-error-handling).
 *
 * [commands] carries what the activity must send to RingService (it holds the Context).
 *
 * Rin speaks through the ring (task 4.2): a first line as it rings, an intro on "Let's play" (the game starts once she
 * is done), during the game only after a mistake (D23: a scold, or what to do after a missed try), then after a pass a
 * won line and one closing remark before [RingUiState.finished] closes the screen (a tap closes it sooner). A snooze or the emergency stop gets
 * a line too, and the screen stays up until she has said it. [RingUiState.line] is the subtitle; the tone steps back
 * only while one of her clips plays. With no script, she stays quiet and the screen closes [CELEBRATE_MS] after a pass.
 *
 * A practice round (UX.8, [EXTRA_PRACTICE] names the game) runs the same screen with no ring at all: no RingService,
 * no tone, nothing logged; the game's first round only ([MissionFactory.practice]), her voice on the media stream, and
 * after the win her won line, then "Play again" or "Done" ([RingUiState.practice]).
 *
 * G.1: every button works while she talks and cuts her line short ([cutLine]); a tap anywhere skips her intro or her
 * scold ([skip]). While she scolds, a switch turns her scolding off ([setScold]) for this alarm and new ones. With
 * scolding off a miss is quiet ([RingUiState.quietMiss], the user): no Rin, a red cross and a red glow at the screen's
 * edges for a moment, and the game goes on.
 */
@HiltViewModel
class RingViewModel
@Inject
constructor(
  private val ringState: RingState,
  private val missions: MissionFactory,
  private val ringLog: RingLog,
  @AppScope private val appScope: CoroutineScope,
  private val clock: ElapsedClock,
  private val readiness: MissionReadiness,
  private val time: TimeSource,
  book: LineBook,
  voices: LineVoiceFactory,
  private val settings: AppSettings,
  private val writer: AlarmWriter,
  savedState: SavedStateHandle,
) : ViewModel() {
  /** The game of a practice round, or null for a real ring. */
  private val practice: MissionType? = savedState.get<String>(EXTRA_PRACTICE)?.let(MissionType::fromStored)
  /** A practice round's level and scold switch, as the editor's "Try this game" sent them (else the new-alarm ones). */
  private val practiceLevel: Difficulty? = savedState.get<String>(EXTRA_LEVEL)?.let(Difficulty::fromStored)
  private val practiceScold: Boolean? = savedState.get<Boolean>(EXTRA_SCOLD)
  /** The game's level: the ring's, or the practice round's. */
  private var level = Difficulty.EASY
  /** A practice round's last progress, which a real ring keeps in [RingState]. */
  private var practiceProgressAt: Long? = null

  private val state = MutableStateFlow(RingUiState())
  val uiState: StateFlow<RingUiState> = state.asStateFlow()

  private val commandFlow = MutableSharedFlow<RingCommand>(extraBufferCapacity = 4)
  val commands: SharedFlow<RingCommand> = commandFlow.asSharedFlow()

  private val cueFlow = MutableSharedFlow<Gesture>(extraBufferCapacity = 2)
  /** Gestures for Rin when the phase changes (RingMoods.cue). */
  val cues: SharedFlow<Gesture> = cueFlow.asSharedFlow()

  private val characterVisible = CompletableDeferred<Unit>()
  /** Her opening line, while it waits for her face (cancelled by any other line). */
  private var opening: Job? = null
  private val speakingFlow = MutableStateFlow<Speaking?>(null)
  /** The clip Rin is saying (one of her lines, or a game's sentence in Repeat after Rin), for her mouth. */
  val speaking: StateFlow<Speaking?> = speakingFlow.asStateFlow()

  /** Rin scolds a miss (G.1): the alarm's switch, or the one flipped on this screen. */
  @Volatile private var scold = true
  private val lines = PoutFilter(book) { calm() }
  private val speaker = RinSpeaker(voices.create(alarm = practice == null), viewModelScope)
  private var gameSpeaking: Speaking? = null
  /** What the game asks of the tone (Repeat after Rin); the ring gets the stronger of it and her lines' hush. */
  private var gameHush = Hush.NONE
  private var ringDay: LocalDate? = null
  /** Keys the session pools: one morning of one alarm, across its snoozes. */
  private var morning: Any = Unit
  /** "Let's play" was tapped for the game on screen, and her intro for it is over. */
  private var started = false
  private var introDone = false
  private var introJob: Job? = null
  /** A miss, a slow round or a wrong try in this ring: the win is a hard one. */
  private var trouble = false
  /** Her line after a snooze or the emergency stop; the screen closes once it is over. */
  private var farewell: Job? = null
  /** Her intro line after "Let's play", while she says it: a tap cuts it and the game starts. */
  private var introLine: Job? = null
  /** A quiet miss's moment, after which the game goes on by itself. */
  private var quietMissJob: Job? = null

  private var mission: Mission? = null
  private var missionJob: Job? = null
  private var gameJob: Job? = null
  private var voiceJobs: List<Job> = emptyList()
  private var stallJob: Job? = null
  private var cupsWaitJob: Job? = null
  /** Why the cups went 2D, for the log (null: her page played them). */
  private var cups2dReason: String? = null
  private var ringKey: Any? = null
  private var missionStartedAt = 0L

  init {
    if (practice != null) {
      viewModelScope.launch {
        level = practiceLevel ?: settings.lastDifficulty.first()
        scold = practiceScold ?: settings.lastScold.first()
        startPractice(practice)
      }
    } else {
      viewModelScope.launch {
        ringState.active.collect { ring ->
          if (ring == null) onRingEnded() else onRing(ring)
        }
      }
    }
    viewModelScope.launch { speaker.line.collect { line -> state.update { it.copy(line = line) } } }
    viewModelScope.launch { speaker.hush.collect { pushHush() } }
    viewModelScope.launch { speaker.speaking.collect { publishSpeaking() } }
  }

  /** No pouting: scold off, or a rest or sick day's ring (it never scolds). */
  private fun calm(): Boolean = !scold || state.value.ring?.dayMode != null

  /** A gesture for Rin, unless it is a pouty one and she is [calm], or it moves her arms while a game is on. */
  private fun cue(gesture: Gesture) {
    if (gesture.pouty && calm()) return
    if (state.value.inGame && !gesture.onRing().armsStill) return
    cueFlow.tryEmit(gesture.onRing())
  }

  private fun pushHush() {
    if (practice == null) ringState.setHush(maxOf(gameHush, speaker.hush.value))
  }

  private fun publishSpeaking() {
    speakingFlow.value = speaker.speaking.value ?: gameSpeaking
  }

  /**
   * Picks a line from [pool] and says it; the job ends when she is done (at once when there is no line). [cut] stops
   * her line in progress, otherwise this one waits for it. [gesture]: she plays the line's gesture as she starts.
   */
  private fun say(pool: String, cut: Boolean = true, gesture: Boolean = true): Job {
    // Any other line means the moment for her opening one has passed (the user tapped Let's play before her face came).
    opening?.cancel()
    return viewModelScope.launch {
      val line = lines.pick(pool, ringDay ?: today(), morning) ?: return@launch
      if (!cut) speaker.finish()
      if (gesture) line.gesture?.let(::cue)
      speaker.say(line).join()
    }
  }

  /** Her face is on screen (or never will be: the still image). Called by the ring screen's CharacterView. */
  fun onCharacterVisible() {
    characterVisible.complete(Unit)
  }

  private fun today(): LocalDate = time.now().atZone(time.zone()).toLocalDate()

  private fun onRing(ring: ActiveRing) {
    val key = ring.request.let { Triple(it.alarmId, it.scheduledAt, it.snoozeCount) }
    if (key == ringKey) {
      state.update { it.copy(ring = ring) }
      return
    }
    ringKey = key
    stopMission()
    cups2dReason = null
    trouble = false
    // Each ring reads the alarm afresh: a switch flipped before a snooze reaches the next ring through the database.
    scold = ring.request.scold
    level = ring.request.difficulty
    state.value = RingUiState(ring = ring, scold = scold)
    state.update { it.copy(calm = calm()) }
    refreshPhase()
    val now = time.now().atZone(time.zone())
    ringDay = now.toLocalDate()
    morning = ring.request.alarmId to ringDay
    // Her first line waits for her face (4.3): a line that starts while her page still loads plays to the still image,
    // and the user saw her mouth miss it. A page that never loads costs at most OPENING_WAIT_MS.
    val pool =
      ring.dayMode?.let(Pools::dayMode)
        ?: ring.request.let { Pools.opening(it.snoozeCount, it.snoozesLeft, it.late, now.dayOfWeek, it.time) }
    val waiting =
      viewModelScope.launch {
        // Her face was already up (a ring after a snooze on the same screen): no wait at all.
        val loading = !characterVisible.isCompleted
        val shown = withTimeoutOrNull(OPENING_WAIT_MS) { characterVisible.await() } != null
        if (shown && loading) delay(OPENING_SETTLE_MS)
        opening = null
        // Her face just came up with its greeting by mood (a wave, or a yawn when sleepy): the line's own gesture
        // 0.3 s later took over the greeting mid-way and her hand jerked (4.3), so one gesture is enough. The user
        // chose a greeting every time over the line's gesture, which 16 of 39 opening lines lack.
        say(pool, gesture = !(shown && loading))
      }
    opening = waiting
    val plan = ring.mission ?: return
    run(ring, plan.type, plan.switchedFrom?.let { " switchedFrom=${it.stored}" } ?: "")
  }

  /**
   * A practice round of [type]: a stand-in ring at the current time, with no snoozes, that nothing outside this screen
   * knows of. Her opening line is left out: the round starts at "Let's play", as the user came to play.
   */
  private fun startPractice(type: MissionType) {
    val now = time.now().atZone(time.zone())
    ringDay = now.toLocalDate()
    morning = PRACTICE_MORNING
    val request =
      RingRequest(PRACTICE_ALARM_ID, now.toLocalTime(), "", null, 0, 0, late = false, RingOptions(), MissionChoice.Only(type), difficulty = level, scold = scold)
    val ring = ActiveRing(request, MissionPlan.Run(type))
    practiceProgressAt = null
    // The cup table her page shows now (the round before): kept, or "Let's play" would wait for it to come back.
    state.value = RingUiState(ring = ring, practice = true, cupsView = state.value.cupsView, scold = scold)
    state.update { it.copy(calm = calm()) }
    refreshPhase()
    run(ring, type, "")
  }

  /** "Play again" after a practice round's win: a new round of the same game. */
  fun playAgain() {
    val type = practice ?: return
    if (!state.value.passed) return
    stopMission()
    speaker.stop()
    trouble = false
    cups2dReason = null
    startPractice(type)
  }

  private fun run(ring: ActiveRing, type: MissionType, logExtra: String) {
    val running =
      runCatching { (if (practice != null) missions.practice(type, level) else missions.create(type, level)).also(Mission::start) }.getOrNull()
    if (running == null) {
      state.update { it.copy(missionFailed = true) }
      log(RingEventType.MISSION_FAILED, ring, "type=${type.stored} reason=create_failed")
      return
    }
    mission = running
    started = false
    introDone = false
    state.update { it.copy(missionType = type, inGame = false) }
    missionStartedAt = clock.now()
    val levelText = if (type.hasLevels) " level=${level.stored}" else ""
    log(RingEventType.MISSION_STARTED, ring, "type=${type.stored} target=${running.progress.value.target}$levelText$logExtra")
    missionJob = viewModelScope.launch { running.progress.collect { onProgress(ring, it) } }
    (running as? PadsMission)?.let { pads -> gameJob = viewModelScope.launch { pads.game.collect(::onGame) } }
    (running as? CupsMission)?.let { cups -> gameJob = viewModelScope.launch { cups.game.collect(::onCups) } }
    (running as? RepeatMission)?.let { repeat ->
      gameJob = viewModelScope.launch { repeat.game.collect(::onRepeat) }
      voiceJobs =
        listOf(
          viewModelScope.launch {
            repeat.hush.collect {
              gameHush = it
              pushHush()
            }
          },
          viewModelScope.launch {
            repeat.speaking.collect { line ->
              gameSpeaking = line
              publishSpeaking()
              state.update { it.copy(rinSpeaking = line != null) }
            }
          },
          viewModelScope.launch { repeat.micLevel.collect { level -> state.update { it.copy(micLevel = level) } } },
        )
    }
  }

  private fun onRepeat(game: RepeatState) {
    val before = state.value.repeat
    state.update { it.copy(repeat = game) }
    if (game.phase == RepeatPhase.TAPPING) trouble = true
    if (game.phase != RepeatPhase.FEEDBACK || before?.phase == RepeatPhase.FEEDBACK) return
    val feedback = game.feedback ?: return
    // A sentence done: she nods. A miss gets no sulk: an unheard word is as likely the mic's fault as the user's.
    if (feedback == Feedback.RIGHT) cue(Gesture.NOD) else trouble = true
    if (feedback != Feedback.RIGHT) say(Pools.speechMiss(feedback, outOfTries = game.tries >= game.maxTries), gesture = false)
  }

  fun hearAgain() {
    cutLine()
    (mission as? RepeatMission)?.hearAgain()
  }

  fun tapWord(chip: Int) {
    cutLine()
    (mission as? RepeatMission)?.tapWord(chip)
  }

  /**
   * "Can't talk right now" (plan phase-3 section 3): the ring switches to one of the other games, picked the way Rin
   * picks, for the rest of this ring. After a snooze the next ring plans afresh.
   */
  fun cantTalk() {
    val ring = state.value.ring ?: return
    if (practice != null) return
    val from = mission as? RepeatMission ?: return
    if (state.value.passed) return
    cutLine()
    val ready = runCatching { readiness.check() }.getOrDefault(emptyMap())
    val others = MissionType.entries.filter { it != MissionType.SPEECH && ready[it] == Readiness.READY }
    val summary = from.summary()
    stopMission()
    if (others.isEmpty()) {
      state.update { it.copy(missionFailed = true, repeat = null) }
      log(RingEventType.MISSION_FAILED, ring, "type=speech reason=cant_talk_no_other $summary")
      return
    }
    val to = MissionPlanner.rotate(others, time.now().atZone(time.zone()).toLocalDate())
    log(RingEventType.MISSION_SWITCHED, ring, "from=speech to=${to.stored} reason=cant_talk $summary")
    state.update { it.copy(repeat = null, progress = null) }
    run(ring, to, " switchedFrom=speech")
  }

  private fun onCups(game: CupsState) {
    val before = state.value.cups
    val wrong = game.phase == CupsPhase.REVEAL && game.right == false
    // A flipped scold switch stays in view for the rest of that scold only; so does a quiet miss.
    state.update { it.copy(cups = game, scoldSwitched = it.scoldSwitched && wrong, quietMiss = it.quietMiss && wrong) }
    if (wrong && game.picks != before?.picks && calm()) {
      trouble = true
      quietMiss(QUIET_MISS_CUPS_MS)
      return
    }
    if (game.phase == CupsPhase.REVEAL && game.right == false && game.picks != before?.picks) {
      // A wrong pick: she sulks with a huff while both cups are up (the same beat as the colour pads' scold).
      cue(Gesture.HUFF)
      trouble = true
      say(Pools.CUPS_SCOLD, gesture = false)
    }
  }

  /**
   * "Let's play" on the cups brings the table into view first ([RingUiState.cupsStaging]); the game starts once her
   * page shows it, or once the 2D board takes over (the page is gone, or [CUPS_PAGE_WAIT_MS] passed). Otherwise the
   * ball's first showing, the only time the user sees where it starts, could play while the page was still loading
   * (smoke ring, 2026-09-29: "Let's play" 0.25 s before her first frame).
   */
  private fun startCups(cups: CupsMission) {
    if (cups.game.value.phase != CupsPhase.READY || state.value.cupsStaging) return
    state.update { it.copy(cupsStaging = true) }
    if (state.value.cupsView is CupsView.Shown || state.value.cups2d) return beginCups()
    cupsWaitJob?.cancel()
    cupsWaitJob =
      viewModelScope.launch {
        delay(CUPS_PAGE_WAIT_MS)
        if (state.value.cupsView !is CupsView.Shown) use2dCups("page_timeout")
      }
  }

  private fun beginCups() {
    if (!introDone) return
    val cups = mission as? CupsMission ?: return
    if (cups.game.value.phase != CupsPhase.READY) return
    cups.begin()
    state.update { it.copy(cupsStaging = false) }
  }

  /** What the character page says about the cup table (CharacterView). */
  fun onCupsView(view: CupsView) {
    if (state.value.cups2d) return
    state.update { it.copy(cupsView = view) }
    when (view) {
      is CupsView.Shown -> {
        cupsWaitJob?.cancel()
        if (state.value.cupsStaging) beginCups()
      }
      CupsView.Unavailable -> if (state.value.cups != null) use2dCups("page_unavailable")
      CupsView.Pending -> Unit
    }
  }

  /** For the rest of this ring the game draws natively: no flipping back and forth if the page shows up late. */
  private fun use2dCups(reason: String) {
    if (state.value.cups2d) return
    cupsWaitJob?.cancel()
    cups2dReason = reason
    state.update { it.copy(cups2d = true) }
    if (state.value.cupsStaging) beginCups()
  }

  fun pickCup(slot: Int) {
    cutLine()
    (mission as? CupsMission)?.pick(slot)
  }

  private fun onGame(game: PadsState) {
    val before = state.value.pads
    val missed = game.phase == PadsPhase.SCOLD
    state.update { it.copy(pads = game, scoldSwitched = it.scoldSwitched && missed, quietMiss = it.quietMiss && missed) }
    if (missed && before?.phase != PadsPhase.SCOLD && calm()) {
      trouble = true
      quietMiss(QUIET_MISS_PADS_MS)
      return
    }
    if (game.phase == PadsPhase.SCOLD && before?.phase != PadsPhase.SCOLD) {
      // The cut to Rin (D17): she sulks with a huff for the length of the scold.
      cue(Gesture.HUFF)
      trouble = true
      say(Pools.padsScold(game.miss ?: Miss.WRONG), gesture = false)
    }
  }

  /**
   * A miss with scolding off: no Rin and no line, only the red cross and glow for [ms], then the next try (the same
   * skip a tap makes, so a tap still ends it sooner).
   */
  private fun quietMiss(ms: Long) {
    state.update { it.copy(quietMiss = true) }
    quietMissJob?.cancel()
    quietMissJob =
      viewModelScope.launch {
        delay(ms)
        (mission as? PadsMission)?.skipScold()
        (mission as? CupsMission)?.skipScold()
      }
  }

  /**
   * "Let's play": Rin's intro for the game, then the game (the cup table comes into view meanwhile). A tap after the
   * first skips the intro.
   */
  fun startGame() {
    val running = mission ?: return
    if (state.value.passed) return
    // Tapped again during her intro: the pads and Repeat after Rin keep "Let's play" up until the game begins, and a
    // second tap there did nothing (device test, G.1). Every tap after the first starts the game now.
    if (started) return skip()
    started = true
    state.update { it.copy(inGame = true) }
    (running as? CupsMission)?.let(::startCups)
    introJob =
      viewModelScope.launch {
        // A tap anywhere cancels her intro (skip), and the game starts as if she had finished.
        val intro = say(Pools.intro(running.type))
        introLine = intro
        intro.join()
        introLine = null
        introDone = true
        (mission as? RepeatMission)?.begin()
        (mission as? PadsMission)?.begin()
        if (state.value.cupsView is CupsView.Shown || state.value.cups2d) beginCups()
      }
  }

  fun tapPad(pad: Pad) {
    cutLine()
    (mission as? PadsMission)?.tap(pad)
  }

  /**
   * Any button pressed while she talks cuts her line at once (G.1, the user: every button works at any time). Not once
   * the ring is over: her won line, or her line after a snooze, is all the screen still shows.
   */
  private fun cutLine() {
    val current = state.value
    if (current.passed || current.leaving) return
    opening?.cancel()
    speaker.stop()
  }

  /**
   * A tap anywhere on the screen, outside the buttons (G.1): skips her intro (the game starts now) or her scold (the
   * next try starts now, once the miss has been seen), and otherwise just cuts her line. Once the ring is over a tap
   * closes the screen instead ([close]).
   */
  fun skip() {
    val current = state.value
    if (current.passed || current.leaving) return
    introLine?.cancel()
    cutLine()
    if (current.scolding) {
      (mission as? PadsMission)?.skipScold()
      (mission as? CupsMission)?.skipScold()
    }
  }

  /**
   * The scold switch on the ring screen (G.1): off, she goes quiet and calm at once, this alarm keeps it off, and so do
   * new alarms, until the user turns it back on in the editor. On again (a slip of the thumb) undoes both.
   */
  fun setScold(on: Boolean) {
    val ring = state.value.ring ?: return
    if (scold == on) return
    scold = on
    if (!on) speaker.stop()
    state.update { it.copy(scold = on, scoldSwitched = true, calm = calm()) }
    val alarmId = ring.request.alarmId
    // App scope: the screen may close right after the tap; the write must still land.
    appScope.launch {
      runCatching {
        if (practice == null && alarmId > 0) writer.setScold(alarmId, on)
        settings.setLastScold(on)
      }
    }
    log(RingEventType.SCOLD_SWITCHED, ring, "scold=$on")
  }

  private fun onProgress(ring: ActiveRing, progress: MissionProgress) {
    val before = state.value.progress
    state.update { it.copy(progress = progress) }
    if (before != null && (progress.done > before.done || progress.activity > before.activity)) {
      if (practice != null) practiceProgressAt = clock.now() else ringState.reportProgress(clock.now())
      refreshPhase()
      // No event marks the idle timeout, so a timer does: STALLED arrives when progress simply stops. Each step
      // restarts it; a one-shot rather than a ticker, so nothing keeps running once the ring is over.
      stallJob?.cancel()
      stallJob =
        viewModelScope.launch {
          delay(RingPolicy.MISSION_IDLE.toMillis())
          refreshPhase()
        }
    }
    when (progress.state) {
      MissionState.RUNNING -> Unit
      MissionState.PASSED -> {
        if (state.value.passed) return
        state.update { it.copy(passed = true, inGame = false) }
        log(
          RingEventType.MISSION_PASSED,
          ring,
          "type=${mission?.type?.stored} done=${progress.done}/${progress.target} tookMs=${clock.now() - missionStartedAt}" +
            summary(),
        )
        stopMission()
        refreshPhase()
        if (practice == null) commandFlow.tryEmit(RingCommand.Dismiss(RingService.SOURCE_MISSION))
        celebrate(ring)
      }
      MissionState.FAILED -> {
        state.update { it.copy(missionFailed = true) }
        log(
          RingEventType.MISSION_FAILED,
          ring,
          "type=${mission?.type?.stored} done=${progress.done}/${progress.target}" + summary(),
        )
        stopMission()
      }
    }
  }

  private fun summary(): String {
    val board = if (mission is CupsMission) " board=" + (cups2dReason?.let { "2d:$it" } ?: "3d") else ""
    return (mission?.summary()?.takeIf { it.isNotEmpty() }?.let { " $it" } ?: "") + board
  }

  /** Her won line (her gesture for it, or a clap), then one closing remark; then the screen closes. */
  private fun celebrate(ring: ActiveRing) {
    viewModelScope.launch {
      val won = lines.pick(Pools.won(clean = !trouble), ringDay ?: today(), morning)
      cue(won?.gesture ?: Gesture.CLAP)
      // A practice round stays for "Play again" or "Done": her won line, and no goodbye.
      if (practice != null) {
        won?.let { speaker.say(it) }
        return@launch
      }
      if (won == null) {
        delay(CELEBRATE_MS)
      } else {
        speaker.say(won).join()
        say(Pools.closing(ring.request.time)).join()
      }
      state.update { it.copy(finished = true) }
    }
  }

  private fun onRingEnded() {
    // How far the game got, for a ring that stopped some other way: a miss the user gave up on is still data.
    val ring = state.value.ring
    if (ring != null && mission != null && !state.value.passed && !state.value.missionFailed) {
      log(RingEventType.MISSION_UNFINISHED, ring, "type=${mission?.type?.stored} tookMs=${clock.now() - missionStartedAt}" + summary())
    }
    stopMission()
    // After a pass the celebration closes the screen.
    if (state.value.ring == null || state.value.passed) return
    val leaving = farewell
    if (leaving == null || !leaving.isActive) {
      state.update { it.copy(finished = true) }
      return
    }
    viewModelScope.launch {
      leaving.join()
      state.update { it.copy(finished = true) }
    }
  }

  /** A tap on the screen once the ring is over (after a pass, a snooze or the emergency stop): it closes now. */
  fun close() {
    if (state.value.passed || state.value.leaving) state.update { it.copy(finished = true) }
  }

  /** "Done" in a practice round: the screen closes, at any point of the round. */
  fun endPractice() {
    if (practice == null) return
    stopMission()
    speaker.stop()
    state.update { it.copy(finished = true) }
  }

  private fun refreshPhase() {
    val current = state.value
    if (current.ring == null) return
    val phase = RingMoods.phase(current.passed, clock.now(), if (practice != null) practiceProgressAt else ringState.lastProgressAt)
    if (phase == current.phase) return
    state.update { it.copy(phase = phase) }
    // A pass brings her won line's own gesture (celebrate).
    if (phase != RingPhase.PASSED) cue(RingMoods.cue(phase))
  }

  fun snooze() {
    val request = state.value.ring?.request ?: return
    leave(Pools.snooze(request.snoozeCount, request.snoozesLeft))
    commandFlow.tryEmit(RingCommand.Snooze)
  }

  /** The plain Dismiss: only offered when there is no mission, or it failed. */
  fun dismiss() {
    commandFlow.tryEmit(RingCommand.Dismiss(RingService.SOURCE_SCREEN))
  }

  fun emergencyStop() {
    leave(Pools.EMERGENCY)
    commandFlow.tryEmit(RingCommand.Dismiss(RingService.SOURCE_EMERGENCY))
  }

  /** Her line as the ring stops without a pass; the screen shows nothing else and waits for it (a tap closes it). */
  private fun leave(pool: String) {
    if (state.value.ring == null || state.value.passed || state.value.leaving) return
    state.update { it.copy(leaving = true, inGame = false) }
    farewell = say(pool)
  }

  private fun stopMission() {
    missionJob?.cancel()
    missionJob = null
    gameJob?.cancel()
    gameJob = null
    stallJob?.cancel()
    stallJob = null
    cupsWaitJob?.cancel()
    cupsWaitJob = null
    voiceJobs.forEach(Job::cancel)
    voiceJobs = emptyList()
    quietMissJob?.cancel()
    quietMissJob = null
    introJob?.cancel()
    introJob = null
    gameHush = Hush.NONE
    pushHush()
    gameSpeaking = null
    publishSpeaking()
    mission?.stop()
    mission = null
  }

  override fun onCleared() {
    stopMission()
    speaker.release()
  }

  private fun log(type: RingEventType, ring: ActiveRing, detail: String) {
    // A practice round is no ring: the ring log (and the reliability run) never sees it.
    if (practice != null) return
    // App scope: a pass logs and then the screen closes; the row must still land.
    appScope.launch { ringLog.record(type, ring.request.alarmId, ring.request.scheduledAt, detail) }
  }

  companion object {
    /** The longest her opening line waits for her page (its first frame came 1.8 s after the screen opened on the 14T). */
    const val OPENING_WAIT_MS = 5_000L

    /** After her page is ready, a moment for its first frames before she speaks. */
    const val OPENING_SETTLE_MS = 300L

    /**
     * A quiet miss's moment (scolding off, the user: "just a moment"): the pads come back with a new demo after it; the
     * cups, which must first rise to show the ball, come down and shuffle after theirs (up at 550 ms, so ~0.45 s seen).
     */
    const val QUIET_MISS_PADS_MS = 700L
    const val QUIET_MISS_CUPS_MS = 1_000L

    /** How long Rin claps after a pass before the ring screen closes, when she has no lines to say. */
    const val CELEBRATE_MS = 2_500L

    /**
     * How long "Let's play" waits for her page to show the cup table before the 2D board takes over: her page's first
     * frame came 1.8 s after the ring screen opened on the 14T, and the camera move takes 0.6 s.
     */
    const val CUPS_PAGE_WAIT_MS = 4_000L

    /** The intent extra (and saved-state key) naming a practice round's game by [MissionType.stored]. */
    const val EXTRA_PRACTICE = "practice"

    /** A practice round's level by [Difficulty.stored], and its scold switch: the editor's draft (G.1). */
    const val EXTRA_LEVEL = "practice.level"
    const val EXTRA_SCOLD = "practice.scold"

    /** A practice round's stand-in alarm id: no stored alarm has it (ids start at 1). */
    const val PRACTICE_ALARM_ID = -2L

    /** Keys a practice round's session pools, apart from any real morning. */
    private val PRACTICE_MORNING = Any()
  }
}

/**
 * @property ring the ring on screen; kept after it ends so the celebration still shows the time and label.
 * @property progress the mission's, or null when there is no mission (plain Dismiss).
 * @property missionFailed the mission broke; a plain Dismiss takes over.
 * @property finished close the screen.
 * @property pads the colour-pads game, when that is the mission.
 * @property missionType the game being played: the ring's planned one, or the one "Can't talk right now" switched to.
 * @property repeat Repeat after Rin, when that is the mission; [micLevel] the mic's level while it listens.
 * @property cups the cup shuffle, when that is the mission; [cupsView] what her page shows of it, and [cups2d] that the
 *   native 2D board plays it instead (her page is gone, or did not show the table in time). [cupsStaging]: "Let's
 *   play" was tapped and the table is coming into view; the game starts once it is (and her intro is over).
 * @property line Rin's line on screen (the subtitle), or null.
 * @property leaving a snooze or the emergency stop was tapped: the screen shows only Rin until her line is over.
 * @property calm no pouting (scold off, or a rest or sick day): a pouty mood shows as cheerful.
 * @property inGame from "Let's play" to the win (or a snooze, the emergency stop): her arms are in the game, so only
 *   head-and-shoulder gestures play (Gesture.armsStill).
 * @property quietMiss a miss with scolding off: the red cross and glow show, Rin stays out of it (the pads stay up).
 * @property scold the scold switch's position (G.1); [scoldSwitched]: it was flipped on this screen, so it stays in view
 *   for the rest of that scold even when off.
 * @property practice a practice round (UX.8): no ring behind it, "Done" instead of Snooze and the emergency hold.
 */
data class RingUiState(
  val ring: ActiveRing? = null,
  val progress: MissionProgress? = null,
  val phase: RingPhase = RingPhase.WAKING,
  val passed: Boolean = false,
  val missionFailed: Boolean = false,
  val finished: Boolean = false,
  val pads: PadsState? = null,
  val cups: CupsState? = null,
  val cupsView: CupsView = CupsView.Pending,
  val cups2d: Boolean = false,
  val cupsStaging: Boolean = false,
  val missionType: MissionType? = null,
  val repeat: RepeatState? = null,
  val micLevel: Float = 0f,
  val rinSpeaking: Boolean = false,
  val line: Line? = null,
  val leaving: Boolean = false,
  val calm: Boolean = false,
  val practice: Boolean = false,
  val scold: Boolean = true,
  val scoldSwitched: Boolean = false,
  val quietMiss: Boolean = false,
  val inGame: Boolean = false,
) {
  /** Rin's mood: her line's while she says it, otherwise the phase's, except while she scolds a missed round. */
  val mood: Mood
    get() {
      val mood =
        line?.emotion ?: if ((pads?.phase == PadsPhase.SCOLD || cupsScold) && !quietMiss) RingMoods.SCOLD_MOOD else RingMoods.mood(phase)
      return if (calm && mood.pouty) Mood.CHEERFUL else mood
    }

  /** A wrong cup is up: Rin sulks and says one of her lines. */
  val cupsScold: Boolean
    get() = cups?.phase == CupsPhase.REVEAL && cups.right == false

  /** Rin's beat after a miss in the pads or the cups: she scolds (unless scold is off), and a tap skips it. */
  val scolding: Boolean
    get() = !passed && !leaving && (pads?.phase == PadsPhase.SCOLD || cupsScold)

  /** The scold switch shows during a scold of an alarm that scolds (G.1), and for the rest of that beat once flipped. */
  val showScoldSwitch: Boolean
    get() = scolding && (scold || scoldSwitched) && ring?.dayMode == null

  /** A plain Dismiss button instead of the mission and the emergency hold. */
  val plainDismiss: Boolean
    get() = ring?.mission == null || missionFailed
}

sealed interface RingCommand {
  data object Snooze : RingCommand

  data class Dismiss(val source: String) : RingCommand
}
