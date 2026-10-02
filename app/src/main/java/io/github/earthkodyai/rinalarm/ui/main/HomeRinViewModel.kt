package io.github.earthkodyai.rinalarm.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.character.GestureDirector
import io.github.earthkodyai.rinalarm.character.Speaking
import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.dialogue.HomeMoments
import io.github.earthkodyai.rinalarm.dialogue.Line
import io.github.earthkodyai.rinalarm.dialogue.LineBook
import io.github.earthkodyai.rinalarm.dialogue.LineVoiceFactory
import io.github.earthkodyai.rinalarm.dialogue.Pools
import io.github.earthkodyai.rinalarm.dialogue.PoutFilter
import io.github.earthkodyai.rinalarm.dialogue.RinSpeaker
import io.github.earthkodyai.rinalarm.time.ElapsedClock
import io.github.earthkodyai.rinalarm.time.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Rin on the home screen's strip (task 4.2): a hello when the app opens (or comes back after
 * [GestureDirector.GREET_AFTER_MS] away, the same rule as her greeting wave, which stays her gesture for it), a line
 * when an alarm is saved, and one when the user taps her head. Her lines play on the media stream and stop when the
 * screen goes.
 */
@HiltViewModel
class HomeRinViewModel
@Inject
constructor(
  private val book: LineBook,
  voices: LineVoiceFactory,
  private val time: TimeSource,
  private val clock: ElapsedClock,
  moments: HomeMoments,
  settings: AppSettings,
) : ViewModel() {
  /** Pout off (Phase 5): her one pouty head-tap line stays unsaid. */
  @Volatile private var poutOff = false
  private val lines = PoutFilter(book) { poutOff }

  private val speaker = RinSpeaker(voices.create(alarm = false), viewModelScope)

  /** Her line on screen (the subtitle), or null. */
  val line: StateFlow<Line?> = speaker.line

  /** Her clip playing now, for her mouth. */
  val speaking: StateFlow<Speaking?>
    get() = speaker.speaking

  private val cueFlow = MutableSharedFlow<Gesture>(extraBufferCapacity = 2)
  /** Her lines' gestures, as the strip plays them. */
  val cues: SharedFlow<Gesture> = cueFlow.asSharedFlow()

  private var seen = false
  private var hiddenAt: Long? = null
  /**
   * Her face is on the strip. Coming back from the editor rebuilds the strip, and her page takes ~1.3 s to show
   * (release build on the 14T): the alarm-saved line started before it, and the user saw her mouth miss the line.
   */
  private val faceUp = MutableStateFlow(false)

  init {
    viewModelScope.launch { settings.poutOff.collect { poutOff = it } }
    viewModelScope.launch { for (saved in moments.alarmSaved) say(Pools.ALARM_SET) }
  }

  /** The screen is on (started). */
  fun onShown() {
    val away = hiddenAt?.let { clock.now() - it }
    val hello = !seen || (away != null && away >= GestureDirector.GREET_AFTER_MS)
    seen = true
    hiddenAt = null
    // Her page greets with a gesture already (GestureDirector): the hello adds only the words.
    if (hello) say(Pools.appOpened(time.now().atZone(time.zone()).toLocalTime()), gesture = false)
  }

  /** The screen went (stopped): she stops mid-line rather than talk to nobody. */
  fun onHidden() {
    hiddenAt = clock.now()
    speaker.stop()
  }

  /** Her face is up (the page is ready) or the still image is here to stay. From the strip's CharacterView. */
  fun onCharacterVisible() {
    faceUp.value = true
  }

  /** The strip left the screen with its page (the editor on top): the next line waits for the new page. */
  fun onCharacterGone() {
    faceUp.value = false
  }

  fun onHeadTap() {
    say(Pools.HEAD_TAP)
  }

  /** The home tour's last step (UX.8), before the first alarm: the one existing clip that says what the games do. */
  fun onTourGames() {
    say { book.line(TOUR_GAMES_LINE) }
  }

  private fun say(pool: String, gesture: Boolean = true) {
    say(gesture) { lines.pick(pool, time.now().atZone(time.zone()).toLocalDate(), Unit) }
  }

  private fun say(gesture: Boolean = true, pick: suspend () -> Line?) {
    viewModelScope.launch {
      val line = pick() ?: return@launch
      // The same wait as the ring screen's opening line (4.3). Her new page greets with a wave as it comes up, so a
      // line that waited for it drops its own gesture: two at once jerked her hand there.
      val waited = !faceUp.value
      if (waited) {
        withTimeoutOrNull(FACE_WAIT_MS) { faceUp.first { it } }
        delay(FACE_SETTLE_MS)
      }
      if (gesture && !waited) line.gesture?.onStrip()?.let(cueFlow::tryEmit)
      speaker.say(line)
    }
  }

  override fun onCleared() {
    speaker.release()
  }

  companion object {
    /** The longest a line waits for her page (the ring screen's RingViewModel.OPENING_WAIT_MS). */
    const val FACE_WAIT_MS = 5_000L

    /** After her page is ready, a moment for its first frames before she speaks. */
    const val FACE_SETTLE_MS = 300L

    /** "Let's play. Win, and the alarm stops." */
    const val TOUR_GAMES_LINE = "game.intro.02"
  }
}
