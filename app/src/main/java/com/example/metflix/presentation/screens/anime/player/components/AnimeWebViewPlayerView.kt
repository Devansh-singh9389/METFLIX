package com.example.metflix.presentation.screens.anime.player.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metflix.presentation.screens.anime.player.AnimePlayerViewModel
import java.io.ByteArrayInputStream

private const val TAG = "AnimeWebViewPlayer"

class AnimeWebBridge(
    private val onProgress: (currentMs: Long, totalMs: Long, bufferedMs: Long) -> Unit,
    private val onSpeed: (speedBps: Long, totalBytes: Long) -> Unit,
    private val onPlayState: (isPlaying: Boolean) -> Unit,
    private val onVideoDiagnostic: (width: Int, height: Int) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface fun reportPlayback(c: Float, t: Float, b: Float) {
        handler.post { onProgress((c * 1000).toLong(), (t * 1000).toLong(), (b * 1000).toLong()) }
    }
    @JavascriptInterface fun reportNetworkSpeed(bps: Long, total: Long) {
        handler.post { onSpeed(bps, total) }
    }
    @JavascriptInterface fun reportPlayState(p: Boolean) {
        handler.post { onPlayState(p) }
    }
    @JavascriptInterface fun reportVideoSize(w: Int, h: Int) {
        handler.post { onVideoDiagnostic(w, h) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AnimeWebViewPlayerView(
    embedUrl: String,
    viewModel: AnimePlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Seek / play-pause / audio / center-click commands stay the same as before
    val seekTarget = viewModel.seekEvent
    LaunchedEffect(seekTarget.value) {
        seekTarget.value?.let { ms ->
            val s = ms / 1000.0
            webViewRef?.evaluateJavascript(
                "var v=document.querySelector('video');if(v){v.currentTime=$s;}" +
                "if(window.jwplayer){try{window.jwplayer().seek($s);}catch(e){}}", null
            )
            viewModel.onSeekEventConsumed()
        }
    }

    val playPauseCommand = viewModel.playPauseCommand
    LaunchedEffect(playPauseCommand.value) {
        playPauseCommand.value?.let { shouldPlay ->
            val js = if (shouldPlay)
                "var v=document.querySelector('video');if(v&&v.paused){v.play().catch(function(){});}"
            else
                "var v=document.querySelector('video');if(v&&!v.paused){v.pause();}"
            webViewRef?.evaluateJavascript(js, null)
            viewModel.onPlayPauseCommandConsumed()
        }
    }

    val selectedAudio = viewModel.selectedAudioTrack
    LaunchedEffect(selectedAudio.value) {
        webViewRef?.evaluateJavascript(
            "var c='${selectedAudio.value.code}';" +
            "if(window.postMessage)window.postMessage({type:'anivara:audio',lang:c},'*');" +
            "var o=document.querySelector('.opt[data-lang=\"'+c+'\"]');if(o)o.click();", null
        )
    }

    val centerClickTrigger by viewModel.webViewCenterClickEvent.collectAsStateWithLifecycle()
    LaunchedEffect(centerClickTrigger) {
        val triggerTime = centerClickTrigger
        if (triggerTime != null && webViewRef != null) {
            val wv = webViewRef!!
            val w = wv.width.toFloat().takeIf { it > 0 } ?: 1920f
            val h = wv.height.toFloat().takeIf { it > 0 } ?: 1080f
            val cx = w / 2f; val cy = h / 2f
            val now = SystemClock.uptimeMillis()
            try {
                val d = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cx, cy, 0)
                val u = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, cx, cy, 0)
                wv.dispatchTouchEvent(d); wv.dispatchTouchEvent(u)
                d.recycle(); u.recycle()
            } catch (_: Exception) {}
            wv.evaluateJavascript("""
                (function(){
                  var cx=window.innerWidth/2,cy=window.innerHeight/2;
                  var el=document.elementFromPoint(cx,cy);
                  if(el){['pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(n){
                    try{el.dispatchEvent(new MouseEvent(n,{bubbles:true,cancelable:true,view:window,clientX:cx,clientY:cy}));}catch(e){}
                  });try{el.click();}catch(e){}}
                  var p=document.querySelector('.jw-display-icon-container,button.play,.play-button,.vjs-big-play-button,video');
                  if(p){try{p.click();}catch(e){}}
                })();
            """.trimIndent(), null)
            viewModel.onCenterClickConsumed()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                createConfiguredWebView(ctx, viewModel).also { wv ->
                    webViewRef = wv
                    wv.loadUrl(embedUrl, buildCustomHeaders(embedUrl))
                }
            },
            update = { wv -> if (wv.url != embedUrl) wv.loadUrl(embedUrl, buildCustomHeaders(embedUrl)) }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading(); webViewRef?.destroy(); webViewRef = null
        }
    }
}

