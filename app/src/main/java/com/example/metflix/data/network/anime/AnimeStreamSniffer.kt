package com.example.metflix.data.network.anime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.metflix.data.network.anime.models.AnimeExtractedStream
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "AnimeStreamSniffer"

// 15-second response budget for stream extraction
private const val SNIFFER_TIMEOUT_MS = 15_000L

private const val CHROME_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36"

@Singleton
class AnimeStreamSniffer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun sniffStream(
        embedUrl: String,
        preferredLanguage: String = "hin"
    ): AnimeExtractedStream? = withContext(Dispatchers.Main) {
        val cleanUrl = embedUrl.trim()
        if (cleanUrl.isBlank()) return@withContext null

        Log.i(TAG, "sniffStream START: $cleanUrl (lang=$preferredLanguage)")

        withTimeoutOrNull(SNIFFER_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val referer = if (cleanUrl.contains("abyss") || cleanUrl.contains("dub-player")) {
                    "https://anivara.to/"
                } else try {
                    val uri = Uri.parse(cleanUrl); "${uri.scheme}://${uri.host}/"
                } catch (_: Exception) { cleanUrl }

                val origin = try {
                    val uri = Uri.parse(referer); "${uri.scheme}://${uri.host}"
                } catch (_: Exception) { referer.removeSuffix("/") }

                val fired = AtomicBoolean(false)
                var webView: WebView? = null
                val mainHandler = Handler(Looper.getMainLooper())

                fun cleanup() {
                    try {
                        webView?.apply {
                            stopLoading()
                            loadUrl("about:blank")
                            clearHistory()
                            removeAllViews()
                            webChromeClient = null
                            webViewClient = object : WebViewClient() {}
                            removeJavascriptInterface("SnifferBridge")
                            destroy()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "cleanup: ${e.message}")
                    } finally {
                        webView = null
                        System.gc()
                    }
                }

                fun fire(url: String) {
                    if (!fired.compareAndSet(false, true)) return
                    Log.i(TAG, "Sniffer captured stream: $url")

                    val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
                    val headers = mutableMapOf(
                        "User-Agent" to CHROME_USER_AGENT,
                        "Referer" to referer,
                        "Origin" to origin,
                        "Accept" to "*/*",
                        "Accept-Language" to "en-US,en;q=0.9",
                        "Sec-Fetch-Site" to "same-origin",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-CH-UA-Platform" to "\"Android\"",
                        "Sec-CH-UA" to "\"Chromium\";v=\"124\", \"Not:A-Brand\";v=\"8\", \"Google Chrome\";v=\"124\"",
                        "Sec-CH-UA-Mobile" to "?1"
                    )
                    if (cookies.isNotBlank()) headers["Cookie"] = cookies

                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(
                            AnimeExtractedStream(
                                streamUrl = url,
                                headers = headers,
                                isDirectStream = true
                            )
                        )
                    }
                }

                continuation.invokeOnCancellation { cleanup() }

                try {
                    val wv = createSnifferWebView(
                        context = context,
                        referer = referer,
                        origin = origin,
                        preferredLanguage = preferredLanguage,
                        onStreamCaptured = { url -> mainHandler.post { fire(url) } }
                    )
                    webView = wv

                    val requestHeaders = mapOf(
                        "Referer" to referer,
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "cross-site",
                        "Sec-Fetch-Dest" to "iframe"
                    )
                    wv.loadUrl(cleanUrl, requestHeaders)
                } catch (e: Exception) {
                    Log.e(TAG, "Sniffer failed to start: ${e.message}", e)
                    cleanup()
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createSnifferWebView(
        context: Context,
        referer: String,
        origin: String,
        preferredLanguage: String,
        onStreamCaptured: (String) -> Unit
    ): WebView {
        return WebView(context).apply {
            measure(
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 1920, 1080)
            isFocusable = false
            isFocusableInTouchMode = false

            CookieManager.getInstance().let { cm ->
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(this, true)
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT

                // ⭐ DON'T block images/fonts — players fail to initialize without them
                loadsImagesAutomatically = true
                blockNetworkImage = false

                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                userAgentString = CHROME_USER_AGENT
            }

            // ⭐ JS → Kotlin bridge. Captures every fetch/XHR/video.src hit.
            addJavascriptInterface(
                Bridge { url -> if (isMediaUrl(url)) onStreamCaptured(url) },
                "SnifferBridge"
            )

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean = false

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.cancel(); return true
                }
                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.cancel(); return true
                }
                override fun onJsPrompt(
                    view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?
                ): Boolean {
                    result?.cancel(); return true
                }
                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                    Log.d(TAG, "[JS] ${msg?.message()}")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val t = request?.url?.toString().orEmpty().lowercase()
                    // Block non-HTTP and obvious ad popups; allow everything else
                    if (!t.startsWith("http://") && !t.startsWith("https://")) return true
                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString().orEmpty()
                    val l = url.lowercase()

                    // ⭐ Primary detection: fires for ALL requests including iframe fetches
                    if (isMediaUrl(url)) {
                        Log.i(TAG, "shouldInterceptRequest → media: $url")
                        onStreamCaptured(url)
                        // Return empty to stop download in this background WebView
                        return WebResourceResponse(
                            "text/plain", "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }

                    // Only block real ad hosts, NOT file extensions
                    if (isAdHost(l)) {
                        return WebResourceResponse(
                            "text/plain", "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "onPageStarted: $url")

                    // Install hooks BEFORE page scripts run
                    view?.evaluateJavascript(HOOKS_JS, null)
                    // Start clicking overlays/play
                    view?.evaluateJavascript(clickScript(preferredLanguage), null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "onPageFinished: $url")
                    // Second pass in case first ran too early or page re-rendered
                    view?.evaluateJavascript(HOOKS_JS, null)
                    view?.evaluateJavascript(clickScript(preferredLanguage), null)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        Log.w(TAG, "Main-frame load error: ${error?.description} for ${request.url}")
                    }
                }
            }
        }
    }

    private fun isMediaUrl(url: String): Boolean {
        if (url.isEmpty()) return false
        val l = url.lowercase()
        if (!(l.contains(".m3u8") || l.contains(".mp4"))) return false
        if (l.contains("poster") || l.contains("thumbnail") || l.contains("dummy")) return false
        return true
    }

    private fun isAdHost(lowerUrl: String): Boolean {
        val ads = listOf(
            "popcash", "clickadu", "decafeligiblyhad", "morphify",
            "doubleclick", "googlesyndication", "adsterra", "adtrue",
            "propellerads", "monetag", "hilltopads", "trafficjunky",
            "yllix", "adsco.re", "mgid", "taboola", "outbrain",
            "revcontent", "popads", "popunder", "admaven", "bidvertiser",
            "google-analytics", "googletagmanager", "histats"
        )
        return ads.any { lowerUrl.contains(it) }
    }

    private fun clickScript(lang: String): String = """
        (function() {
          if (window.__mfClickInstalled) return;
          window.__mfClickInstalled = true;
          window.open = function() { return null; };

          // Select preferred audio language if the page exposes language buttons
          try {
            var opt = document.querySelector('.opt[data-lang="$lang"]');
            if (opt) opt.click();
          } catch(e){}

          var step = 0;
          var timer = setInterval(function() {
            step++;
            try {
              var sels = [
                '.jw-display-icon-container',
                'button.jw-icon-playback',
                '.vjs-big-play-button',
                '.play-button', 'button.play',
                '[aria-label="Play"]', '[aria-label="play"]',
                '.plyr__control--overlaid',
                'video'
              ];
              for (var i = 0; i < sels.length; i++) {
                var b = document.querySelector(sels[i]);
                if (b) {
                  try { b.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true})); } catch(e){}
                  try { b.click(); } catch(e){}
                }
              }
              var cx = window.innerWidth / 2;
              var cy = window.innerHeight / 2;
              var el = document.elementFromPoint(cx, cy);
              if (el) {
                ['pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(n){
                  try {
                    el.dispatchEvent(new MouseEvent(n, {bubbles:true, cancelable:true, view:window, clientX:cx, clientY:cy}));
                  } catch(e){}
                });
                try { if (typeof el.click === 'function') el.click(); } catch(e){}
              }
              var ov = document.getElementById('overlay');
              if (ov) { try { ov.click(); } catch(e){} }
            } catch(e){}
            if (step >= 15) clearInterval(timer);
          }, 700);
        })();
    """.trimIndent()

    class Bridge(private val onUrl: (String) -> Unit) {
        @JavascriptInterface
        fun onUrlFound(url: String) {
            try { if (url.isNotBlank()) onUrl(url) } catch (_: Throwable) {}
        }
    }

    companion object {
        // Installs fetch/XHR/video.src hooks so ANY request path is caught
        private val HOOKS_JS = """
            (function() {
              if (window.__mfHooksInstalled) return;
              window.__mfHooksInstalled = true;

              function report(u) {
                try { if (u) SnifferBridge.onUrlFound(String(u)); } catch(e){}
              }

              // 1. Patch fetch
              try {
                var of = window.fetch;
                if (of) {
                  window.fetch = function(input) {
                    try {
                      var u = (typeof input === 'string') ? input : (input && input.url);
                      if (u) report(u);
                    } catch(e){}
                    return of.apply(this, arguments);
                  };
                }
              } catch(e){}

              // 2. Patch XHR.open
              try {
                var xo = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                  try { if (url) report(url); } catch(e){}
                  return xo.apply(this, arguments);
                };
              } catch(e){}

              // 3. Patch HTMLMediaElement.src setter
              try {
                var d = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
                if (d && d.set) {
                  Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                    configurable: true,
                    get: d.get,
                    set: function(v) {
                      try { if (v) report(v); } catch(e){}
                      return d.set.call(this, v);
                    }
                  });
                }
              } catch(e){}

              // 4. Periodic scan of <video> elements
              setInterval(function() {
                try {
                  var vs = document.querySelectorAll('video');
                  for (var i = 0; i < vs.length; i++) {
                    var v = vs[i];
                    if (v.currentSrc) report(v.currentSrc);
                    if (v.src) report(v.src);
                    if (v.paused) { try { v.play(); } catch(e){} }
                  }
                } catch(e){}
              }, 500);
            })();
        """.trimIndent()
    }
}