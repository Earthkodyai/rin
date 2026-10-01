package io.github.earthkodyai.rinalarm.dialogue

import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.character.RinMouth
import io.github.earthkodyai.rinalarm.character.Speaking
import io.github.earthkodyai.rinalarm.character.VoicePlayer
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock

/** Rin's recorded clips for her [Line]s (the voice pack of task 4.3). */
interface LineVoice {
  /** The clip playing now, for her mouth on the character page. */
  val speaking: StateFlow<Speaking?>

  fun hasClip(line: Line): Boolean

  /** Plays the line's clip and returns once it ends: true when it played to the end. */
  suspend fun play(line: Line): Boolean

  fun release()
}

/** [alarm]: on the alarm stream (the ring screen), or the media stream (the rest of the app). */
fun interface LineVoiceFactory {
  fun create(alarm: Boolean): LineVoice
}

class AndroidLineVoiceFactory @Inject constructor(@ApplicationContext private val context: Context) : LineVoiceFactory {
  override fun create(alarm: Boolean): LineVoice =
    AndroidLineVoice(context, if (alarm) VoicePlayer.ALARM_SPEECH else VoicePlayer.SPEECH)
}

/**
 * Rin's line clips, `voice/rin/<id>.mp3` in assets (with an optional `<id>.mouth.json`), through [VoicePlayer]. Until
 * 4.3 records the pack no line has one, and RinSpeaker shows each line as a subtitle alone. One voice at a time
 * ([RinMouth]): a game's sentence waits for her line, and a line for the sentence. Main thread only.
 */
class AndroidLineVoice(private val context: Context, attributes: AudioAttributes, private val pack: String = PACK) :
  LineVoice {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val speakingState = MutableStateFlow<Speaking?>(null)
  override val speaking: StateFlow<Speaking?> = speakingState.asStateFlow()
  private val player = VoicePlayer(context, scope, attributes) { speakingState.value = it }
  private val clips: Set<String> by lazy {
    runCatching { context.assets.list(pack)?.toSet() }.getOrNull().orEmpty()
  }

  init {
    // Lists the pack off the main thread before the first line asks.
    scope.launch(Dispatchers.IO) { clips }
  }

  // RinSpeaker asks once per line as she starts it: the log shows every line she says (tag RinLines).
  override fun hasClip(line: Line): Boolean {
    val name = "${line.id}.mp3"
    val listed = name in clips
    // On 2026-10-01 two lines in one ring logged clip=false although the APK carried both clips (the next ring played
    // them). Until the cause is known, a clip missing from the list is opened directly before she goes silent, and
    // the log says so.
    val found = listed || runCatching { context.assets.open("$pack/$name").close() }.isSuccess
    if (found && !listed) Log.w(TAG, "${line.id}: not in the listed ${clips.size} files, but it opens")
    Log.i(TAG, "say ${line.id} clip=$found")
    return found
  }

  override suspend fun play(line: Line): Boolean =
    RinMouth.turn.withLock {
      suspendCancellableCoroutine { cont ->
        player.play("$pack/${line.id}") { played -> if (cont.isActive) cont.resume(played) }
        // Cut off (a newer line, the screen closing): stop her on the main thread, where the player lives.
        cont.invokeOnCancellation { scope.launch { player.stop() } }
      }
    }

  override fun release() {
    player.stop()
    scope.cancel()
  }

  companion object {
    const val PACK = "voice/rin"
    private const val TAG = "RinLines"
  }
}
