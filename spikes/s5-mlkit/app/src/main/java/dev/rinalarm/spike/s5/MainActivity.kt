package dev.rinalarm.spike.s5

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * S5 spike: which things in the tester's home does ML Kit's bundled labeler find reliably in
 * morning light? Each trial streams the live camera for 8 s and logs every frame's labels
 * (confidence >= 0.1). Frames are analysed in memory and dropped; no image is ever written.
 * Scoring (label sets, threshold, consecutive frames) happens on the PC in tools/summarize.py.
 */
class MainActivity : ComponentActivity(), SensorEventListener {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.1f).build())
    private val main = Handler(Looper.getMainLooper())
    private val store by lazy { TrialStore(File(getExternalFilesDir(null), "s5")) }
    private val prefs by lazy { getSharedPreferences("s5", MODE_PRIVATE) }
    private val sessions = listOf("smoke", "dev", "heldout")

    private var cameraProvider: ProcessCameraProvider? = null
    private var sensorManager: SensorManager? = null
    @Volatile private var lux = -1f
    @Volatile private var recorder: TrialRecorder? = null
    @Volatile private var live: ((List<Pair<String, Float>>) -> Unit)? = null
    private var onBack: (() -> Unit)? = null

    private var session: String
        get() = prefs.getString("session", "smoke")!!
        set(v) { prefs.edit().putString("session", v).apply() }

    private fun customTargets(): List<Scene> =
        prefs.getStringSet("custom", emptySet())!!.sorted().map { Scene(Targets.customId(it), it, "Your own target") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(SensorManager::class.java)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
        store.append(JSONObject().put("type", "header").put("at", System.currentTimeMillis())
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("sdk", Build.VERSION.SDK_INT)
            .put("mlkit", "image-labeling 17.0.9 bundled").put("camerax", "1.6.2").put("min_conf", 0.1))
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { onBack?.invoke() ?: finish() }
        })
        showHome()
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        // Leaving mid-trial (call, screen off) makes the trial meaningless: drop it unsaved.
        if (recorder != null) { recorder = null; main.removeCallbacksAndMessages(null); showHome() }
    }

    override fun onDestroy() {
        super.onDestroy()
        labeler.close()
        analysisExecutor.shutdown()
    }

    override fun onSensorChanged(event: SensorEvent) { lux = event.values[0] }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------- screens ----------

    private fun showHome() {
        unbindCamera()
        onBack = null
        val col = column()
        col.addView(text("S5 · ML Kit in your home", 22f, bold = true))
        col.addView(text("Only labels are logged. Camera frames stay in memory and are never saved.", 13f, color = Color.GRAY))
        col.addView(button("Session: $session  (tap to change)") {
            session = sessions[(sessions.indexOf(session) + 1) % sessions.size]; showHome()
        })
        col.addView(button("Explore: live labels (nothing logged)") { showExplore() })

        col.addView(text("Targets · ${Targets.TARGET_TRIALS} trials each · only the ones you have", 16f, bold = true, top = 24))
        (Targets.targets + customTargets()).forEach { col.addView(sceneButton(it)) }

        val input = EditText(this).apply { hint = "Add another thing (English name)"; setSingleLine() }
        col.addView(input)
        col.addView(button("Add target") {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                prefs.edit().putStringSet("custom", prefs.getStringSet("custom", emptySet())!! + name).apply()
                showHome()
            }
        })

        col.addView(text("From bed · ${Targets.NEGATIVE_TRIALS} each · nothing should be found here", 16f, bold = true, top = 24))
        Targets.negatives.forEach { col.addView(sceneButton(it)) }

        col.addView(text("Log: ${store.file.path}", 11f, color = Color.GRAY, top = 24))
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun sceneButton(scene: Scene): View {
        val done = store.count(session, scene.id)
        val planned = Targets.planned(scene)
        val mark = if (done >= planned) "  ✓" else ""
        return button("${scene.name}  ·  $done/$planned$mark") { showTrial(scene) }
    }

    private fun showTrial(scene: Scene) {
        onBack = { showHome() }
        val preview = PreviewView(this)
        val n = store.count(session, scene.id) + 1
        val cond = Targets.condition(scene, n)
        val info = text("${scene.name} · trial $n/${Targets.planned(scene)} · $session\n" +
            (if (scene.hint.isNotEmpty()) scene.hint + "\n" else "") + cond.describe(), 15f)
        val status = text("Press Start, then aim at it for 8 s.", 15f, bold = true)
        val start = Button(this).apply { text = "Start" }
        val discard = button("Discard last trial (I aimed wrong)") {
            store.lastId()?.let { store.discard(it, "tester"); showTrial(scene) }
        }
        start.setOnClickListener {
            start.isEnabled = false
            discard.isEnabled = false
            val rec = TrialRecorder(
                id = "$session-${scene.id}-$n-${System.currentTimeMillis()}", session = session, scene = scene, n = n,
                condition = cond, startWallMs = System.currentTimeMillis(), startElapsedMs = SystemClock.elapsedRealtime(), luxStart = lux,
            )
            recorder = rec
            val end = SystemClock.elapsedRealtime() + Targets.TRIAL_MS
            val tick = object : Runnable {
                override fun run() {
                    val left = end - SystemClock.elapsedRealtime()
                    if (left > 0) { status.text = "Recording… %.1f s".format(left / 1000.0); main.postDelayed(this, 200); return }
                    recorder = null
                    store.append(rec.toJson(lux))
                    beep()
                    status.text = "Saved: ${rec.frameCount()} frames. Change position for the next one."
                    main.postDelayed({ showTrial(scene) }, 1500)
                }
            }
            main.post(tick)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        col.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(info); col.addView(status); col.addView(start); col.addView(discard)
        col.addView(button("Back") { showHome() })
        setContentView(col)
        bindCamera(preview)
    }

    private fun showExplore() {
        onBack = { showHome() }
        val preview = PreviewView(this)
        val labels = text("…", 18f)
        live = { list -> labels.text = list.take(8).joinToString("\n") { (t, c) -> "%.2f  %s".format(c, t) }.ifEmpty { "(nothing ≥ 0.10)" } }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        col.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(labels, LinearLayout.LayoutParams(-1, dp(220)))
        col.addView(button("Back") { showHome() })
        setContentView(col)
        bindCamera(preview)
    }

    // ---------- camera + labeler ----------

    private fun bindCamera(preview: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get().also { cameraProvider = it }
            val p = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { analyze(it) }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, analysis)
        }, mainExecutor)
    }

    private fun unbindCamera() {
        live = null
        cameraProvider?.unbindAll()
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: run { proxy.close(); return }
        val grabbed = SystemClock.elapsedRealtime()
        val luma = meanLuma(proxy)
        val w = proxy.width; val h = proxy.height; val rot = proxy.imageInfo.rotationDegrees
        labeler.process(InputImage.fromMediaImage(media, rot))
            .addOnSuccessListener(analysisExecutor) { result ->
                val inferMs = SystemClock.elapsedRealtime() - grabbed
                val list = result.sortedByDescending { it.confidence }.map { it.text to it.confidence }
                recorder?.let { r -> r.width = w; r.height = h; r.rotation = rot; r.add(grabbed, inferMs, luma, lux, list) }
                live?.let { cb -> runOnUiThread { live?.let { cb(list) } } }
            }
            .addOnCompleteListener(analysisExecutor) { proxy.close() }
    }

    /** Average of the Y plane (0–255), sampled on an 8-pixel grid: how dark the scene is. */
    private fun meanLuma(proxy: ImageProxy): Int {
        val plane = proxy.planes[0]
        val buf = plane.buffer
        var sum = 0L; var count = 0
        var y = 0
        while (y < proxy.height) {
            var x = 0
            while (x < proxy.width) {
                sum += buf.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 0xFF; count++
                x += 8
            }
            y += 8
        }
        return if (count == 0) -1 else (sum / count).toInt()
    }

    private fun beep() = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).apply { startTone(ToneGenerator.TONE_PROP_ACK, 200) }
            .also { main.postDelayed({ it.release() }, 400) }
    }

    // ---------- tiny view helpers ----------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)) }

    private fun text(s: String, size: Float, bold: Boolean = false, color: Int? = null, top: Int = 0) = TextView(this).apply {
        text = s; textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        color?.let { setTextColor(it) }
        setPadding(0, dp(top), 0, dp(4))
    }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
    }
}
