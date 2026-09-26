package dev.rinalarm.spike.s3

import android.content.Context
import android.content.Intent as AIntent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/** Result of one utterance. latencyMs = final text time minus the moment speech ended in the fed audio. */
data class Rec(val text: String, val latencyMs: Long, val err: String? = null, val alts: List<String> = emptyList(), val via: String = "")

interface Engine {
    val name: String
    /** Feeds [pcm] at real-time pace; [speechEnd] is the sample index where speech stops. */
    suspend fun recognize(pcm: ShortArray, speechEnd: Int): Rec
    fun close() {}
}

private const val CHUNK = RATE / 10 // 100 ms

/**
 * Android SpeechRecognizer fed from our own audio through a pipe (EXTRA_AUDIO_SOURCE, API 33+),
 * so the same recording reaches every engine. Must be called on the main thread.
 */
class AndroidEngine(
    private val ctx: Context,
    override val name: String,
    private val onDevice: Boolean,
    private val biasing: List<String>? = null,
) : Engine {
    private val sr: SpeechRecognizer =
        if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx) else SpeechRecognizer.createSpeechRecognizer(ctx)

    fun baseIntent() = AIntent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (biasing != null && Build.VERSION.SDK_INT >= 33) {
            putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(biasing))
        }
    }

    /** API 33+: which languages the service has installed on-device. */
    suspend fun support(): String {
        if (Build.VERSION.SDK_INT < 33) return "n/a (API<33)"
        val d = CompletableDeferred<String>()
        sr.checkRecognitionSupport(baseIntent(), ctx.mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(s: RecognitionSupport) = d.complete(
                "installed=${s.installedOnDeviceLanguages} pending=${s.pendingOnDeviceLanguages} " +
                    "supported=${s.supportedOnDeviceLanguages.size} online=${s.onlineLanguages.size}").let {}
            override fun onError(error: Int) = d.complete("error=$error").let {}
        })
        return withTimeoutOrNull(5000) { d.await() } ?: "timeout"
    }

    fun triggerDownload() { if (Build.VERSION.SDK_INT >= 33) sr.triggerModelDownload(baseIntent()) }

    override suspend fun recognize(pcm: ShortArray, speechEnd: Int): Rec = coroutineScope {
        val (readFd, writeFd) = ParcelFileDescriptor.createPipe()
        val done = CompletableDeferred<Rec>()
        val ended = CompletableDeferred<Unit>() // onResults/onError: the service has closed this session
        var t0 = 0L
        val latency = { SystemClock.elapsedRealtime() - (t0 + speechEnd * 1000L / RATE) }
        var lastPartial = ""
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                val list = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (DEBUG) android.util.Log.i("S3dbg", "$name onResults ${dump(b)} lastPartial=\"$lastPartial\"")
                // Google's on-device service (ASI/SODA) returns an empty bundle here with external audio;
                // the real final text came earlier as a partial flagged final_result.
                if (list.isNotEmpty()) done.complete(Rec(list.first(), latency(), alts = list.drop(1), via = "results"))
                else done.complete(Rec(lastPartial, latency(), via = "results-empty+lastPartial"))
                ended.complete(Unit)
            }
            override fun onError(error: Int) {
                // NO_MATCH / SPEECH_TIMEOUT mean "heard nothing usable": an empty transcript, not a crash.
                if (DEBUG) android.util.Log.i("S3dbg", "$name onError ${errName(error)} lastPartial=\"$lastPartial\"")
                done.complete(Rec("", latency(), err = errName(error)))
                ended.complete(Unit)
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(buf: ByteArray?) {}
            override fun onEndOfSpeech() { if (DEBUG) android.util.Log.i("S3dbg", "$name onEndOfSpeech at ${SystemClock.elapsedRealtime() - t0} ms") }
            override fun onPartialResults(b: Bundle?) {
                lastPartial = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (DEBUG) android.util.Log.i("S3dbg", "$name partial ${dump(b)}")
                if (b?.getBoolean("final_result") == true) done.complete(Rec(lastPartial, latency(), via = "final_partial"))
            }
            override fun onSegmentResults(b: Bundle) { if (DEBUG) android.util.Log.i("S3dbg", "$name segment ${dump(b)}") }
            override fun onEndOfSegmentedSession() { if (DEBUG) android.util.Log.i("S3dbg", "$name endOfSegmentedSession") }
            override fun onEvent(type: Int, b: Bundle?) { if (DEBUG) android.util.Log.i("S3dbg", "$name event $type ${dump(b)}") }
        })
        val intent = baseIntent().apply {
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readFd)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
        }
        t0 = SystemClock.elapsedRealtime()
        sr.startListening(intent)
        val writer = launch(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    var i = 0
                    while (i < pcm.size && !done.isCompleted) {
                        val end = minOf(i + CHUNK, pcm.size)
                        out.write(Audio.toBytes(pcm, i, end)); out.flush()
                        i = end
                        val wait = t0 + i * 1000L / RATE - SystemClock.elapsedRealtime()
                        if (wait > 0) delay(wait)
                    }
                }
            } catch (_: Exception) { /* reader closed: the recognizer finished early */ }
        }
        val rec = withTimeoutOrNull(pcm.size * 1000L / RATE + 8000) { done.await() }
            ?: Rec("", latency(), err = "TIMEOUT").also { sr.cancel() }
        writer.cancel()
        runCatching { writeFd.close() } // EOF, so the service ends the session
        if (withTimeoutOrNull(4000) { ended.await() } == null) sr.cancel()
        runCatching { readFd.close() }
        rec
    }

    /** Live mic, no pipe: for checking the real path works offline. Returns text + ms from onEndOfSpeech. */
    suspend fun live(): Rec {
        val done = CompletableDeferred<Rec>()
        var tEnd = 0L
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                val list = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                android.util.Log.i("S3dbg", "live onResults ${dump(b)}")
                done.complete(Rec(list.firstOrNull().orEmpty(), SystemClock.elapsedRealtime() - tEnd, alts = list.drop(1), via = "results"))
            }
            override fun onError(error: Int) = done.complete(Rec("", -1, err = errName(error))).let {}
            override fun onEndOfSpeech() { tEnd = SystemClock.elapsedRealtime() }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(buf: ByteArray?) {}
            override fun onPartialResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                android.util.Log.i("S3dbg", "live partial ${dump(b)}")
                if (b?.getBoolean("final_result") == true) done.complete(Rec(t, SystemClock.elapsedRealtime() - tEnd, via = "final_partial"))
            }
            override fun onEvent(type: Int, b: Bundle?) {}
        })
        sr.startListening(baseIntent())
        return withTimeoutOrNull(15000) { done.await() } ?: Rec("", -1, err = "TIMEOUT").also { sr.cancel() }
    }

    override fun close() = sr.destroy()

    companion object {
        var DEBUG = false
        fun dump(b: Bundle?) = b?.keySet()?.joinToString(", ", "{", "}") { k ->
            @Suppress("DEPRECATION") "$k=${b.get(k)}"
        } ?: "null"

        fun errName(e: Int) = when (e) {
            SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
            SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSIONS"
            SpeechRecognizer.ERROR_SERVER -> "SERVER"
            10 -> "TOO_MANY_REQUESTS"
            11 -> "SERVER_DISCONNECTED"
            12 -> "LANGUAGE_NOT_SUPPORTED"
            13 -> "LANGUAGE_UNAVAILABLE"
            14 -> "CANNOT_CHECK_SUPPORT"
            15 -> "CANNOT_LISTEN_TO_DOWNLOAD_EVENTS"
            else -> "ERR_$e"
        }
    }
}

