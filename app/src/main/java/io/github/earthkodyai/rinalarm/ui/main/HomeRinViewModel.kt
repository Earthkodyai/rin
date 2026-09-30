package io.github.earthkodyai.rinalarm.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.character.GestureDirector
import io.github.earthkodyai.rinalarm.character.Speaking
import io.github.earthkodyai.rinalarm.dialogue.HomeMoments
import io.github.earthkodyai.rinalarm.dialogue.Line
import io.github.earthkodyai.rinalarm.dialogue.LineBook
import io.github.earthkodyai.rinalarm.dialogue.LineVoiceFactory
import io.github.earthkodyai.rinalarm.dialogue.Pools
import io.github.earthkodyai.rinalarm.dialogue.RinSpeaker
import io.github.earthkodyai.rinalarm.time.ElapsedClock
import io.github.earthkodyai.rinalarm.time.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

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
  private val lines: LineBook,
  voices: LineVoiceFactory,
  private val time: TimeSource,
  private val clock: ElapsedClock,
  moments: HomeMoments,
) : ViewModel() {
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

  init {
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

  fun onHeadTap() {
    say(Pools.HEAD_TAP)
  }

  private fun say(pool: String, gesture: Boolean = true) {
    viewModelScope.launch {
      val line = lines.pick(pool, time.now().atZone(time.zone()).toLocalDate(), Unit) ?: return@launch
      if (gesture) line.gesture?.onStrip()?.let(cueFlow::tryEmit)
      speaker.say(line)
    }
  }

  override fun onCleared() {
    speaker.release()
  }
}
