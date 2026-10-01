package io.github.earthkodyai.rinalarm.character

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.mission.CupsAct
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext

/**
 * Debug hooks for DebugCharacterReceiver: crash the renderer (to prove the fallback), force a mood (to time it), play
 * a gesture, say a debug voice line (assets voice/dev/<clip>.mp3, git-ignored), measure frame pacing, or change the
 * frame-rate cap (to measure the headroom above it).
 */
object CharacterDebug {
  val crashRenderer = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  /** Overrides the screen's mood until the screen's own mood next changes. */
  val mood = MutableSharedFlow<Pair<Mood, Float>>(extraBufferCapacity = 1)
  val gesture = MutableSharedFlow<Gesture>(extraBufferCapacity = 1)
  val say = MutableSharedFlow<String>(extraBufferCapacity = 1)
  /** Milliseconds of rendering to measure; the result is logged as `RinChar: stats ...`. */
  val measureFrames = MutableSharedFlow<Long>(extraBufferCapacity = 1)
  val fpsCap = MutableSharedFlow<Int>(extraBufferCapacity = 1)
}

/** Whether the page shows the cup table (task 3.4), so the ring screen knows when to draw its own 2D board. */
sealed interface CupsView {
  /** Loading, moving the camera, or not asked yet. */
  data object Pending : CupsView

  /** The table is in view; [x] is each slot's cup across the view (0..1), for the tap zones. */
  data class Shown(val x: List<Float>) : CupsView

  /** The page is gone for good (the still image shows): only the 2D board can play. */
  data object Unavailable : CupsView
}

private enum class Phase {
  LOADING,
  READY,
  /** The still image, for good: no model in this build, no bridge support, a page error, a timeout or a crash. */
  FALLBACK,
}

/**
 * Rin, rendered by the web/character page in a WebView. It is never on the ring path (hard rule): whatever goes
 * wrong here, the still image takes over and the rest of the app carries on. The page's canvas stays transparent
 * until Rin's first frame, so the still sits on top and fades out once she is drawn. The page stops rendering while
 * the screen is paused. [mood] blends in on the page (≤ 300 ms, logged by tag RinChar); a tap on her head makes her
 * happy for a moment there (no vibration: the user cut it in UX.3, too faint to feel). She greets the user when the app opens and
 * gestures now and then (GestureDirector), and plays each gesture from [cues] (the ring screen's mission moments).
 * [onHeadTap] hears each tap on her head, for the screen's own answer (her line on the home screen).
 * Voice lines play natively (VoicePlayer, here or in a game's [speech]) and only move her mouth here.
 *
 * Before the first unlock after a reboot (a ring can come then) the WebView has no credential-encrypted storage to
 * start in, so she is the still until then.
 */