/** Vosk (Kaldi) small en-US, free vocabulary or limited to [grammar] words. */
class VoskEngine(model: Model, override val name: String, grammar: List<String>? = null) : Engine {
    private val rec: Recognizer = if (grammar == null) Recognizer(model, RATE.toFloat())
    else Recognizer(model, RATE.toFloat(), org.json.JSONArray(grammar).toString())

    override suspend fun recognize(pcm: ShortArray, speechEnd: Int): Rec = withContext(Dispatchers.Default) {
        rec.reset()
        val t0 = SystemClock.elapsedRealtime()
        val speechEndAt = t0 + speechEnd * 1000L / RATE
        val parts = mutableListOf<String>()
        var tFinal = 0L
        var i = 0
        while (i < pcm.size) {
            val end = minOf(i + CHUNK, pcm.size)
            val b = Audio.toBytes(pcm, i, end)
            if (rec.acceptWaveForm(b, b.size)) {
                val t = text(rec.result)
                if (t.isNotEmpty()) { parts += t; tFinal = SystemClock.elapsedRealtime() }
            }
            i = end
            val wait = t0 + i * 1000L / RATE - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
        }
        // No endpoint fired before the audio ran out: flush. Latency then includes the whole pad.
        val tail = text(rec.finalResult)
        if (tail.isNotEmpty()) { parts += tail; tFinal = SystemClock.elapsedRealtime() }
        val latency = if (tFinal == 0L) SystemClock.elapsedRealtime() - speechEndAt else tFinal - speechEndAt
        Rec(parts.joinToString(" ").replace("[unk]", "").trim().replace(Regex("\\s+"), " "), latency)
    }

    private fun text(json: String) = JSONObject(json).optString("text")

    override fun close() = rec.close()

    companion object {
        const val ASSET_DIR = "vosk-model-small-en-us-0.15"

        /** Copies the bundled model out of the APK once (Vosk needs real files). Returns dir and ms taken (0 if cached). */
        fun unpack(ctx: Context): Pair<File, Long> {
            val dst = File(ctx.filesDir, ASSET_DIR)
            val marker = File(dst, ".complete")
            if (marker.exists()) return dst to 0L
            val t0 = SystemClock.elapsedRealtime()
            dst.deleteRecursively()
            fun copy(path: String) {
                val kids = ctx.assets.list(path).orEmpty()
                if (kids.isEmpty()) {
                    val out = File(ctx.filesDir, path); out.parentFile?.mkdirs()
                    ctx.assets.open(path).use { i -> out.outputStream().use { i.copyTo(it, 1 shl 16) } }
                } else kids.forEach { copy("$path/$it") }
            }
            copy(ASSET_DIR)
            marker.writeText("ok")
            return dst to SystemClock.elapsedRealtime() - t0
        }

        fun load(dir: File): Pair<Model, Long> {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val t0 = SystemClock.elapsedRealtime()
            val m = Model(dir.absolutePath)
            return m to SystemClock.elapsedRealtime() - t0
        }
    }
}

/** Vosk limited to the words of the replies on the current screen; the grammar changes per utterance. */
class VoskChipsEngine(private val model: Model, override val name: String) : Engine {
    var grammar: List<String> = listOf("[unk]")

    override suspend fun recognize(pcm: ShortArray, speechEnd: Int): Rec {
        val e = VoskEngine(model, name, grammar)
        try { return e.recognize(pcm, speechEnd) } finally { e.close() }
    }
}
