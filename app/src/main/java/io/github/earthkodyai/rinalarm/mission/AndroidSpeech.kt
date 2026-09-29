package io.github.earthkodyai.rinalarm.mission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.character.Speaking
import io.github.earthkodyai.rinalarm.character.VoicePlayer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/**
 * The Vosk model bundled in the APK (assets/vosk/, fetched by Gradle's fetchVoskModel). Vosk reads real files, so it
 * is copied once into device-protected storage: a ring can come before the first unlock after a reboot, and the game
 * must be able to listen then too. Loading takes about a second (S3), so the mission starts it as the ring screen opens.
 */
@Singleton
class VoskModels @Inject constructor(@ApplicationContext private val context: Context) {
  private val lock = Mutex()
  private val dir = File(context.createDeviceProtectedStorageContext().noBackupFilesDir, "vosk/$MODEL")

  /** Whether this build carries the model at all. */
  fun bundled(): Boolean = runCatching { context.assets.list(ASSET_DIR)?.isNotEmpty() == true }.getOrDefault(false)

  /** A freshly loaded model; the caller closes it. Blocks for about a second, so never on the main thread. */
  suspend fun load(): Model =
    withContext(Dispatchers.IO) {
      lock.withLock { unpack() }
      LibVosk.setLogLevel(LogLevel.WARNINGS)
      Model(dir.path)
    }

  private fun unpack() {
    val done = File(dir, ".unpacked")
    if (done.isFile) return
    dir.deleteRecursively()
    val started = SystemClock.elapsedRealtime()
    copy(ASSET_DIR, dir)
    done.writeText(MODEL)
    Log.i(TAG, "unpacked $MODEL in ${SystemClock.elapsedRealtime() - started} ms")
  }

  private fun copy(asset: String, to: File) {
    val children = context.assets.list(asset).orEmpty()
    if (children.isEmpty()) {
      to.parentFile?.mkdirs()
      context.assets.open(asset).use { input -> to.outputStream().use { input.copyTo(it) } }
      return
    }
    to.mkdirs()
    children.forEach { copy("$asset/$it", File(to, it)) }
  }

  companion object {
    const val ASSET_DIR = "vosk"
    /** Bump with the model: a new name unpacks afresh. */
    const val MODEL = "small-en-us-0.15"
    private const val TAG = "RinSpeech"
  }
}

/**
 * [SpeechListener] on Vosk and the phone's mic (VOICE_RECOGNITION source, 16 kHz mono). One Recognizer per try, limited
 * to that try's grammar. A try ends at Vosk's end of an utterance with words in it, or after its time limit. Audio
 * never leaves this function: nothing is saved (CLAUDE.md, mic only during the game).
 */
class VoskListener(private val context: Context, private val models: VoskModels) : SpeechListener {
  private val levelState = MutableStateFlow(0f)
  override val level: StateFlow<Float> = levelState.asStateFlow()
  @Volatile private var model: Model? = null

  override suspend fun prepare() {
    if (model != null) return
    check(models.bundled()) { "no model in this build" }
    model = models.load()
  }

  override suspend fun listen(grammar: List<String>, maxMs: Long): Heard =
    withContext(Dispatchers.IO) {
      val m = checkNotNull(model) { "not prepared" }
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        throw SecurityException("RECORD_AUDIO")
      }
      val chunk = RATE / 10
      val record =
        AudioRecord(
          MediaRecorder.AudioSource.VOICE_RECOGNITION,
          RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          max(AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), chunk * 4),
        )
      val decoder = VoskDecoder(m, grammar)
      try {
        check(record.state == AudioRecord.STATE_INITIALIZED) { "mic did not initialise" }
        record.startRecording()
        check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "mic is busy" }
        val buffer = ShortArray(chunk)
        val started = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - started < maxMs) {
          ensureActive()
          val n = record.read(buffer, 0, chunk)
          if (n < 0) error("mic read failed: $n")
          if (n == 0) continue
          val done = decoder.feed(buffer, n)
          levelState.value = levelOf(decoder.lastPeak)
          if (done) break
        }
        decoder.heard().also { h ->
          Log.d(TAG, "heard ${h.words.joinToString(" ") { "${it.word}:${"%.2f".format(it.conf)}" }} unk=${h.unknown} lag=${h.lagMs} peakDb=${h.peakDb}")
        }
      } finally {
        levelState.value = 0f
        runCatching { record.stop() }
        record.release()
        decoder.close()
      }
    }

  override fun release() {
    model?.close()
    model = null
  }

  companion object {
    private const val TAG = "RinSpeech"
    const val RATE = 16_000
    private val json = Json { ignoreUnknownKeys = true }

    /** Maps a chunk's peak to 0..1 on a -50..0 dBFS scale, for the mic indicator. */
    fun levelOf(peak: Int): Float =
      if (peak <= 0) 0f else ((20 * log10(peak / 32768.0) + 50) / 50).toFloat().coerceIn(0f, 1f)

    /** The words and confidences of a Vosk result (`{"result":[{"conf":1.0,"word":"good",...}],"text":...}`). */
    fun words(result: String): List<HeardWord> =
      runCatching {
          json.parseToJsonElement(result).jsonObject["result"]?.jsonArray.orEmpty().map {
            val o = it.jsonObject
            HeardWord(o.getValue("word").jsonPrimitive.content, o["conf"]?.jsonPrimitive?.float ?: 0f)
          }
        }
        .getOrDefault(emptyList())
        .filter { it.word != "[unk]" }

    /** How many `[unk]` entries a Vosk result has. */
    fun unknowns(result: String): Int =
      runCatching {
          json.parseToJsonElement(result).jsonObject["result"]?.jsonArray.orEmpty().count {
            it.jsonObject["word"]?.jsonPrimitive?.content == "[unk]"
          }
        }
        .getOrDefault(0)

    fun lastWordEnd(result: String): Double? =
      runCatching {
          json.parseToJsonElement(result).jsonObject["result"]?.jsonArray?.lastOrNull()?.jsonObject?.get("end")
            ?.jsonPrimitive?.content?.toDouble()
        }
        .getOrNull()
  }
}

