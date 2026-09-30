package io.github.earthkodyai.rinalarm.dialogue

import io.github.earthkodyai.rinalarm.character.Speaking
import io.github.earthkodyai.rinalarm.mission.Hush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Rin saying her lines (task 4.2). Each shows as a subtitle ([line]) while she says it: in her voice when the build has
 * the line's clip (the 4.3 pack), and as text alone until then. While a clip plays, [hush] asks the ring tone to step
 * back (QUIET, as the games do while she speaks); a subtitle alone leaves the tone as it is. Main thread only.
 */
class RinSpeaker(private val voice: LineVoice, private val scope: CoroutineScope) {
  private val lineState = MutableStateFlow<Line?>(null)
  /** The line on screen, or null. */
  val line: StateFlow<Line?> = lineState.asStateFlow()

  private val hushState = MutableStateFlow(Hush.NONE)
  val hush: StateFlow<Hush> = hushState.asStateFlow()

  /** Her mouth: the clip playing now. */
  val speaking: StateFlow<Speaking?>
    get() = voice.speaking

  /** The newest line's job; [queued] also holds the ones it waits for, so [stop] ends them all. */
  private var current: Job? = null
  private val queued = mutableListOf<Job>()

  /**
   * Says [line]; the job is done once she has finished: at the clip's end plus [TAIL_MS], or after [readingMs] for a
   * subtitle alone. [cut] stops the line in progress at once; otherwise [line] waits for it to finish.
   */
  fun say(line: Line, cut: Boolean = true): Job {
    val before = current
    if (cut) queued.toList().forEach(Job::cancel)
    val job =
      scope.launch {
        if (!cut) before?.join()
        val clip = voice.hasClip(line)
        lineState.value = line
        hushState.value = if (clip) Hush.QUIET else Hush.NONE
        try {
          if (clip) {
            voice.play(line)
            hushState.value = Hush.NONE
            delay(TAIL_MS)
          } else {
            delay(readingMs(line))
          }
        } finally {
          // A newer line may already be on (it cut this one off): only clear what is still this line's own.
          if (current === coroutineContext[Job]) {
            lineState.value = null
            hushState.value = Hush.NONE
          }
        }
      }
    current = job
    queued += job
    job.invokeOnCompletion { queued -= job }
    return job
  }

  /** Waits for the line in progress, if any, to finish. */
  suspend fun finish() {
    current?.join()
  }

  fun stop() {
    queued.toList().forEach(Job::cancel)
    current = null
    lineState.value = null
    hushState.value = Hush.NONE
  }

  fun release() {
    stop()
    voice.release()
  }

  companion object {
    /** A beat after her voice ends before the subtitle goes. */
    const val TAIL_MS = 300L

    /** How long a subtitle alone stays up: a moment to look, then about 18 characters a second. */
    fun readingMs(line: Line): Long = 600 + 55L * line.text.length
  }
}
