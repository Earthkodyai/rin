package io.github.earthkodyai.rinalarm.alarm.ring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import io.github.earthkodyai.rinalarm.mission.Pad
import io.github.earthkodyai.rinalarm.mission.PadsMission
import io.github.earthkodyai.rinalarm.mission.PadsPhase
import io.github.earthkodyai.rinalarm.mission.PadsState
import io.github.earthkodyai.rinalarm.mission.QrScanPolicy
import io.github.earthkodyai.rinalarm.mission.Readiness
import io.github.earthkodyai.rinalarm.mission.RepeatMission
import io.github.earthkodyai.rinalarm.mission.RepeatPhase
import io.github.earthkodyai.rinalarm.mission.RepeatState
import io.github.earthkodyai.rinalarm.mission.ScanMission
import io.github.earthkodyai.rinalarm.mission.ScanVerdict
import io.github.earthkodyai.rinalarm.mission.SeenCode
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
  settings: AppSettings,
) : ViewModel() {
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

  /** Pout off (Phase 5), as last read from the settings. */
  @Volatile private var poutOff = false
  private val lines = PoutFilter(book) { calm() }
  private val speaker = RinSpeaker(voices.create(alarm = true), viewModelScope)
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

  // Volatile: onScan reads it from the camera's analysis thread.
  @Volatile private var mission: Mission? = null
  private var missionJob: Job? = null
  private var gameJob: Job? = null
  private var voiceJobs: List<Job> = emptyList()
  private var stallJob: Job? = null
  private var cameraJob: Job? = null
  private var cupsWaitJob: Job? = null
  /** Why the cups went 2D, for the log (null: her page played them). */
  private var cups2dReason: String? = null
  private var ringKey: Any? = null
  private var missionStartedAt = 0L

  init {
    viewModelScope.launch {
      settings.poutOff.collect { off ->
        poutOff = off
        state.update { it.copy(calm = calm()) }
      }
    }
    viewModelScope.launch {
      ringState.active.collect { ring ->
        if (ring == null) onRingEnded() else onRing(ring)
      }
    }
    viewModelScope.launch { speaker.line.collect { line -> state.update { it.copy(line = line) } } }
    viewModelScope.launch { speaker.hush.collect { pushHush() } }
    viewModelScope.launch { speaker.speaking.collect { publishSpeaking() } }
  }

  /** No pouting: Pout off, or a rest or sick day's ring (it never scolds). */
  private fun calm(): Boolean = poutOff || state.value.ring?.dayMode != null

  /** A gesture for Rin, unless it is a pouty one and she is [calm]. */
  private fun cue(gesture: Gesture) {
    if (!(gesture.pouty && calm())) cueFlow.tryEmit(gesture.onRing())
  }

  private fun pushHush() = ringState.setHush(maxOf(gameHush, speaker.hush.value))

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
    state.value = RingUiState(ring = ring)
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

  private fun run(ring: ActiveRing, type: MissionType, logExtra: String) {
    val running = runCatching { missions.create(type).also(Mission::start) }.getOrNull()
    if (running == null) {
      state.update { it.copy(missionFailed = true) }
      log(RingEventType.MISSION_FAILED, ring, "type=${type.stored} reason=create_failed")
      return
    }
    mission = running
    started = false
    introDone = false
    state.update { it.copy(missionType = type) }
    missionStartedAt = clock.now()
    log(RingEventType.MISSION_STARTED, ring, "type=${type.stored} target=${running.progress.value.target}$logExtra")
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
    (mission as? RepeatMission)?.hearAgain()
  }

  fun tapWord(chip: Int) {
    (mission as? RepeatMission)?.tapWord(chip)
  }

  /**
   * "Can't talk right now" (plan phase-3 section 3): the ring switches to one of the other games, picked the way Rin
   * picks, for the rest of this ring. After a snooze the next ring plans afresh.
   */
  fun cantTalk() {
    val ring = state.value.ring ?: return
    val from = mission as? RepeatMission ?: return
    if (state.value.passed) return
    val ready = runCatching { readiness.check() }.getOrDefault(emptyMap())
    val others = MissionType.offeredEntries.filter { it != MissionType.SPEECH && ready[it] == Readiness.READY }
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
    state.update { it.copy(cups = game) }
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
    (mission as? CupsMission)?.pick(slot)
  }

  private fun onGame(game: PadsState) {
    val before = state.value.pads
    state.update { it.copy(pads = game) }
    if (game.phase == PadsPhase.SCOLD && before?.phase != PadsPhase.SCOLD) {
      // The cut to Rin (D17): she sulks with a huff for the length of the scold.
      cue(Gesture.HUFF)
      trouble = true
      say(Pools.padsScold(game.miss ?: Miss.WRONG), gesture = false)
    }
  }

  /**
   * "Let's play": Rin's intro for the game, then the game (the cup table comes into view meanwhile). Taps after the
   * first are ignored.
   */
  fun startGame() {
    val running = mission ?: return
    if (started || state.value.passed) return
    started = true
    (running as? CupsMission)?.let(::startCups)
    introJob =
      viewModelScope.launch {
        say(Pools.intro(running.type)).join()
        introDone = true
        (mission as? RepeatMission)?.begin()
        (mission as? PadsMission)?.begin()
        if (state.value.cupsView is CupsView.Shown || state.value.cups2d) beginCups()
      }
  }

  fun tapPad(pad: Pad) {
    (mission as? PadsMission)?.tap(pad)
  }

  private fun onProgress(ring: ActiveRing, progress: MissionProgress) {
    val before = state.value.progress
    state.update { it.copy(progress = progress) }
    if (before != null && (progress.done > before.done || progress.activity > before.activity)) {
      if (state.value.cameraOpen) scheduleCameraClose()
      ringState.reportProgress(clock.now())
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
        state.update { it.copy(passed = true, cameraOpen = false, scanHint = null) }
        log(
          RingEventType.MISSION_PASSED,
          ring,
          "type=${mission?.type?.stored} done=${progress.done}/${progress.target} tookMs=${clock.now() - missionStartedAt}" +
            summary(),
        )
        stopMission()
        refreshPhase()
        commandFlow.tryEmit(RingCommand.Dismiss(RingService.SOURCE_MISSION))
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

  /** "Scan sticker" (user decision: the camera opens on a tap, never by itself while the user is still in bed). */
  fun openCamera() {
    val scan = mission as? ScanMission ?: return
    if (state.value.cameraOpen || state.value.passed) return
    scan.cameraOpened()
    state.update { it.copy(cameraOpen = true, scanHint = null) }
    scheduleCameraClose()
  }

  fun closeCamera() {
    cameraJob?.cancel()
    cameraJob = null
    state.update { it.copy(cameraOpen = false, scanHint = null) }
  }

  /** [QrScanPolicy.CAMERA_IDLE] after opening, or after the last sighting or step, the camera closes by itself. */
  private fun scheduleCameraClose() {
    cameraJob?.cancel()
    cameraJob =
      viewModelScope.launch {
        delay(QrScanPolicy.CAMERA_IDLE.toMillis())
        closeCamera()
      }
  }

  /** From the camera's analysis thread: one frame's codes. */
  fun onScan(codes: List<SeenCode>) {
    val scan = mission as? ScanMission ?: return
    val verdict = scan.onCodes(codes)
    // The hint keeps the last thing seen; an empty frame between two sightings must not make it flicker.
    if (verdict == ScanVerdict.TOO_FAR || verdict == ScanVerdict.OTHER) {
      state.update { if (it.cameraOpen) it.copy(scanHint = verdict) else it }
    }
  }

  fun onTorch(on: Boolean, auto: Boolean) {
    (mission as? ScanMission)?.torchChanged(on, auto)
  }

  /** The camera could not start: the ring falls back to a plain Dismiss, as for any broken mission. */
  fun onCameraError(error: Throwable) {
    val ring = state.value.ring ?: return
    if (state.value.passed || state.value.missionFailed) return
    state.update { it.copy(missionFailed = true, cameraOpen = false) }
    log(RingEventType.MISSION_FAILED, ring, "type=${mission?.type?.stored} reason=camera_${error.javaClass.simpleName}" + summary())
    stopMission()
  }

  /** Her won line (her gesture for it, or a clap), then one closing remark; then the screen closes. */
  private fun celebrate(ring: ActiveRing) {
    viewModelScope.launch {
      val won = lines.pick(Pools.won(clean = !trouble), ringDay ?: today(), morning)
      cue(won?.gesture ?: Gesture.CLAP)
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

  private fun refreshPhase() {
    val current = state.value
    if (current.ring == null) return
    val phase = RingMoods.phase(current.passed, clock.now(), ringState.lastProgressAt)
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
    state.update { it.copy(leaving = true) }
    farewell = say(pool)
  }

  private fun stopMission() {
    missionJob?.cancel()
    missionJob = null
    gameJob?.cancel()
    gameJob = null
    stallJob?.cancel()
    stallJob = null
    cameraJob?.cancel()
    cameraJob = null
    cupsWaitJob?.cancel()
    cupsWaitJob = null
    voiceJobs.forEach(Job::cancel)
    voiceJobs = emptyList()
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
    // App scope: a pass logs and then the screen closes; the row must still land.
    appScope.launch { ringLog.record(type, ring.request.alarmId, ring.request.scheduledAt, detail) }
  }

  companion object {
    /** The longest her opening line waits for her page (its first frame came 1.8 s after the screen opened on the 14T). */
    const val OPENING_WAIT_MS = 5_000L

    /** After her page is ready, a moment for its first frames before she speaks. */
    const val OPENING_SETTLE_MS = 300L

    /** How long Rin claps after a pass before the ring screen closes, when she has no lines to say. */
    const val CELEBRATE_MS = 2_500L

    /**
     * How long "Let's play" waits for her page to show the cup table before the 2D board takes over: her page's first
     * frame came 1.8 s after the ring screen opened on the 14T, and the camera move takes 0.6 s.
     */
    const val CUPS_PAGE_WAIT_MS = 4_000L
  }
}

/**
 * @property ring the ring on screen; kept after it ends so the celebration still shows the time and label.
 * @property progress the mission's, or null when there is no mission (plain Dismiss).
 * @property missionFailed the mission broke; a plain Dismiss takes over.
 * @property finished close the screen.
 * @property cameraOpen the QR mission's camera is on (it opens on a tap).
 * @property scanHint what the camera last saw that was not a pass: the sticker from too far, or another code.
 * @property pads the colour-pads game, when that is the mission.
 * @property missionType the game being played: the ring's planned one, or the one "Can't talk right now" switched to.
 * @property repeat Repeat after Rin, when that is the mission; [micLevel] the mic's level while it listens.
 * @property cups the cup shuffle, when that is the mission; [cupsView] what her page shows of it, and [cups2d] that the
 *   native 2D board plays it instead (her page is gone, or did not show the table in time). [cupsStaging]: "Let's
 *   play" was tapped and the table is coming into view; the game starts once it is (and her intro is over).
 * @property line Rin's line on screen (the subtitle), or null.
 * @property leaving a snooze or the emergency stop was tapped: the screen shows only Rin until her line is over.
 * @property calm no pouting (Pout off, or a rest or sick day): a pouty mood shows as cheerful.
 */
data class RingUiState(
  val ring: ActiveRing? = null,
  val progress: MissionProgress? = null,
  val phase: RingPhase = RingPhase.WAKING,
  val passed: Boolean = false,
  val missionFailed: Boolean = false,
  val finished: Boolean = false,
  val cameraOpen: Boolean = false,
  val scanHint: ScanVerdict? = null,
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
) {
  /** Rin's mood: her line's while she says it, otherwise the phase's, except while she scolds a missed round. */
  val mood: Mood
    get() {
      val mood =
        line?.emotion ?: if (pads?.phase == PadsPhase.SCOLD || cupsScold) RingMoods.SCOLD_MOOD else RingMoods.mood(phase)
      return if (calm && mood.pouty) Mood.CHEERFUL else mood
    }

  /** A wrong cup is up: Rin sulks and says one of her lines. */
  val cupsScold: Boolean
    get() = cups?.phase == CupsPhase.REVEAL && cups.right == false

  /** A plain Dismiss button instead of the mission and the emergency hold. */
  val plainDismiss: Boolean
    get() = ring?.mission == null || missionFailed
}

sealed interface RingCommand {
  data object Snooze : RingCommand

  data class Dismiss(val source: String) : RingCommand
}
