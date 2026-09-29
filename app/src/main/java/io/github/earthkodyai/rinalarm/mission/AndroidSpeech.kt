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

  override suspend fun listen(grammar: List<String>, maxMs: Long, enough: (Heard) -> Boolean): Heard =
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
      val decoder = VoskDecoder(m, grammar, enough)
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
 * the same way. Fed 100 ms chunks. Words are collected across Vosk's utterances, because a pause inside a sentence
 * ends an utterance (dev d04: "The sun is up, || and so am I" lost its second half when the first utterance ended
 * the try). The try is over as soon as the words so far are [enough], or [QUIET_MS] after an utterance with words
 * ended with no new speech since; the caller's time limit covers the rest.
 */
class VoskDecoder(private val recognizer: Recognizing, private val enough: (Heard) -> Boolean = { false }) : AutoCloseable {
  constructor(model: Model, grammar: List<String>, enough: (Heard) -> Boolean = { false }) : this(VoskRecognizing(model, grammar), enough)
  private val words = mutableListOf<HeardWord>()
  private var unknown = 0
  private var lastEnd: Double? = null
  /** Sample count when the last utterance with words ended, while no new speech has started; null otherwise. */
  private var quietFrom: Long? = null
  private var done = false
  private var samples = 0L
  private var peak = 0
  /** The peak of the last chunk fed, for a level meter. */
  var lastPeak = 0
    private set

  /** Feeds [n] samples; true once the try is over. */
  fun feed(buffer: ShortArray, n: Int): Boolean {
    if (done) return true
    samples += n
    lastPeak = (0 until n).maxOfOrNull { abs(buffer[it].toInt()) } ?: 0
    peak = max(peak, lastPeak)
    if (recognizer.accept(buffer, n)) {
      if (take(recognizer.result())) {
        quietFrom = samples
        if (enough(snapshot())) done = true
      }
    } else if (quietFrom != null && partialText(recognizer.partial()).isNotEmpty()) {
      quietFrom = null // speaking again
    }
    quietFrom?.let { if (samples - it >= QUIET_MS * VoskListener.RATE / 1000) done = true }
    return done
  }

  /** What the try heard; after the time limit, including whatever Vosk has not closed yet. Ends the try. */
  fun heard(): Heard {
    if (!done) {
      take(recognizer.final())
      done = true
    }
    return snapshot()
  }

  /** The words so far, without ending the try. */
  private fun snapshot(): Heard {
    // Audio time of the last word's end, against the samples read: how long the user waited after speaking.
    val lag = lastEnd?.let { ((samples.toDouble() / VoskListener.RATE - it) * 1000).toLong().coerceAtLeast(0) }
    return Heard(
      words.toList(),
      lagMs = lag,
      peakDb = if (peak == 0) null else (20 * log10(peak / 32768.0)).toFloat(),
      unknown = unknown,
    )
  }

  /** Adds one utterance's result; true when it had words. */
  private fun take(json: String): Boolean {
    val got = VoskListener.words(json)
    unknown += VoskListener.unknowns(json)
    if (got.isEmpty()) return false
    words += got
    lastEnd = VoskListener.lastWordEnd(json) ?: lastEnd
    return true
  }

  private fun partialText(json: String): String =
    runCatching { Json.parseToJsonElement(json).jsonObject["partial"]?.jsonPrimitive?.content.orEmpty() }
      .getOrDefault("")
      .replace("[unk]", "")
      .trim()

  override fun close() = recognizer.close()

  companion object {
    /** Silence after an utterance before a try that has not passed ends (on top of Vosk's own ~0.5 s endpoint). */
    const val QUIET_MS = 1_000L
  }
}

/** The part of Vosk's Recognizer that [VoskDecoder] uses (results as Vosk's JSON), so its logic is unit-tested. */
interface Recognizing : AutoCloseable {
  /** True when an utterance ended; [result] then has it. */
  fun accept(buffer: ShortArray, n: Int): Boolean

  fun result(): String

  fun partial(): String

  fun final(): String
}

private class VoskRecognizing(model: Model, grammar: List<String>) : Recognizing {
  private val recognizer = Recognizer(model, VoskListener.RATE.toFloat(), JSONArray(grammar).toString()).apply { setWords(true) }

  override fun accept(buffer: ShortArray, n: Int) = recognizer.acceptWaveForm(buffer, n)

  override fun result(): String = recognizer.result

  override fun partial(): String = recognizer.partialResult

  override fun final(): String = recognizer.finalResult

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
