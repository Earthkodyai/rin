package dev.rinalarm.spike.s5

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
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
import android.util.Base64
import android.util.Half
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.android.gms.tasks.Tasks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * S5 spike, part B. Setup (once): teach each of the tester's own objects with a 6 s scan, then a
 * 15 s scan of everything reachable from bed. Trials: 8 s blind recordings. Every frame is analysed
 * in memory and dropped; the log keeps ML Kit labels, brightness and two MediaPipe image embeddings
 * (MobileNetV3 small/large, centre square crop). Matching and the light gate are scored on the PC
 * by tools/score_b.py, so the app never shows whether a trial passed.
 */
class MainActivity : ComponentActivity(), SensorEventListener {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.1f).build())
    private val embedSmall by lazy { embedder("mobilenet_v3_small.tflite") }
    private val embedLarge by lazy { embedder("mobilenet_v3_large.tflite") }
    private val main = Handler(Looper.getMainLooper())
    private val store by lazy { TrialStore(File(getExternalFilesDir(null), "s5")) }
    private val prefs by lazy { getSharedPreferences("s5", MODE_PRIVATE) }
    private val sessions = listOf("b-smoke", "b-dev", "b-heldout")

    private var cameraProvider: ProcessCameraProvider? = null
    private var sensorManager: SensorManager? = null
    @Volatile private var lux = -1f
    @Volatile private var recorder: TrialRecorder? = null
    @Volatile private var live: ((List<Pair<String, Float>>, Int) -> Unit)? = null
    private var onBack: (() -> Unit)? = null
    /** Only the trial just recorded on this scene can be discarded, once (part A's button cascaded). */
    private var lastSaved: Pair<String, String>? = null

    private var session: String
        get() = prefs.getString("session_b", "b-smoke")!!
        set(v) { prefs.edit().putString("session_b", v).apply() }

    private fun objects(): List<Scene> =
        prefs.getStringSet("objects", emptySet())!!.sorted().map { Scene(Targets.objectId(it), it, "") }

    private fun embedder(asset: String): ImageEmbedder = ImageEmbedder.createFromOptions(this,
        ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(asset).build())
            .setRunningMode(RunningMode.IMAGE).setL2Normalize(true).setQuantize(false).build())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(SensorManager::class.java)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
        store.append(JSONObject().put("type", "header").put("at", System.currentTimeMillis()).put("part", "B")
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("sdk", Build.VERSION.SDK_INT)
            .put("mlkit", "image-labeling 17.0.9 bundled").put("camerax", "1.6.2")
            .put("embedder", "mediapipe tasks-vision 1.0.0, mobilenet_v3_small/large float32, L2, centre square").put("min_conf", 0.1))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
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
        // Leaving mid-recording (call, screen off) makes it meaningless: drop it unsaved.
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
        col.addView(text("S5 part B · your own objects", 22f, bold = true))
        col.addView(text("Camera frames stay in memory and are never saved. The log keeps labels, brightness and embeddings (numbers).", 13f, color = Color.GRAY))
        col.addView(button("Explore: live labels + brightness (nothing logged)") { showExplore() })

        col.addView(text("1 · Setup, once · lights ON", 16f, bold = true, top = 20))
        col.addView(text("Tap the things you have (tap again to remove):", 14f))
        val chosen = prefs.getStringSet("objects", emptySet())!!
        (SUGGESTED + chosen.filter { it !in SUGGESTED }.sorted()).chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { name ->
                val on = name in chosen
                row.addView(button(if (on) "✓ $name" else name) {
                    val taught = store.done("teach", Targets.objectId(name))
                    if (on && taught) toast("Already taught; kept") else { setObject(name, !on); showHome() }
                }.apply { if (on) setTextColor(Color.rgb(0, 150, 80)) }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            col.addView(row)
        }
        val input = EditText(this).apply { hint = "Something else? English name"; setSingleLine() }
        col.addView(input)
        col.addView(button("Add this name") {
            val name = input.text.toString().trim().lowercase()
            if (name.isEmpty()) toast("Type a name first") else { setObject(name, true); showHome() }
        })
        objects().forEach { o ->
            val taught = store.done("teach", o.id)
            col.addView(button("Teach: ${o.name}  ·  ${if (taught) "✓ (tap to redo)" else "not yet"}") {
                showRecord("teach", o, n = 1, Condition("on", "teach"), Targets.TEACH_MS,
                    "Keep the ${o.name} inside the square. Move slowly around it: close, far, left, right.")
            })
        }
        val bed = Scene("bed-scan", "Bed scan", "")
        col.addView(button("Bed scan (15 s)  ·  ${if (store.done("bedscan", bed.id)) "✓ (tap to redo)" else "not yet"}") {
            showRecord("bedscan", bed, n = 1, Condition("on", "hold"), Targets.BED_SCAN_MS,
                "From bed, lights ON, without getting up: slowly sweep the bed, pillow, blanket, ceiling, walls and everything by the bed.")
        })

        col.addView(button("Session: $session  (tap to change)").also { b ->
            b.setOnClickListener { session = sessions[(sessions.indexOf(session) + 1) % sessions.size]; showHome() }
        }.also { (it.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(20) })
        col.addView(text("2 · Your objects · ${Targets.TARGET_TRIALS} trials each", 16f, bold = true, top = 8))
        objects().filter { store.done("teach", it.id) }.forEach { col.addView(sceneButton(it)) }
        col.addView(text("3 · From bed · ${Targets.NEGATIVE_TRIALS} each", 16f, bold = true, top = 20))
        Targets.negatives.forEach { col.addView(sceneButton(it)) }

        col.addView(text("Log: ${store.file.path}", 11f, color = Color.GRAY, top = 24))
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun setObject(name: String, add: Boolean) {
        val set = prefs.getStringSet("objects", emptySet())!!
        prefs.edit().putStringSet("objects", if (add) set + name else set - name).apply()
    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()

    private fun sceneButton(scene: Scene): View {
        val done = store.count(session, scene.id)
        val planned = Targets.planned(scene)
        return button("${scene.name}  ·  $done/$planned${if (done >= planned) "  ✓" else ""}") {
            lastSaved = null
            val n = store.count(session, scene.id) + 1
            showRecord("trial", scene, n, Targets.condition(scene, n), Targets.TRIAL_MS, null)
        }
    }

    /** One recording screen for trials, teach scans and the bed scan. */
    private fun showRecord(type: String, scene: Scene, n: Int, cond: Condition, windowMs: Long, instructions: String?) {
        onBack = { showHome() }
        val preview = PreviewView(this)
        val sess = if (type == "trial") session else TrialStore.SETUP
        val header = when (type) {
            "trial" -> "${scene.name} · trial $n/${Targets.planned(scene)} · $session\n" +
                (if (scene.hint.isNotEmpty()) scene.hint + "\n" else "") + cond.describe()
            else -> "${scene.name} · ${windowMs / 1000} s\n$instructions"
        }
        val status = text("Press Start, then aim for ${windowMs / 1000} s. Keep it inside the square.", 15f, bold = true)
        val start = Button(this).apply { text = "Start" }
        val canDiscard = type == "trial" && lastSaved?.first == scene.id
        val discard = button("Discard the trial just recorded (I aimed wrong)") {
            lastSaved?.let { store.discard(it.second, "tester") }
            lastSaved = null
            showRecord(type, scene, store.count(session, scene.id) + 1, Targets.condition(scene, store.count(session, scene.id) + 1), windowMs, instructions)
        }.apply { isEnabled = canDiscard }
        start.setOnClickListener {
            start.isEnabled = false
            discard.isEnabled = false
            val rec = TrialRecorder(
                type = type, id = "$sess-$type-${scene.id}-$n-${System.currentTimeMillis()}", session = sess, scene = scene, n = n,
                condition = cond, windowMs = windowMs, startWallMs = System.currentTimeMillis(),
                startElapsedMs = SystemClock.elapsedRealtime(), luxStart = lux,
            )
            recorder = rec
            val end = SystemClock.elapsedRealtime() + windowMs
            main.post(object : Runnable {
                override fun run() {
                    val left = end - SystemClock.elapsedRealtime()
                    if (left > 0) { status.text = "Recording… %.1f s".format(left / 1000.0); main.postDelayed(this, 200); return }
                    recorder = null
                    store.append(rec.toJson(lux))
                    beep()
                    status.text = "Saved: ${rec.frameCount()} frames."
                    if (type == "trial") {
                        lastSaved = scene.id to rec.id
                        val next = store.count(session, scene.id) + 1
                        main.postDelayed({ showRecord(type, scene, next, Targets.condition(scene, next), windowMs, instructions) }, 1500)
                    } else main.postDelayed({ showHome() }, 1500)
                }
            })
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        col.addView(framed(preview), LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(text(header, 15f)); col.addView(status); col.addView(start)
        if (type == "trial") col.addView(discard)
        col.addView(button("Back") { showHome() })
        setContentView(col)
        bindCamera(preview)
    }

    private fun showExplore() {
        onBack = { showHome() }
        val preview = PreviewView(this)
        val labels = text("…", 17f)
        live = { list, luma ->
            labels.text = "brightness $luma/255 (gate ≥ 70)\n" +
                list.take(7).joinToString("\n") { (t, c) -> "%.2f  %s".format(c, t) }.ifEmpty { "(no label ≥ 0.10)" }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        col.addView(framed(preview), LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(labels, LinearLayout.LayoutParams(-1, dp(220)))
        col.addView(button("Back") { showHome() })
        setContentView(col)
        bindCamera(preview)
    }

    /** Preview with an outline of the centre square that the embedder sees. */
    private fun framed(preview: PreviewView) = FrameLayout(this).apply {
        preview.scaleType = PreviewView.ScaleType.FIT_CENTER
        addView(preview, FrameLayout.LayoutParams(-1, -1))
        addView(SquareGuide(this@MainActivity), FrameLayout.LayoutParams(-1, -1))
    }

    // ---------- camera + models ----------

    private fun bindCamera(preview: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get().also { cameraProvider = it }
            val p = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
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

    private fun analyze(proxy: ImageProxy) {
        val grabbed = SystemClock.elapsedRealtime()
        val rot = proxy.imageInfo.rotationDegrees
        val w = proxy.width; val h = proxy.height
        val raw = try { proxy.toBitmap() } finally { proxy.close() }
        val upright = if (rot == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        val luma = meanLuma(upright)
        val labels = runCatching {
            Tasks.await(labeler.process(InputImage.fromBitmap(upright, 0)))
                .sortedByDescending { it.confidence }.map { it.text to it.confidence }
        }.getOrDefault(emptyList())
        val inferMs = SystemClock.elapsedRealtime() - grabbed
        val rec = recorder
        if (rec != null) {
            val side = minOf(upright.width, upright.height)
            val square = Bitmap.createBitmap(upright, (upright.width - side) / 2, (upright.height - side) / 2, side, side)
            val t0 = SystemClock.elapsedRealtime()
            val mp = BitmapImageBuilder(square).build()
            val es = encode(embedSmall.embed(mp).embeddingResult().embeddings()[0].floatEmbedding())
            val el = encode(embedLarge.embed(mp).embeddingResult().embeddings()[0].floatEmbedding())
            val embedMs = SystemClock.elapsedRealtime() - t0
            rec.width = w; rec.height = h; rec.rotation = rot
            rec.add(grabbed) { t -> Frame(t, inferMs, embedMs, luma, lux, labels, es, el) }
        }
        live?.let { cb -> runOnUiThread { live?.let { cb(labels, luma) } } }
    }

    /** float32 embedding -> little-endian float16 -> base64 (half the size, far more precision than cosine needs). */
    private fun encode(v: FloatArray): String {
        val buf = ByteBuffer.allocate(v.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { buf.putShort(Half.toHalf(it)) }
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    /** Mean luma (0–255, BT.601) on an 8-pixel grid: how dark the scene is after auto-exposure. */
    private fun meanLuma(b: Bitmap): Int {
        var sum = 0L; var count = 0
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val p = b.getPixel(x, y)
                sum += (299 * ((p shr 16) and 0xFF) + 587 * ((p shr 8) and 0xFF) + 114 * (p and 0xFF)) / 1000; count++
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

    companion object {
        /** Fixed, mirror-free things usually away from the bed. The tester picks the ones they have. */
        val SUGGESTED = listOf(
            "fridge", "front door", "kettle", "rice cooker", "microwave", "kitchen sink", "stairs", "tv",
            "shoe rack", "washing machine", "helmet", "fan", "sofa", "desk", "computer", "water dispenser",
        )
    }

    private class SquareGuide(ctx: Context) : View(ctx) {
        private val paint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.YELLOW }
        override fun onDraw(canvas: Canvas) {
            // The preview is FIT_CENTER 4:3 portrait, so the frame's width is the view's width (or less).
            val frameW = minOf(width.toFloat(), height * 3f / 4f)
            val cx = width / 2f; val cy = height / 2f; val half = frameW / 2f
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
        }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)) }

    private fun text(s: String, size: Float, bold: Boolean = false, color: Int? = null, top: Int = 0) = TextView(this).apply {
        text = s; textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        color?.let { setTextColor(it) }
        setPadding(0, dp(top), 0, dp(4))
    }

    private fun button(s: String, onClick: () -> Unit = {}) = Button(this).apply {
        text = s; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
    }
}