@Composable
fun CharacterView(
  mood: Mood,
  modifier: Modifier = Modifier,
  intensity: Float = 1f,
  framing: Framing = Framing.STRIP,
  cues: Flow<Gesture>? = null,
  cups: CupsAct? = null,
  onCups: (CupsView) -> Unit = {},
  speech: Flow<Speaking?>? = null,
  onHeadTap: () -> Unit = {},
  onVisible: () -> Unit = {},
  greetOnShow: Boolean = true,
) {
  val context = LocalContext.current
  val model = remember {
    CharacterAssets.model(context)?.takeIf {
      WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        context.getSystemService(UserManager::class.java)?.isUserUnlocked != false
    }
  }
  var phase by remember { mutableStateOf(if (model == null) Phase.FALLBACK else Phase.LOADING) }
  val stills = remember { CharacterAssets.stills(context, framing) }
  val host = remember { CharacterHost() }
  val description = stringResource(R.string.character_description)
  var debugMood by remember { mutableStateOf<Pair<Mood, Float>?>(null) }
  val shown = debugMood ?: (mood to intensity)
  SideEffect { host.setMood(shown) }
  val currentOnCups by rememberUpdatedState(onCups)
  SideEffect {
    host.onCups = { currentOnCups(it) }
    host.setCups(cups)
  }
  val currentOnHeadTap by rememberUpdatedState(onHeadTap)
  SideEffect { host.onHeadTap = { currentOnHeadTap() } }
  val director = remember { GestureDirector() }
  val currentMood by rememberUpdatedState(shown.first)
  // A screen can turn the greeting off; the ring screen keeps it and drops its first line's gesture instead (4.3).
  SideEffect { host.onShown = { away -> if (greetOnShow) director.greetOnShow(currentMood, away)?.let(host::gesture) } }
  // Her face is up (the page is ready) or the still image is here to stay: a screen may wait for this to speak (4.3).
  val currentOnVisible by rememberUpdatedState(onVisible)
  LaunchedEffect(phase) { if (phase != Phase.LOADING) currentOnVisible() }
  val scope = rememberCoroutineScope()
  val voice = remember { VoicePlayer(context, scope, onSpeaking = host::speak) }
  DisposableEffect(voice) { onDispose { voice.stop() } }

  Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
    if (model != null && phase != Phase.FALLBACK) {
      AndroidView(
        factory = { ctx ->
          host.create(ctx, model, shown, framing, onReady = { phase = Phase.READY }, onFailed = { phase = Phase.FALLBACK })
        },
        onRelease = { host.release() },
        modifier = Modifier.fillMaxSize(),
      )
    }
    AnimatedVisibility(phase != Phase.READY, enter = fadeIn(tween(FADE_MS)), exit = fadeOut(tween(FADE_MS))) {
      Still(shown.first, stills)
    }
  }

  LifecycleResumeEffect(host) {
    host.resume()
    onPauseOrDispose { host.pause() }
  }
  LaunchedEffect(phase) { if (phase == Phase.FALLBACK) currentOnCups(CupsView.Unavailable) }
  LaunchedEffect(phase) {
    if (phase != Phase.LOADING) return@LaunchedEffect
    delay(LOAD_TIMEOUT_MS)
    Log.w(TAG, "no first frame after $LOAD_TIMEOUT_MS ms, showing the still")
    phase = Phase.FALLBACK
  }
  LaunchedEffect(phase) {
    if (phase != Phase.READY) return@LaunchedEffect
    while (true) {
      delay(director.nextIdleDelayMs())
      host.gesture(director.idle(currentMood)) // dropped while off screen
    }
  }
  LaunchedEffect(host, cues) { cues?.collect { host.gesture(it) } }
  // A game's lines (Repeat after Rin) play in the game's own VoicePlayer; only her mouth follows them here.
  LaunchedEffect(host, speech) { speech?.collect(host::speak) }
  LaunchedEffect(host) { CharacterDebug.crashRenderer.collect { host.crashRenderer() } }
  LaunchedEffect(host) { CharacterDebug.gesture.collect { host.gesture(it) } }
  LaunchedEffect(voice) { CharacterDebug.say.collect { voice.play("voice/dev/$it") } }
  LaunchedEffect(host) { CharacterDebug.measureFrames.collect { host.debug(CharacterCommand.MeasureFrames(it)) } }
  LaunchedEffect(host) { CharacterDebug.fpsCap.collect { host.debug(CharacterCommand.FpsCap(it)) } }
  LaunchedEffect(host) { CharacterDebug.mood.collect { debugMood = it } }
  LaunchedEffect(mood) { debugMood = null }
}

/**
 * Rin's still for [mood] (task 2.5): rendered from the build's model with the live page's framing, scaled to the
 * view's height and centred, so she sits exactly where the page will draw her and the fade between them is seamless.
 * A mood change crossfades like the page's blend. Builds without stills (no model) show a tinted silhouette.
 */
@Composable
private fun Still(mood: Mood, stills: Map<Mood, String>) {
  val context = LocalContext.current
  // Keeps the last image while the next one decodes; null only until the first decode, or if it failed.
  val image by produceState<StillImage?>(null, mood) {
    val path = stills[mood] ?: stills.values.firstOrNull()
    val bitmap =
      path?.let {
        withContext(Dispatchers.IO) { runCatching { context.assets.open(it).use(BitmapFactory::decodeStream) }.getOrNull() }
      }
    if (path != null && bitmap == null) Log.w(TAG, "could not decode $path")
    value = bitmap?.let { StillImage.Of(it.asImageBitmap()) } ?: StillImage.None
  }
  Crossfade(image, animationSpec = tween(STILL_BLEND_MS), label = "still") { still ->
    when (still) {
      is StillImage.Of ->
        Image(still.bitmap, contentDescription = null, contentScale = ContentScale.FillHeight, modifier = Modifier.fillMaxSize())
      StillImage.None ->
        Image(
          painterResource(R.drawable.rin_still),
          contentDescription = null,
          colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxHeight(),
        )
      null -> Unit // decoding (a few ms): nothing rather than a flash of the silhouette
    }
  }
}

private sealed interface StillImage {
  data class Of(val bitmap: ImageBitmap) : StillImage

  data object None : StillImage
}

/**
 * Owns one WebView and its bridge. Main thread only (Compose, WebView callbacks and the message listener all are).
 * CharacterView creates a host only after checking WEB_MESSAGE_LISTENER, which covers every bridge call here.
 */
