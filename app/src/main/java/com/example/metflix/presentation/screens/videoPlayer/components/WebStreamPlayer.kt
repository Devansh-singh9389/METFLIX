/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.metflix.presentation.screens.videoPlayer.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

private const val TAG = "WebStreamPlayer"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/**
 * TV-optimized WebView Player for Web Embed streaming providers (VidLink, SuperEmbed, AutoEmbed, 2Embed).
 *
 * Features:
 * - Full-screen hardware-accelerated playback
 * - Automatic ad & popup suppression
 * - TV Remote D-Pad / media key control (play, pause, seek)
 * - Dynamic M3U8/MP4 video stream sniffer
 * - Graceful error reporting
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebStreamPlayer(
    url: String,
    modifier: Modifier = Modifier,
    onCapturedDirectStream: (streamUrl: String, headers: Map<String, String>) -> Unit = { _, _ -> },
    onError: (errorMessage: String) -> Unit = {},
    onToggleControls: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(url) {
        onDispose {
            webViewRef?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up WebView: ${e.message}")
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isFocusable = true
                    isFocusableInTouchMode = true
                    keepScreenOn = true
                    setBackgroundColor(android.graphics.Color.BLACK)

                    // Enable HTML5 cookies
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        userAgentString = DESKTOP_USER_AGENT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT

                        // Suppress popups
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        allowFileAccess = false
                        allowContentAccess = true
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            // Block any popup window requests
                            return false
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress > 75) {
                                isLoading = false
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        private var hasCapturedPrimaryStream = false

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUrl = request?.url?.toString() ?: return false
                            val uri = request.url ?: return false
                            val initialHost = Uri.parse(url).host.orEmpty().lowercase()
                            val targetHost = uri.host.orEmpty().lowercase()

                            // Allow same domain or subdomains
                            if (targetHost.isNotEmpty() && (targetHost == initialHost || targetHost.endsWith(".$initialHost"))) {
                                return false
                            }

                            // Allow known video streaming endpoints and CDNs
                            val isAllowedCdn = targetHost.endsWith("vidlink.pro") ||
                                    targetHost.endsWith("vidsrc.to") ||
                                    targetHost.endsWith("vidsrc.me") ||
                                    targetHost.endsWith("superembed.stream") ||
                                    targetHost.endsWith("autoembed.to") ||
                                    targetHost.endsWith("cloudflare.com") ||
                                    targetHost.endsWith("cloudflarestorage.com") ||
                                    targetHost.endsWith("fastly.net") ||
                                    targetHost.endsWith("akamaihd.net") ||
                                    targetUrl.contains(".m3u8") ||
                                    targetUrl.contains(".mp4")

                            if (isAllowedCdn) {
                                return false
                            }

                            // Block ad popups, redirections, app links
                            Log.d(TAG, "Suppressed ad/redirect popup to: $targetUrl")
                            return true
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString()
                            if (reqUrl != null && !hasCapturedPrimaryStream) {
                                val lower = reqUrl.lowercase()
                                val isMedia = (lower.contains(".m3u8") || lower.contains(".mp4"))
                                val isAdOrTracking = lower.contains("doubleclick") ||
                                        lower.contains("googleads") ||
                                        lower.contains("adnxs") ||
                                        lower.contains("analytics")
                                val isSubtitleOrAudio = lower.contains("sub") || lower.contains(".vtt") || lower.contains("audio")

                                if (isMedia && !isAdOrTracking && !isSubtitleOrAudio) {
                                    hasCapturedPrimaryStream = true
                                    Log.d(TAG, "Captured primary direct stream URL: $reqUrl")
                                    onCapturedDirectStream(reqUrl, request.requestHeaders.orEmpty())
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, finishUrl: String?) {
                            super.onPageFinished(view, finishUrl)
                            isLoading = false

                            // Inject styles and script to maximize video element, suppress overlay banners, and auto-unmute
                            val script = """
                                (function() {
                                    var style = document.createElement('style');
                                    style.type = 'text/css';
                                    style.innerHTML = `
                                        body, html { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; overflow: hidden !important; background: #000 !important; }
                                        video { width: 100vw !important; height: 100vh !important; object-fit: contain !important; position: fixed !important; top: 0 !important; left: 0 !important; z-index: 99999 !important; }
                                        [id*='banner'], [class*='banner'], [id*='pop'], [class*='pop'], [id*='ads'], [class*='ads'] { display: none !important; }
                                    `;
                                    document.head.appendChild(style);

                                    // Auto play if paused
                                    var vids = document.querySelectorAll('video');
                                    vids.forEach(function(v) {
                                        v.play().catch(function(e) {});
                                    });
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(script, null)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                val msg = error?.description?.toString() ?: "Failed to load embed page"
                                Log.w(TAG, "WebStream error: $msg")
                                onError(msg)
                            }
                        }
                    }

                    // TV Remote Key Interception for Play/Pause and Seek
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    val toggleJs = """
                                        (function() {
                                            var v = document.querySelector('video');
                                            if (v) {
                                                if (v.paused) { v.play(); } else { v.pause(); }
                                            } else {
                                                var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
                                                if (el) el.click();
                                            }
                                        })();
                                    """.trimIndent()
                                    evaluateJavascript(toggleJs, null)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                    val seekRewindJs = """
                                        (function() {
                                            var v = document.querySelector('video');
                                            if (v) { v.currentTime = Math.max(0, v.currentTime - 10); }
                                        })();
                                    """.trimIndent()
                                    evaluateJavascript(seekRewindJs, null)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                    val seekForwardJs = """
                                        (function() {
                                            var v = document.querySelector('video');
                                            if (v) { v.currentTime = Math.min(v.duration || Infinity, v.currentTime + 10); }
                                        })();
                                    """.trimIndent()
                                    evaluateJavascript(seekForwardJs, null)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_MENU -> {
                                    onToggleControls()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }

                    loadUrl(url)
                    webViewRef = this
                }
            },
            update = { wv ->
                if (wv.url != url) {
                    wv.loadUrl(url)
                }
            }
        )

        if (isLoading) {
            VideoPlayerBufferingSpinner(
                label = "Loading web stream player...",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
