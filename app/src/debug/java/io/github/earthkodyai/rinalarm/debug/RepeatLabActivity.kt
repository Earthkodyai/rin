package io.github.earthkodyai.rinalarm.debug

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.earthkodyai.rinalarm.mission.MatchRules
import io.github.earthkodyai.rinalarm.mission.RepeatMatcher
import io.github.earthkodyai.rinalarm.mission.RepeatSentences
import io.github.earthkodyai.rinalarm.mission.VoskDecoder
import io.github.earthkodyai.rinalarm.mission.VoskListener
import io.github.earthkodyai.rinalarm.mission.VoskModels
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repeat after Rin measurement (task 3.5, docs/spikes/3.5-repeat-after-rin.md). Two modes:
 * - record: `am start -n <pkg>/.debug.RepeatLabActivity --es set dev` walks the frozen protocol (assets
 *   repeat-lab/<set>.json) one try at a time and saves each as a 16 kHz mono WAV in the app's external files
 *   (repeat-lab/<set>/<id>.wav). Recordings stay on the phone and in a git-ignored folder on the PC.
 * - replay: `--es replay dev --es label <name> [--ez decoys false]` runs every WAV of the set through VoskDecoder, the
 *   game's own decoding, and writes repeat-lab/<set>/results-<label>.jsonl (the words and confidences heard) for
 *   RepeatRescoreTest on the PC.
 */
class RepeatLabActivity : ComponentActivity() {
  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface Deps {
    fun vosk(): VoskModels
  }

  @Serializable data class Item(val id: String, val kind: String, val target: String, val cond: String, val read: String)

  @Serializable private data class Protocol(val items: List<Item>)