@SuppressLint("RequiresFeature")
private class CharacterHost {
  private var webView: WebView? = null
  private var reply: JavaScriptReplyProxy? = null
  private var resumed = false
  private var ready = false
  /** The mood the screen wants, and the one the page has (from the URL, then from the last command sent). */
  private var wanted: Pair<Mood, Float>? = null
  private var onPage: Pair<Mood, Float>? = null
  /** The line being spoken, re-sent on resume so the mouth picks up mid-line (the page times it from `at`). */
  private var speaking: Speaking? = null
  private var pausedAt: Long? = null
  /** Called when she is on screen and ready: first with null (the app just opened), then with the time away. */
  var onShown: (awayMs: Long?) -> Unit = {}
  /** The cup shuffle's act the screen wants shown, and whether the page has it. */
  private var cups: CupsAct? = null
  private var cupsSent = false
  var onCups: (CupsView) -> Unit = {}
  /** The user tapped her head; the page has already reacted (task 2.2), the screen may add a line (4.2). */
  var onHeadTap: () -> Unit = {}

  @SuppressLint("SetJavaScriptEnabled") // our own page from APK assets; nothing else can load (see the client)
  fun create(
    context: Context,
    model: String,
    mood: Pair<Mood, Float>,
    framing: Framing,
    onReady: () -> Unit,
    onFailed: () -> Unit,
  ): View {
    val t0 = System.currentTimeMillis() // before Chromium starts, so the load time includes WebView start-up
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    WebView.setWebContentsDebuggingEnabled(debuggable)
    val loader =
      WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
    val web = WebView(context)
    web.setBackgroundColor(Color.TRANSPARENT) // the page's canvas is transparent too; the screen shows through
    web.settings.apply {
      javaScriptEnabled = true
      allowFileAccess = false
      allowContentAccess = false
      domStorageEnabled = false
      setGeolocationEnabled(false)
      setSupportMultipleWindows(false)
    }
    // Off screen, Android may kill this renderer instead of the app, whose process also runs the ring path.
    web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
    web.webViewClient =
      CharacterClient(loader) {
        reply = null
        onFailed() // removes the view from the screen, then release() destroys it
      }
    WebViewCompat.addWebMessageListener(web, BRIDGE, setOf(CharacterAssets.ORIGIN)) { _, message, _, isMainFrame, proxy ->
      if (!isMainFrame) return@addWebMessageListener
      reply = proxy
      if (!resumed) proxy.postMessage(CharacterCommand.Pause.json)
      when (val parsed = CharacterMessage.parse(message.data ?: return@addWebMessageListener)) {
        is CharacterMessage.Ready -> {
          Log.i(TAG, "ready $parsed")
          ready = true
          onReady()
          sendMood()
          sendSpeaking()
          sendCups()
          // Debug builds: frame pacing over the ring screen's first seconds, where the tester saw her stutter (3.4).
          if (debuggable && framing == Framing.FULL) proxy.postMessage(CharacterCommand.MeasureFrames(STARTUP_MEASURE_MS).json)
          if (resumed) onShown(null)
        }
        is CharacterMessage.EmotionShown ->
          Log.i(TAG, "emotion ${parsed.mood} toPage=${parsed.ms.toPage} total=${parsed.ms.total}")
        is CharacterMessage.GestureStarted -> Log.i(TAG, "gesture ${parsed.name} ok=${parsed.ok}")
        is CharacterMessage.Stats -> Log.i(TAG, "stats $parsed")
        is CharacterMessage.CupsShown -> {
          Log.i(TAG, "cups shown=${parsed.shown} x=${parsed.x}")
          onCups(if (parsed.shown && parsed.x.size == 3) CupsView.Shown(parsed.x.map { it.toFloat() }) else CupsView.Pending)
        }
        is CharacterMessage.Tap -> {
          Log.i(TAG, "tap ${parsed.part}")
          if (parsed.part == "head") onHeadTap()
        }
        is CharacterMessage.Error -> {
          Log.w(TAG, "page error: ${parsed.message}")
          onFailed()
        }
        null -> Unit
      }
    }
    val url =
      CharacterAssets.PAGE.toUri()
        .buildUpon()
        .appendQueryParameter("model", model)
        .appendQueryParameter("fps", framing.fpsCap.toString())
        .appendQueryParameter("pr", PIXEL_RATIO_CAP.toString())
        .appendQueryParameter("t0", t0.toString())
        .appendQueryParameter("mood", mood.first.wire)
        .appendQueryParameter("intensity", mood.second.toString())
        .apply { if (framing != Framing.STRIP) appendQueryParameter("frame", framing.wire) }
        .build()
    wanted = mood
    onPage = mood
    web.loadUrl(url.toString())
    webView = web
    // Hosting the WebView directly in AndroidView froze a WebGL page after its first frames on the 14T (WebView 153,
    // HyperOS 2): the WebView stopped invalidating, so Rin never appeared. Plain HTML kept updating, and a FrameLayout
    // parent fixed WebGL (task 2.1 bisect: invalidates 6 -> 140 in 8 s).
    return FrameLayout(context).apply { addView(web) }
  }