/**
 * One try's decoding, shared by the live mic ([VoskListener]) and the lab's replay of recorded tries, so both judge
 * the same way: 100 ms chunks, and the try ends at the first end of an utterance that has words in it.
 */
class VoskDecoder(model: Model, grammar: List<String>) : AutoCloseable {
  private val recognizer = Recognizer(model, VoskListener.RATE.toFloat(), JSONArray(grammar).toString()).apply { setWords(true) }
  private var result: String? = null
  private var samples = 0L
  private var peak = 0
  /** The peak of the last chunk fed, for a level meter. */
  var lastPeak = 0
    private set

  /** Feeds [n] samples; true once the try is over (an utterance with words ended). */
  fun feed(buffer: ShortArray, n: Int): Boolean {
    if (result != null) return true
    samples += n
    lastPeak = (0 until n).maxOfOrNull { abs(buffer[it].toInt()) } ?: 0
    peak = max(peak, lastPeak)
    if (recognizer.acceptWaveForm(buffer, n)) {
      val text = recognizer.result
      if (VoskListener.words(text).isNotEmpty()) result = text
    }
    return result != null
  }

  /** What the try heard; after the time limit, whatever Vosk has so far. */
  fun heard(): Heard {
    val json = result ?: recognizer.finalResult
    // Audio time of the last word's end, against the samples read: how long the user waited after speaking.
    val lag = VoskListener.lastWordEnd(json)?.let { ((samples.toDouble() / VoskListener.RATE - it) * 1000).toLong().coerceAtLeast(0) }
    return Heard(
      VoskListener.words(json),
      lagMs = lag,
      peakDb = if (peak == 0) null else (20 * log10(peak / 32768.0)).toFloat(),
      unknown = VoskListener.unknowns(json),
    )
  }

  override fun close() = recognizer.close()
}

/**
 * Rin's sentences through [VoicePlayer] on the alarm stream: at wake-up the media volume may be at zero, and the alarm
 * stream is the one RingService has raised to its floor. The clips are the debug build's (ElevenLabs free plan, S4)
 * until Phase 4's voice pack; a release build has none and shows the sentence to read instead.
 */
class AndroidRinVoice(context: Context, private val pack: String = DEV_PACK) : RinVoice {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val speakingState = MutableStateFlow<Speaking?>(null)
  override val speaking: StateFlow<Speaking?> = speakingState.asStateFlow()
  private val player = VoicePlayer(context, scope, ALARM_SPEECH) { speakingState.value = it }

  override suspend fun say(sentence: Sentence): Boolean =
    suspendCancellableCoroutine { cont ->
      player.play("$pack/${sentence.id}") { played -> if (cont.isActive) cont.resume(played) }
      // Cut off ("Hear again", the ring ended): stop her on the main thread, where the player lives.
      cont.invokeOnCancellation { scope.launch { player.stop() } }
    }

  override fun release() {
    player.stop()
    scope.cancel()
  }

  companion object {
    const val DEV_PACK = "voice/dev/repeat"
    private val ALARM_SPEECH: AudioAttributes =
      AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
  }
}