  private val json = Json { ignoreUnknownKeys = true }
  private var index by mutableIntStateOf(0)
  private var status by mutableStateOf("")
  private var recording by mutableStateOf(false)
  private var job: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val replay = intent.getStringExtra("replay")
    val set = replay ?: intent.getStringExtra("set") ?: "dev"
    val items = json.decodeFromString<Protocol>(assets.open("repeat-lab/$set.json").use { it.reader().readText() }).items
    val dir = File(getExternalFilesDir(null), "repeat-lab/$set").apply { mkdirs() }
    if (replay != null) {
      replay(items, dir, intent.getStringExtra("label") ?: "run", intent.getBooleanExtra("decoys", true))
    } else {
      index = items.indexOfFirst { !File(dir, "${it.id}.wav").isFile }.let { if (it < 0) items.size else it }
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.RECORD_AUDIO)
      }
    }
    setContent {
      RinAlarmTheme {
        Surface(Modifier.fillMaxSize()) {
          Column(
            Modifier.safeDrawingPadding().padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            if (replay != null) {
              Text("Replay '$set'", style = MaterialTheme.typography.titleLarge)
              Text(status, style = MaterialTheme.typography.bodyLarge)
              return@Column
            }
            if (index >= items.size) {
              Text("All ${items.size} tries of '$set' recorded. Thank you!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
              OutlinedButton(onClick = { index = items.size - 1 }) { Text("Back to the last one") }
              return@Column
            }
            val item = items[index]
            val saved = File(dir, "${item.id}.wav").isFile
            Text("${index + 1} / ${items.size}   (${item.id})", style = MaterialTheme.typography.titleMedium)
            Text(COND[item.cond] ?: item.cond, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
            Text(KIND[item.kind] ?: item.kind, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (item.read.isNotEmpty()) {
              Text("“${item.read}”", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))
            Text(status, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Button(
              onClick = { record(item, File(dir, "${item.id}.wav")) },
              enabled = !recording,
              modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
              Text(if (saved) "Redo (${seconds(item)} s)" else "Record (${seconds(item)} s)", style = MaterialTheme.typography.titleLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              OutlinedButton(onClick = { index--; status = "" }, enabled = !recording && index > 0, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("Back")
              }
              // Next only once this try is saved, so none is skipped by a slip.
              Button(onClick = { index++; status = "" }, enabled = !recording && saved, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("Next")
              }
            }
          }
        }
      }
    }
  }

  private fun seconds(item: Item) = if (item.cond == "far") FAR_SECONDS else SECONDS

  private fun record(item: Item, file: File) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      status = "Allow the microphone first (reopen this screen)."
      return
    }
    recording = true
    job =
      lifecycleScope.launch {
        val seconds = seconds(item)
        val pcm =
          withContext(Dispatchers.IO) {
            val rate = VoskListener.RATE
            val record =
              AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, rate)
            val out = ShortArray(rate * seconds)
            try {
              record.startRecording()
              var n = 0
              val started = SystemClock.elapsedRealtime()
              while (n < out.size) {
                val got = record.read(out, n, minOf(rate / 10, out.size - n))
                if (got < 0) break
                n += got
                val left = seconds - (SystemClock.elapsedRealtime() - started) / 1000
                withContext(Dispatchers.Main) { status = "Recording… $left s" }
              }
              out.copyOf(n)
            } finally {
              runCatching { record.stop() }
              record.release()
            }
          }
        withContext(Dispatchers.IO) { writeWav(file, pcm) }
        val peak = pcm.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
        status = "Saved ${"%.1f".format(pcm.size / VoskListener.RATE.toFloat())} s (peak ${peak * 100 / 32768}%). Tap Next."
        recording = false
      }
  }

  private fun replay(items: List<Item>, dir: File, label: String, decoys: Boolean) {
    val rules = MatchRules(decoys = decoys)
    val pool = RepeatSentences.parse(assets.open(RepeatSentences.ASSET).use { it.reader().readText() }).associateBy { it.id }
    lifecycleScope.launch {
      val out = File(dir, "results-$label.jsonl")
      val lines = mutableListOf<String>()
      val model = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).vosk().load()
      var accepted = 0
      try {
        for ((n, item) in items.withIndex()) {
          val wav = File(dir, "${item.id}.wav")
          if (!wav.isFile) continue
          val sentence = pool.getValue(item.target)
          val started = SystemClock.elapsedRealtime()
          val heard =
            withContext(Dispatchers.Default) {
              val pcm = readWav(wav)
              VoskDecoder(model, RepeatMatcher.grammar(sentence, rules)).use { d ->
                val chunk = VoskListener.RATE / 10
                var i = 0
                while (i < pcm.size && !d.feed(pcm.copyOfRange(i, minOf(i + chunk, pcm.size)), minOf(chunk, pcm.size - i))) i += chunk
                d.heard()
              }
            }
          val ms = SystemClock.elapsedRealtime() - started
          val match = RepeatMatcher.match(sentence, heard, rules)
          if (match.accepted) accepted++
          lines +=
            buildJsonObject {
                put("id", item.id)
                put("kind", item.kind)
                put("target", item.target)
                put("cond", item.cond)
                put("decoys", decoys)
                put("words", buildJsonArray { heard.words.forEach { w -> add(buildJsonObject { put("w", w.word); put("c", w.conf) }) } })
                heard.peakDb?.let { put("peakDb", it) }
                put("decodeMs", ms)
              }
              .toString()
          status = "${n + 1}/${items.size}: ${item.id} ${item.kind} -> ${heard.words.joinToString(" ") { it.word }}"
          Log.i(TAG, "${item.id} ${item.kind} ${match.matched}/${match.of} ${if (match.accepted) "ACCEPT" else "reject"} ${ms}ms")
        }
      } finally {
        model.close()
      }
      withContext(Dispatchers.IO) { out.writeText(lines.joinToString("\n", postfix = "\n")) }
      status = "Done: ${lines.size} tries, $accepted accepted with the default rules. Wrote ${out.name}."
      Log.i(TAG, "replay done $label: ${lines.size} tries -> ${out.path}")
    }
  }

  private fun writeWav(file: File, pcm: ShortArray) {
    val rate = VoskListener.RATE
    RandomAccessFile(file, "rw").use { f ->
      f.setLength(0)
      val data = pcm.size * 2
      fun int(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
      fun short(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
      f.writeBytes("RIFF"); int(36 + data); f.writeBytes("WAVE")
      f.writeBytes("fmt "); int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
      f.writeBytes("data"); int(data)
      val bytes = ByteArray(data)
      pcm.forEachIndexed { i, s ->
        bytes[2 * i] = s.toInt().toByte()
        bytes[2 * i + 1] = (s.toInt() shr 8).toByte()
      }
      f.write(bytes)
    }
  }

  /** Reads this lab's own WAVs (16-bit mono, 44-byte header). */
  private fun readWav(file: File): ShortArray {
    val bytes = DataInputStream(file.inputStream()).use { it.readBytes() }
    val n = (bytes.size - 44) / 2
    return ShortArray(n) { i -> ((bytes[44 + 2 * i].toInt() and 0xff) or (bytes[45 + 2 * i].toInt() shl 8)).toShort() }
  }

  private companion object {
    const val TAG = "RepeatLab"
    const val SECONDS = 6
    /** The far tries: time to put the phone down on the bedside first. */
    const val FAR_SECONDS = 9
    val COND =
      mapOf(
        "hand" to "Phone in your hand, normal voice",
        "lying" to "Lie down: sleepy voice, as if you just woke up",
        "far" to "Tap Record, put the phone on the bedside (about 1 m away), then speak",
      )
    val KIND =
      mapOf(
        "say" to "Say this sentence:",
        "mumble" to "Mumble sleepily for about 2 seconds. No real words.",
        "other" to "Say this sentence (it is not the one Rin expects, on purpose):",
        "partial" to "Say only this much, then stop:",
      )
  }
}
