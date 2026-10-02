package io.github.earthkodyai.rinalarm.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.AlarmSound
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmWriter
import io.github.earthkodyai.rinalarm.alarm.ring.MusicCatalog
import io.github.earthkodyai.rinalarm.alarm.ring.MusicTheme
import io.github.earthkodyai.rinalarm.alarm.ring.PreviewSounds
import io.github.earthkodyai.rinalarm.alarm.ring.RingSound
import io.github.earthkodyai.rinalarm.data.AlarmRepository
import io.github.earthkodyai.rinalarm.dialogue.HomeMoments
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionPlan
import io.github.earthkodyai.rinalarm.mission.MissionPlanner
import io.github.earthkodyai.rinalarm.mission.MissionReadiness
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.Readiness
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Edits one alarm, or a new one when [alarmId] is [NEW_ALARM_ID]. Changes stay in a draft until [save]; every write
 * goes through [AlarmWriter], so AlarmManager is re-armed in the same step.
 *
 * Saving always switches the alarm on: someone who just set a time expects it to ring.
 */
@HiltViewModel(assistedFactory = AlarmEditorViewModel.Factory::class)
class AlarmEditorViewModel
@AssistedInject
constructor(
  @Assisted private val alarmId: Long,
  private val repository: AlarmRepository,
  private val writer: AlarmWriter,
  private val time: TimeSource,
  private val missionReadiness: MissionReadiness,
  private val moments: HomeMoments,
  private val music: MusicCatalog,
  private val previews: PreviewSounds,
) : ViewModel() {
  private val session = MutableStateFlow<Session>(Session.Loading)
  private val readiness = MutableStateFlow(readMissions())
  private val themes = runCatching { music.themes() }.getOrDefault(emptyList())
  private val previewing = MutableStateFlow<AlarmSound?>(null)
  private var preview: Job? = null
  private var previewSound: RingSound? = null

  val uiState: StateFlow<AlarmEditorUiState> =
    combine(session, readiness, previewing, time.minuteTicks) { session, readiness, previewing, _ ->
        session.toUiState(readiness, previewing)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlarmEditorUiState.Loading)

  init {
    if (alarmId == NEW_ALARM_ID) {
      session.value = Session.Open(NEW_ALARM, NEW_ALARM)
    } else {
      viewModelScope.launch {
        val alarm = repository.get(alarmId)
        session.value = if (alarm == null) Session.NotFound else Session.Open(alarm, alarm)
      }
    }
  }

  fun setTime(value: LocalTime) = edit { it.copy(time = value.withSecond(0).withNano(0)) }

  fun toggleDay(day: DayOfWeek) = edit { it.copy(repeatDays = it.repeatDays.toggle(day)) }

  fun setLabel(value: String) = edit { it.copy(label = value.replace('\n', ' ').take(RingChoices.LABEL_MAX)) }

  fun setRampSeconds(value: Int) = editRing { it.copy(rampSeconds = value) }

  fun setVibrate(value: Boolean) = editRing { it.copy(vibrate = value) }

  fun setSnoozeMinutes(value: Int) = editRing { it.copy(snoozeMinutes = value) }

  fun setMaxSnoozes(value: Int) = editRing { it.copy(maxSnoozes = value) }

  fun setMission(value: MissionChoice) = edit { it.copy(mission = value) }

  /**
   * A sound tile was tapped: the alarm takes that sound and plays a few seconds of it (the user, 2026-10-02). Tapping
   * the tile that is playing stops it instead.
   */
  fun pickSound(value: AlarmSound) {
    val open = session.value as? Session.Open ?: return
    if (open.busy) return
    if (previewing.value == value) {
      stopPreview()
      return
    }
    editRing { it.copy(sound = value) }
    startPreview(value, open.draft)
  }

  /** Silences the preview: the editor left the screen, or a save or delete started. */
  fun stopPreview() {
    preview?.cancel()
    preview = null
    previewSound?.let { runCatching { it.release() } }
    previewSound = null
    previewing.value = null
  }

  /**
   * Plays [sound] for [PREVIEW_MS], fading out over the last [FADE_MS]. Rin picks plays the theme she would pick for
   * the draft's next ring, so the user hears what they will wake up to.
   */
  private fun startPreview(sound: AlarmSound, draft: Alarm) {
    stopPreview()
    val zone = time.zone()
    val day = (draft.copy(enabled = true).nextTrigger(time.now(), zone) ?: time.now()).atZone(zone).toLocalDate()
    val theme = AlarmSound.resolve(sound, themes.map { it.id }, day)
    val player = runCatching { previews.open(theme) }.getOrNull() ?: return
    previewSound = player
    previewing.value = sound
    preview =
      viewModelScope.launch {
        runCatching {
            player.setGain(1f)
            player.play()
          }
          .onFailure {
            stopPreview()
            return@launch
          }
        delay(PREVIEW_MS - FADE_MS)
        for (step in 1..FADE_STEPS) {
          val left = 1f - step.toFloat() / FADE_STEPS
          // Squared, so the fade sounds even to the ear rather than dropping late.
          runCatching { player.setGain(left * left) }
          delay(FADE_MS / FADE_STEPS)
        }
        stopPreview()
      }
  }

  override fun onCleared() {
    stopPreview()
  }

  /** Re-reads which missions can run: on resume, and after a permission answer (the user may change it in Settings). */
  fun refreshMissions() {
    readiness.value = readMissions()
  }

  /**
   * "Try this game" (the user, 2026-10-02): the game this alarm would play on its next ring, for a practice round.
   * Rin picks gives the game she would pick that day (as the sound preview plays her pick); None gives null. A game
   * that is not ready still plays: Repeat after Rin without the mic falls back to tapping the words.
   */
  fun gameToTry(): MissionType? {
    val open = session.value as? Session.Open ?: return null
    val draft = open.draft
    val zone = time.zone()
    val day = (draft.copy(enabled = true).nextTrigger(time.now(), zone) ?: time.now()).atZone(zone).toLocalDate()
    return when (val choice = draft.mission) {
      MissionChoice.None -> null
      is MissionChoice.Only -> choice.type
      MissionChoice.RinPicks -> (MissionPlanner.plan(choice, readiness.value, day) as? MissionPlan.Run)?.type
    }
  }

  private fun readMissions(): Map<MissionType, Readiness> = runCatching { missionReadiness.check() }.getOrDefault(emptyMap())

  fun save() {
    val open = session.value as? Session.Open ?: return
    if (open.busy) return // a second tap while the first save is still running
    stopPreview()
    session.value = open.copy(busy = true)
    viewModelScope.launch {
      val alarm = open.draft.copy(label = open.draft.label.trim(), enabled = true)
      session.value =
        try {
          writer.save(alarm)
          // Rin says so on the home screen (task 4.2).
          moments.alarmSaved.trySend(Unit)
          Session.Saved(ringsIn(alarm))
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          open.copy(failed = true)
        }
    }
  }

  fun delete() {
    val open = session.value as? Session.Open ?: return
    if (open.busy || open.original.id == NEW_ALARM_ID) return
    stopPreview()
    session.value = open.copy(busy = true)
    viewModelScope.launch {
      session.value =
        try {
          writer.delete(open.original.id)
          Session.Deleted
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          open.copy(failed = true)
        }
    }
  }

  private fun edit(change: (Alarm) -> Alarm) =
    session.update { if (it is Session.Open && !it.busy) it.copy(draft = change(it.draft), failed = false) else it }

  private fun editRing(change: (RingOptions) -> RingOptions) = edit { it.copy(ring = change(it.ring)) }

  private fun ringsIn(alarm: Alarm): Duration? =
    alarm.copy(enabled = true).nextTrigger(time.now(), time.zone())?.let { Duration.between(time.now(), it) }

  private fun Session.toUiState(readiness: Map<MissionType, Readiness>, previewing: AlarmSound?): AlarmEditorUiState =
    when (this) {
      Session.Loading -> AlarmEditorUiState.Loading
      Session.NotFound -> AlarmEditorUiState.NotFound
      is Session.Open ->
        AlarmEditorUiState.Editing(
          draft = draft,
          isNew = original.id == NEW_ALARM_ID,
          hasChanges = draft != original,
          ringsIn = ringsIn(draft),
          busy = busy,
          failed = failed,
          missionReadiness = readiness,
          themes = themes,
          previewing = previewing,
        )
      is Session.Saved -> AlarmEditorUiState.Saved(ringsIn)
      Session.Deleted -> AlarmEditorUiState.Deleted
    }

  private sealed interface Session {
    data object Loading : Session

    data object NotFound : Session

    data class Open(val original: Alarm, val draft: Alarm, val busy: Boolean = false, val failed: Boolean = false) :
      Session

    data class Saved(val ringsIn: Duration?) : Session

    data object Deleted : Session
  }

  @AssistedFactory
  interface Factory {
    fun create(alarmId: Long): AlarmEditorViewModel
  }

  companion object {
    const val NEW_ALARM_ID = 0L

    /** What "Add alarm" starts from. */
    val NEW_ALARM = Alarm(id = NEW_ALARM_ID, time = LocalTime.of(7, 0))

    /** How long a sound tile plays when tapped (the user, 2026-10-02: about six seconds, fading out). */
    const val PREVIEW_MS = 6_000L
    const val FADE_MS = 1_500L
    private const val FADE_STEPS = 30
  }
}

