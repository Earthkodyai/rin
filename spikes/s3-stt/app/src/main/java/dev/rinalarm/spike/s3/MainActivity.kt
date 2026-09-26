package dev.rinalarm.spike.s3

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S3 spike: record the tester's answers once, then replay the same WAVs into each STT engine
 * and score intent accuracy + latency. Headless replay:
 *   am start -n dev.rinalarm.spike.s3/.MainActivity --es mode replay --es set smoke --es engines all
 */
class MainActivity : Activity() {
    private val scope = MainScope()
    private lateinit var root: LinearLayout
    private lateinit var log: TextView
    private var job: Job? = null

    private val engineNames = listOf("android-od", "android-od-bias", "android-default", "vosk-free", "vosk-grammar")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Create the folders ourselves: dirs that adb creates are shell-owned and invisible to the app.
        listOf("me", "smoke", "sugg").forEach { setDir(it).mkdirs() }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        showHome()
        if (intent.getStringExtra("mode") == "download") job = scope.launch { checkSupport(download = true) }
        if (intent.getStringExtra("mode") == "replay") {
            val set = intent.getStringExtra("set") ?: "me"
            val eng = intent.getStringExtra("engines")?.takeIf { it != "all" }?.split(',') ?: engineNames
            job = scope.launch { replay(set, eng) }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    // ---------- UI helpers ----------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun screen(): LinearLayout {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(32), dp(16), dp(16)) }
        setContentView(ScrollView(this).apply { addView(root) })
        return root
    }

