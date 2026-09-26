package dev.rinalarm.spike.s2

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Hosts the three-vrm page from APK assets via WebViewAssetLoader and forwards
 * the page's measurements to logcat (tag S2), where s2.sh collects them.
 *
 * Launch extras: `q` = query string passed to the page (e.g. "run=pr2&pr=2&dur=30").
 */
class MainActivity : Activity() {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Taken before the WebView (and Chromium) is created, so "native to first
        // frame" includes WebView start-up, which the real app pays on every cold ring.
        val t0 = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        WebView.setWebContentsDebuggingEnabled(true)
        val wv = WebView(this)
        val tWebView = System.currentTimeMillis()
        wv.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }
        wv.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }
        // Origin-scoped bridge (not addJavascriptInterface): only our asset origin can post.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(wv, "S2Bridge", setOf(ORIGIN)) { _, message, _, _, _ ->
                Log.i(TAG, message.data ?: "")
            }
        } else {
            Log.e(TAG, "WEB_MESSAGE_LISTENER unsupported")
        }
        setContentView(wv)
        webView = wv

        val q = intent.getStringExtra("q") ?: "run=default"
        Log.i(TAG, """{"phase":"native","webViewCreateMs":${tWebView - t0},"webViewPkg":"${WebViewCompat.getCurrentWebViewPackage(this)?.versionName}"}""")
        wv.loadUrl("$ORIGIN/assets/index.html?$q&t0=$t0")
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "S2"
        const val ORIGIN = "https://appassets.androidplatform.net"
    }
}