sealed interface AlarmEditorUiState {
  data object Loading : AlarmEditorUiState

  /** The alarm was deleted before the editor could open it. */
  data object NotFound : AlarmEditorUiState

  /**
   * @property hasChanges the draft differs from what is stored; leaving then asks before discarding.
   * @property ringsIn how long until the draft would ring once saved (saving switches it on).
   * @property busy a save or delete is running; edits are ignored until it finishes.
   * @property failed the last save or delete threw (e.g. disk full); the draft is kept so the user can retry.
   * @property missionReadiness which missions can run on this phone now; the editor explains the ones that can't.
   * @property themes the alarm themes this build carries (UX.7); none in builds without the music, which only beep.
   * @property previewing the sound whose tile is playing its preview, if any.
   */
  data class Editing(
    val draft: Alarm,
    val isNew: Boolean,
    val hasChanges: Boolean,
    val ringsIn: Duration?,
    val busy: Boolean,
    val failed: Boolean = false,
    val missionReadiness: Map<MissionType, Readiness> = emptyMap(),
    val themes: List<MusicTheme> = emptyList(),
    val previewing: AlarmSound? = null,
  ) : AlarmEditorUiState {
    /** The missions this alarm could run that are not ready, and why (shown under the mission choice). */
    val missionProblems: Map<MissionType, Readiness>
      get() {
        val wanted =
          when (val choice = draft.mission) {
            MissionChoice.None -> emptyList()
            // Rin only needs one ready mission to pick from.
            MissionChoice.RinPicks ->
              if (MissionType.entries.any { missionReadiness[it] == Readiness.READY }) emptyList()
              else MissionType.entries
            is MissionChoice.Only -> listOf(choice.type)
          }
        return wanted.associateWith { missionReadiness[it] ?: Readiness.READY }.filterValues { it != Readiness.READY }
      }

  }

  data class Saved(val ringsIn: Duration?) : AlarmEditorUiState

  data object Deleted : AlarmEditorUiState
}

/** The fixed, tap-only choices the editor offers (chosen by the user in task 1.3). */
object RingChoices {
  val RAMP_SECONDS = listOf(0, 15, 30, 60)
  val SNOOZE_MINUTES = listOf(1, 5, 10, 15)
  val MAX_SNOOZES = listOf(0, 1, 2, 3, 5)
  const val LABEL_MAX = 40

  /** The mission choices, in the order the editor shows them (D15: Rin picks first, the default). */
  val MISSIONS: List<MissionChoice> =
    listOf(MissionChoice.RinPicks) + MissionType.entries.map(MissionChoice::Only) + MissionChoice.None

  /**
   * [standard] plus [current] when the alarm already holds a value outside it (set by the debug hook or a future
   * version), so opening the editor never silently changes it.
   */
  fun withCurrent(standard: List<Int>, current: Int): List<Int> = (standard + current).distinct().sorted()
}