    private fun text(s: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(6))
    }.also { root.addView(it) }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; setOnClickListener { onClick() }
    }.also { root.addView(it) }

    private fun say(s: String) {
        Log.i(TAG, s)
        runOnUiThread { if (::log.isInitialized) log.append(s + "\n") }
    }

    private fun setDir(name: String) = File(getExternalFilesDir(null), "sets/$name")

    private fun showHome() {
        screen()
        text("S3: offline English STT", 22f, true)
        val n = loadManifest("me").size
        text("Your recordings: $n / ${Prompts.ALL.size}")
        button("Record my answers") { showRecord(0.coerceAtLeast(firstMissing())) }
        button("Replay my answers into all engines") { job = scope.launch { replay("me", engineNames) } }
        button("Replay smoke set (TTS voices)") { job = scope.launch { replay("smoke", engineNames) } }
        button("Live test: Android on-device (mic)") { job = scope.launch { liveTest() } }
        text("Suggested replies (set 2): ${loadManifest("sugg").size} / ${Suggest.ALL.size}", 15f, true)
        button("Record suggested replies") { showSugg(Suggest.ALL.indexOfFirst { it.id !in loadManifest("sugg") }.coerceAtLeast(0)) }
        button("Live test: suggested replies (mic, 8 screens)") { showLiveSugg(0, 0) }
        button("Check / download Android en-US offline model") { job = scope.launch { checkSupport(download = true) } }
        log = text("", 12f).apply { typeface = android.graphics.Typeface.MONOSPACE }
        say("network=${network()} sdk=${Build.VERSION.SDK_INT} onDeviceAvailable=${onDeviceAvailable()}")
    }

    private fun onDeviceAvailable() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)

    private fun network(): String {
        val cm = getSystemService(ConnectivityManager::class.java)
        return if (cm.activeNetwork == null) "OFFLINE" else "ONLINE"
    }

    // ---------- Recording ----------

    private fun loadManifest(set: String): MutableMap<String, JSONObject> {
        val f = File(setDir(set), "manifest.jsonl")
        if (!f.exists()) return linkedMapOf()
        return f.readLines().filter { it.isNotBlank() }.map { JSONObject(it) }.associateByTo(linkedMapOf()) { it.getString("id") }
    }

    private fun saveManifest(set: String, m: Map<String, JSONObject>) =
        File(setDir(set), "manifest.jsonl").writeText(m.values.joinToString("\n") { it.toString() } + "\n")

    private fun firstMissing(): Int {
        val m = loadManifest("me")
        return Prompts.ALL.indexOfFirst { it.id !in m }.let { if (it < 0) 0 else it }
    }

    private var recording: Job? = null
    @Volatile private var stopRequested = false

    private fun showRecord(index: Int) {
        val p = Prompts.ALL[index]
        val have = loadManifest("me")
        screen()
        text("${index + 1} / ${Prompts.ALL.size}   ·   ${Prompts.COND[p.cond]}", 14f)
        text("Rin: “${p.question}”", 24f, true)
        text("Your answer: ${Prompts.TASK[p.intent]}", 20f).setTextColor(Color.rgb(0x1a, 0x73, 0xe8))
        text("Use your own words, vary them, answer the way you would half-asleep.", 13f)
        val status = text(if (p.id in have) "Recorded (${have[p.id]!!.optInt("durMs")} ms). Redo or go next." else "Tap to speak. It stops by itself after you finish.", 14f)
        lateinit var rec: Button
        rec = button("🎤  Tap to speak") {
            if (recording?.isActive == true) { stopRequested = true; return@button }
            stopRequested = false
            rec.text = "■  Listening… (tap to stop)"; rec.setBackgroundColor(Color.rgb(0xd9, 0x30, 0x25))
            recording = scope.launch {
                val pcm = withContext(Dispatchers.IO) { recordUtterance() }
                val bounds = Audio.speechBounds(pcm)
                val peak = Audio.peakDb(pcm)
                val file = File(setDir("me"), "${p.id}.wav")
                withContext(Dispatchers.IO) { Audio.writeWav(file, pcm) }
                val m = loadManifest("me")
                m[p.id] = JSONObject().put("id", p.id).put("intent", p.intent.name).put("cond", p.cond)
                    .put("question", p.question).put("file", file.name).put("durMs", pcm.size * 1000 / RATE)
                    .put("peakDb", "%.1f".format(peak).toDouble()).put("ts", System.currentTimeMillis())
                saveManifest("me", m)
                val warn = when {
                    bounds == null -> "  ⚠ No speech detected. Please redo."
                    peak > -0.5 -> "  ⚠ Clipped (too loud)."
                    else -> ""
                }
                status.text = "Saved ${pcm.size * 1000 / RATE} ms, peak ${"%.0f".format(peak)} dBFS.$warn"
                rec.text = "🎤  Redo"; rec.setBackgroundColor(Color.LTGRAY)
                if (warn.isEmpty() && index + 1 < Prompts.ALL.size) {
                    kotlinx.coroutines.delay(700); showRecord(index + 1)
                }
            }
        }
        rec.minHeight = dp(96)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        root.addView(row)
        fun nav(s: String, to: Int) = Button(this).apply {
            text = s; isAllCaps = false; isEnabled = to in Prompts.ALL.indices
            setOnClickListener { recording?.cancel(); showRecord(to) }
        }.also { row.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        nav("◀ Back", index - 1); nav("Next ▶", index + 1)
        button("Home") { recording?.cancel(); showHome() }
        if (p.cond != Prompts.ALL.getOrNull(index - 1)?.cond) {
            status.text = "New position: ${Prompts.COND[p.cond]}.\n" + status.text
        }
    }

    /** Records until 1.5 s of silence after speech (or 10 s, or tap-to-stop via cancellation). */
    @SuppressLint("MissingPermission")
    private suspend fun recordUtterance(): ShortArray = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val ar = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE))
        val out = ArrayList<Short>(RATE * 10)
        val frame = ShortArray(FRAME)
        var floor = Double.NaN; var voicedRun = 0; var heard = false; var silentMs = 0
        ar.startRecording()
        try {
            while (isActive && !stopRequested && out.size < RATE * 10) {
                val n = ar.read(frame, 0, FRAME)
                if (n <= 0) continue
                for (i in 0 until n) out.add(frame[i])
                val db = Audio.frameDb(frame, 0, n)
                if (out.size <= RATE * 3 / 10) { floor = if (floor.isNaN()) db else minOf(floor, db); continue }
                val voiced = db > maxOf(floor + 12, -55.0)
                voicedRun = if (voiced) voicedRun + 1 else 0
                if (voicedRun >= 3) heard = true
                silentMs = if (voiced) 0 else silentMs + 20
                if (heard && silentMs >= 1500) break
            }
        } finally { ar.stop(); ar.release() }
        out.toShortArray()
    }

    // ---------- Replay ----------

    private suspend fun checkSupport(download: Boolean) {
        if (!onDeviceAvailable()) { say("on-device recognizer NOT available"); return }
        val e = AndroidEngine(this, "android-od", onDevice = true)
        val s = e.support()
        say("android on-device support: $s")
        val installed = { t: String -> t.substringAfter("installed=").substringBefore(']').contains("en-US") }
        if (download && !installed(s)) {
            say("triggering en-US model download (needs network)…"); e.triggerDownload()
            // Poll for up to 5 min; the pack is a few tens of MB.
            for (i in 1..60) {
                kotlinx.coroutines.delay(5000)
                val now = e.support(); say("[$i] $now")
                if (installed(now)) { say("en-US on-device model INSTALLED"); break }
            }
        }
        e.close()
    }

    private suspend fun replay(set: String, names: List<String>) {
        val items = loadManifest(set).values.toList()
        if (items.isEmpty()) { say("set '$set' is empty (${setDir(set)})"); return }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val outFile = File(getExternalFilesDir(null), "results/$set-$stamp.jsonl").apply { parentFile?.mkdirs() }
        fun emit(o: JSONObject) { outFile.appendText("$o\n"); Log.i(TAG, o.toString()) }
        emit(JSONObject().put("phase", "header").put("set", set).put("n", items.size).put("network", network())
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("sdk", Build.VERSION.SDK_INT)
            .put("onDeviceAvailable", onDeviceAvailable()).put("engines", names.joinToString(",")))
        var model: org.vosk.Model? = null
        for (fullName in names) {
            // Audio-side ablations ride on the engine name: "android-od+lead+norm".
            val name = fullName.substringBefore('+')
            val lead = "+lead" in fullName; val norm = "+norm" in fullName
            val engine: Engine = try {
                when (name) {
                    "android-od" -> AndroidEngine(this, name, onDevice = true)
                    "android-od-bias" -> AndroidEngine(this, name, onDevice = true, biasing = IntentMatcher.cuePhrases())
                    "android-default" -> AndroidEngine(this, name, onDevice = false)
                    "vosk-free", "vosk-grammar", "vosk-chips" -> {
                        if (model == null) {
                            val (dir, unpackMs) = withContext(Dispatchers.IO) { VoskEngine.unpack(this@MainActivity) }
                            val (m, loadMs) = withContext(Dispatchers.IO) { VoskEngine.load(dir) }
                            model = m
                            emit(JSONObject().put("phase", "vosk-model").put("unpackMs", unpackMs).put("loadMs", loadMs))
                        }
                        if (name == "vosk-chips") VoskChipsEngine(model!!, name)
                        else VoskEngine(model!!, name, if (name == "vosk-grammar") IntentMatcher.voskVocabulary() else null)
                    }
                    else -> { say("unknown engine $name"); continue }
                }
            } catch (t: Throwable) {
                emit(JSONObject().put("phase", "engine-error").put("engine", name).put("error", t.toString())); continue
            }
            // Probe on the same instance: destroying a separate probe drops the shared service connection.
            if (engine is AndroidEngine) emit(JSONObject().put("phase", "support").put("engine", name).put("support", engine.support()))
            var ok = 0
            items.forEachIndexed { k, it ->
                val pcm0 = Audio.readWav(File(setDir(set), it.getString("file")))
                var pcm = pcm0
                if (norm) pcm = Audio.normalize(pcm, -3.0)
                if (lead) pcm = ShortArray(RATE) + pcm // 1 s of silence before the answer
                val (_, speechEnd) = Audio.speechBounds(pcm) ?: (0 to pcm.size)
                // Pad so every engine sees >= 2.5 s after speech ends, room for its own endpointer.
                val padded = pcm.copyOf(maxOf(pcm.size, speechEnd + RATE * 5 / 2))
                val chips = it.optJSONArray("chips")?.let { a -> List(a.length()) { i -> a.getString(i) } }
                if (engine is VoskChipsEngine) engine.grammar = ChipMatcher.grammar(chips ?: emptyList())
                val r = engine.recognize(padded, speechEnd)
                // Suggested-reply items score "which chip (or none)"; open answers score the intent.
                val (pred, expected) = if (chips != null) {
                    "chip${ChipMatcher.match(r.text, chips) ?: -1}" to "chip${it.getInt("target")}"
                } else IntentMatcher.classify(r.text).name to it.getString("intent")
                if (pred == expected) ok++
                emit(JSONObject().put("phase", "utt").put("set", set).put("engine", fullName).put("id", it.getString("id"))
                    .put("intent", expected).put("cond", it.optString("cond")).put("text", r.text)
                    .put("alts", org.json.JSONArray(r.alts)).put("pred", pred).put("ok", pred == expected)
                    .put("speechEndMs", speechEnd * 1000L / RATE).put("latencyMs", r.latencyMs).put("err", r.err ?: "").put("via", r.via))
                say("[$fullName ${k + 1}/${items.size}] ${if (pred == expected) "✓" else "✗"} ${it.getString("id")}: \"${r.text}\" → $pred (${r.latencyMs} ms)")
            }
            emit(JSONObject().put("phase", "engine-done").put("engine", fullName).put("ok", ok).put("n", items.size))
            say("== $fullName: $ok/${items.size} = ${"%.1f".format(100.0 * ok / items.size)}%")
            engine.close()
            kotlinx.coroutines.delay(1500) // let the recognition service unbind before the next engine binds
        }
        model?.close()
        emit(JSONObject().put("phase", "done").put("file", outFile.name))
    }

    // ---------- Suggested replies ----------

    private fun chipLines(chips: List<String>, target: Int) = chips.forEachIndexed { i, c ->
        text((if (i == target) "▶  " else "     ") + c, 20f, i == target)
            .setTextColor(if (i == target) Color.rgb(0x1a, 0x73, 0xe8) else Color.GRAY)
    }

    private fun showSugg(index: Int) {
        val p = Suggest.ALL[index]
        val have = loadManifest("sugg")
        screen()
        text("${index + 1} / ${Suggest.ALL.size}   ·   ${Prompts.COND[p.cond]}", 14f)
        text("Rin: “${p.question}”", 24f, true)
        chipLines(p.chips, p.target)
        text(if (p.target >= 0) "Say the highlighted reply, as written." else "Say something that is NOT on the screen.", 16f, true)
        val status = text(if (p.id in have) "Recorded. Redo or go next." else "Tap to speak. It stops by itself.", 14f)
        if (p.cond != Suggest.ALL.getOrNull(index - 1)?.cond) status.text = "New position: ${Prompts.COND[p.cond]}.\n" + status.text
        lateinit var rec: Button
        rec = button("🎤  Tap to speak") {
            if (recording?.isActive == true) { stopRequested = true; return@button }
            stopRequested = false
            rec.text = "■  Listening… (tap to stop)"; rec.setBackgroundColor(Color.rgb(0xd9, 0x30, 0x25))
            recording = scope.launch {
                val pcm = withContext(Dispatchers.IO) { recordUtterance() }
                val file = File(setDir("sugg"), "${p.id}.wav")
                withContext(Dispatchers.IO) { Audio.writeWav(file, pcm) }
                val m = loadManifest("sugg")
                m[p.id] = JSONObject().put("id", p.id).put("intent", if (p.target >= 0) "CHIP" else "OFFLIST")
                    .put("chips", org.json.JSONArray(p.chips)).put("target", p.target).put("cond", p.cond)
                    .put("question", p.question).put("file", file.name).put("durMs", pcm.size * 1000 / RATE)
                    .put("peakDb", "%.1f".format(Audio.peakDb(pcm)).toDouble()).put("ts", System.currentTimeMillis())
                saveManifest("sugg", m)
                val heard = Audio.speechBounds(pcm) != null
                status.text = if (heard) "Saved ${pcm.size * 1000 / RATE} ms." else "⚠ No speech detected. Please redo."
                rec.text = "🎤  Redo"; rec.setBackgroundColor(Color.LTGRAY)
                if (heard && index + 1 < Suggest.ALL.size) { kotlinx.coroutines.delay(700); showSugg(index + 1) }
            }
        }
        rec.minHeight = dp(96)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        root.addView(row)
        fun nav(s: String, to: Int) = Button(this).apply {
            text = s; isAllCaps = false; isEnabled = to in Suggest.ALL.indices
            setOnClickListener { recording?.cancel(); showSugg(to) }
        }.also { row.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        nav("◀ Back", index - 1); nav("Next ▶", index + 1)
        button("Home") { recording?.cancel(); showHome() }
    }

    /** The real app path: Google's on-device recognizer on the live mic, one screen each (8 screens). */
    private fun showLiveSugg(i: Int, score: Int) {
        val screens = Suggest.ALL.filter { it.target >= 0 }.distinctBy { it.question }
        if (i >= screens.size) { showHome(); say("live suggested replies: $score/${screens.size}"); return }
        val p = screens[i]
        screen()
        text("Live ${i + 1} / ${screens.size}   ·   ${network()}", 14f)
        text("Rin: “${p.question}”", 24f, true)
        chipLines(p.chips, p.target)
        val status = text("Tap, then say the highlighted reply.", 16f)
        button("🎤  Tap to speak") {
            status.text = "Listening…"
            scope.launch {
                val e = AndroidEngine(this@MainActivity, "android-od-live", onDevice = true)
                val r = e.live(); e.close()
                val c = ChipMatcher.match(r.text, p.chips)
                val ok = c == p.target
                Log.i(TAG, JSONObject().put("phase", "live-sugg").put("network", network()).put("id", p.id)
                    .put("target", p.target).put("text", r.text).put("pred", c ?: -1).put("ok", ok)
                    .put("endOfSpeechToResultMs", r.latencyMs).put("err", r.err ?: "").put("via", r.via).toString())
                status.text = "Heard \"${r.text}\" → ${if (ok) "✓" else "✗"}   (${r.latencyMs} ms after you stopped)"
                kotlinx.coroutines.delay(1500)
                showLiveSugg(i + 1, score + if (ok) 1 else 0)
            }
        }.minHeight = dp(96)
        button("Home") { showHome() }
    }

    private suspend fun liveTest() {
        if (!onDeviceAvailable()) { say("on-device recognizer NOT available"); return }
        val e = AndroidEngine(this, "android-od-live", onDevice = true)
        say("network=${network()}. Speak now…")
        val r = e.live()
        val pred = IntentMatcher.classify(r.text)
        Log.i(TAG, JSONObject().put("phase", "live").put("network", network()).put("text", r.text)
            .put("pred", pred.name).put("endOfSpeechToResultMs", r.latencyMs).put("err", r.err ?: "").put("via", r.via).toString())
        say("live: \"${r.text}\" → $pred, onEndOfSpeech→result ${r.latencyMs} ms ${r.err ?: ""}")
        e.close()
    }

    companion object { const val TAG = "S3" }
}