  fun resume() {
    resumed = true
    webView?.onResume()
    reply?.postMessage(CharacterCommand.Resume.json)
    sendMood()
    sendSpeaking()
    cupsSent = false // re-sent on every resume: the page times acts by the clock, so it picks up where they are now
    sendCups()
    val away = pausedAt?.let { System.currentTimeMillis() - it }
    if (ready && away != null) onShown(away)
  }

  /** Plays [gesture] if she is ready and on screen; otherwise it is dropped (gestures are of the moment). */
  fun gesture(gesture: Gesture) {
    val proxy = reply ?: return
    if (ready && resumed) proxy.postMessage(CharacterCommand.PlayGesture(gesture).json)
  }

  fun speak(line: Speaking?) {
    speaking = line
    if (line == null) reply?.postMessage(CharacterCommand.Hush.json) else sendSpeaking()
  }

  private fun sendSpeaking() {
    val line = speaking ?: return
    val proxy = reply ?: return
    if (ready && resumed) proxy.postMessage(CharacterCommand.Speak(line.mouth, line.at).json)
  }

  fun setCups(act: CupsAct?) {
    if (act == cups) return
    cups = act
    cupsSent = false
    sendCups()
  }

  private fun sendCups() {
    val proxy = reply ?: return
    if (!ready || !resumed || cupsSent) return
    val offset = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    proxy.postMessage(CharacterCommand.Cups(cups, offset).json)
    cupsSent = true
  }

  fun setMood(mood: Pair<Mood, Float>) {
    wanted = mood
    sendMood()
  }

  /**
   * Sends the wanted mood once the page can show it straight away: after its first frame and while on screen. A change
   * made while paused waits for resume, so its timing never includes time off screen.
   */
  private fun sendMood() {
    val mood = wanted ?: return
    val proxy = reply ?: return
    if (!ready || !resumed || mood == onPage) return
    proxy.postMessage(CharacterCommand.Emotion(mood.first, mood.second, System.currentTimeMillis()).json)
    onPage = mood
  }

  fun pause() {
    resumed = false
    pausedAt = System.currentTimeMillis()
    reply?.postMessage(CharacterCommand.Pause.json)
    webView?.onPause()
  }

  /** Debug commands (frame measurements, the fps cap) go straight to the page once it is ready. */
  fun debug(command: CharacterCommand) {
    val proxy = reply ?: return
    if (ready) proxy.postMessage(command.json)
  }

  fun crashRenderer() {
    webView?.loadUrl("chrome://crash")
  }

  fun release() {
    reply = null
    ready = false
    webView?.destroy()
    webView = null
  }

}

/**
 * Serves only our assets, never navigates, and survives the renderer dying. The suppression is for a false positive:
 * androidx.webkit's detector reads Kotlin's supertype call `WebViewClientCompat()` as a bare client being created.
 */
@SuppressLint("MissingOnRenderProcessGone")
private class CharacterClient(private val loader: WebViewAssetLoader, private val onGone: () -> Unit) :
  WebViewClientCompat() {
  // Offline only: our assets or a 404. The app has no INTERNET permission either.
  override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
    loader.shouldInterceptRequest(request.url)
      ?: WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

  override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
    Log.w(TAG, "renderer gone (crash=${detail.didCrash()}), showing the still")
    onGone()
    return true // handled: without this the whole app process dies with the renderer
  }
}

private const val TAG = "RinChar"
private const val BRIDGE = "RinBridge"
private const val FADE_MS = 300
/** Matches the page's mood blend (emotion.ts BLEND_S). */
private const val STILL_BLEND_MS = 200
private const val LOAD_TIMEOUT_MS = 10_000L
private const val STARTUP_MEASURE_MS = 10_000L

/** S2: a pixel ratio of 2 keeps GPU memory down at no visible cost. The frame-rate cap is per framing (Framing.fpsCap). */
private const val PIXEL_RATIO_CAP = 2