private fun buildCustomHeaders(embedUrl: String): Map<String, String> {
    val referer = if (embedUrl.contains("abyss") || embedUrl.contains("dub-player"))
        "https://anivara.to/"
    else try {
        val u = Uri.parse(embedUrl); "${u.scheme}://${u.host}/"
    } catch (_: Exception) { embedUrl }
    return mapOf(
        "Referer" to referer,
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "cross-site",
        "Sec-Fetch-Dest" to "iframe"
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(context: Context, viewModel: AnimePlayerViewModel): WebView {
    return WebView(context).apply {
        isFocusable = false
        isFocusableInTouchMode = false

        // ⭐ CRITICAL FIX for MediaTek 512 MB: hardware layer makes the video
        // SurfaceTexture fail to composite → audio plays, screen is black.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            blockNetworkImage = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36"
        }

        val bridge = AnimeWebBridge(
            onProgress = { c, t, b -> viewModel.updatePlaybackProgress(c, t, b) },
            onSpeed = { s, tot -> viewModel.updateDownloadMetrics(s, tot) },
            onPlayState = {},
            onVideoDiagnostic = { w, h ->
                if (w > 0 && h > 0) {
                    Log.i(TAG, "WebView video decoding OK: ${w}x${h}")
                    viewModel.updateVideoFormat("${w}x${h}", "WebView")
                } else {
                    Log.e(TAG, "WebView video NOT decoding (videoWidth=0). " +
                            "Source is likely HEVC — this TV cannot decode HEVC.")
                }
            }
        )
        addJavascriptInterface(bridge, "MetFlixBridge")

        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView?, d: Boolean, g: Boolean, m: android.os.Message?) = false
            override fun onJsAlert(v: WebView?, u: String?, msg: String?, r: JsResult?): Boolean {
                r?.cancel()
                return true
            }
            override fun onJsConfirm(v: WebView?, u: String?, msg: String?, r: JsResult?): Boolean {
                r?.cancel()
                return true
            }
            override fun onJsPrompt(v: WebView?, u: String?, msg: String?, d: String?, r: JsPromptResult?): Boolean {
                r?.cancel()
                return true
            }
            override fun onJsBeforeUnload(v: WebView?, u: String?, msg: String?, r: JsResult?): Boolean {
                r?.confirm()
                return true
            }
        }

        webViewClient = object : WebViewClient() {
            private val blockedDomains = listOf(
                "decafeligiblyhad.com","adblock","morphify.net","popcash","clickadu",
                "doubleclick","googlesyndication","adnxs","exoclick","propellerads",
                "monetag","hilltopads","trafficjunky","adtrue","adsterra","yllix",
                "google-analytics","googletagmanager","histats","adsco.re",
                "onclickperformance","richaudience","mgid","taboola","outbrain",
                "revcontent","popads","popunder","admaven","bidvertiser","adcash"
            )
            private fun isAllowed(t: String): Boolean {
                val l = t.lowercase()
                if (!l.startsWith("http://") && !l.startsWith("https://")) return false
                if (blockedDomains.any { l.contains(it) }) return false
                return true
            }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?) =
                !isAllowed(r?.url?.toString().orEmpty())
            override fun shouldInterceptRequest(v: WebView?, r: WebResourceRequest?): WebResourceResponse? {
                val url = r?.url?.toString().orEmpty().lowercase()
                if (blockedDomains.any { url.contains(it) }) {
                    return WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(v, r)
            }
            override fun onPageStarted(v: WebView?, u: String?, f: Bitmap?) {
                super.onPageStarted(v, u, f)
                v?.evaluateJavascript(
                    "(function(){window.open=function(){return null;};window.alert=function(){};" +
                    "window.confirm=function(){return false;};})();", null
                )
            }
            override fun onPageFinished(v: WebView?, u: String?) {
                super.onPageFinished(v, u)
                injectObserver(v)
            }
        }
    }
}

private fun injectObserver(webView: WebView?) {
    val js = """
        (function(){
          if(window._mfInjected) return;
          window._mfInjected = true;
          try{Object.defineProperty(window,'open',{value:function(){return null;},writable:false,configurable:false});}
          catch(e){window.open=function(){return null;};}
          var st=document.createElement('style');
          st.textContent='html,body{width:100%!important;height:100%!important;margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;display:flex!important;align-items:center!important;justify-content:center!important}iframe,video,#player,.player,.jwplayer,.jw-media{width:100%!important;height:100%!important;max-width:100vw!important;max-height:100vh!important;object-fit:contain!important;border:none!important}';
          document.head.appendChild(st);
          setInterval(function(){
            var v=document.querySelector('video');
            if(v){
              try{v.setAttribute('playsinline','');v.setAttribute('webkit-playsinline','');}catch(e){}
              var c=v.currentTime||0,d=v.duration||0,b=0;
              if(v.buffered&&v.buffered.length>0){
                try{for(var i=0;i<v.buffered.length;i++){
                  if(v.buffered.start(i)<=c&&v.buffered.end(i)>=c){b=v.buffered.end(i);break;}
                }}catch(e){}
              }
              if(window.MetFlixBridge&&window.MetFlixBridge.reportPlayback)
                window.MetFlixBridge.reportPlayback(c,d,b);
              if(window.MetFlixBridge&&window.MetFlixBridge.reportVideoSize)
                window.MetFlixBridge.reportVideoSize(v.videoWidth||0,v.videoHeight||0);
              if(v.paused&&c===0){v.muted=false;v.play().catch(function(){});}
            }
            try{
              var links=document.querySelectorAll('a[target="_blank"]');
              for(var k=0;k<links.length;k++){
                links[k].removeAttribute('target');
                links[k].onclick=function(ev){ev.preventDefault();ev.stopPropagation();return false;};
              }
            }catch(e){}
          },1000);
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}
