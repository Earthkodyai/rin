package io.github.earthkodyai.rinalarm.character

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.util.Log
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
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
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow

/** Debug hook: every CharacterView crashes its renderer on request, to prove the fallback (DebugCharacterReceiver). */
object CharacterDebug {
  val crashRenderer = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
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
 * the screen is paused.
 */
@Composable
fun CharacterView(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val model = remember {
    CharacterAssets.model(context)?.takeIf { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }
  }
  var phase by remember { mutableStateOf(if (model == null) Phase.FALLBACK else Phase.LOADING) }
  val host = remember { CharacterHost() }
  val description = stringResource(R.string.character_description)

  Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
    if (model != null && phase != Phase.FALLBACK) {
      AndroidView(
        factory = { ctx ->
          host.create(ctx, model, onReady = { phase = Phase.READY }, onFailed = { phase = Phase.FALLBACK })
        },
        onRelease = { host.release() },
        modifier = Modifier.fillMaxSize(),
      )
    }
    AnimatedVisibility(phase != Phase.READY, enter = fadeIn(tween(FADE_MS)), exit = fadeOut(tween(FADE_MS))) {
      Image(
        painterResource(R.drawable.rin_still),
        contentDescription = null,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxHeight(),
      )
    }
  }

  LifecycleResumeEffect(host) {
    host.resume()
    onPauseOrDispose { host.pause() }
  }
  LaunchedEffect(phase) {
    if (phase != Phase.LOADING) return@LaunchedEffect
    delay(LOAD_TIMEOUT_MS)
    Log.w(TAG, "no first frame after $LOAD_TIMEOUT_MS ms, showing the still")
    phase = Phase.FALLBACK
  }
  LaunchedEffect(host) { CharacterDebug.crashRenderer.collect { host.crashRenderer() } }
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

  @SuppressLint("SetJavaScriptEnabled") // our own page from APK assets; nothing else can load (see the client)
  fun create(context: Context, model: String, onReady: () -> Unit, onFailed: () -> Unit): View {
    val t0 = System.currentTimeMillis() // before Chromium starts, so the load time includes WebView start-up
    WebView.setWebContentsDebuggingEnabled(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
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
      if (!resumed) proxy.postMessage(CharacterCommand.PAUSE.json)
      when (val parsed = CharacterMessage.parse(message.data ?: return@addWebMessageListener)) {
        is CharacterMessage.Ready -> {
          Log.i(TAG, "ready $parsed")
          onReady()
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
        .appendQueryParameter("fps", FPS_CAP.toString())
        .appendQueryParameter("pr", PIXEL_RATIO_CAP.toString())
        .appendQueryParameter("t0", t0.toString())
        .build()
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
    reply?.postMessage(CharacterCommand.RESUME.json)
  }

  fun pause() {
    resumed = false
    reply?.postMessage(CharacterCommand.PAUSE.json)
    webView?.onPause()
  }

  fun crashRenderer() {
    webView?.loadUrl("chrome://crash")
  }

  fun release() {
    reply = null
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
private const val LOAD_TIMEOUT_MS = 10_000L

/** S2: 30 fps is plenty for an idle character on a 120 Hz screen, and 2 keeps GPU memory down at no visible cost. */
private const val FPS_CAP = 30
private const val PIXEL_RATIO_CAP = 2
