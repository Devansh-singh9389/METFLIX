package com.example.metflix.presentation.screens.anime.player.components

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.example.metflix.data.player.MediaCacheManager
import com.example.metflix.presentation.screens.anime.player.AnimePlayerViewModel
import kotlinx.coroutines.delay

private const val TAG = "AnimeExo"

@OptIn(UnstableApi::class)
@Composable
fun AnimeExoPlayerView(
    streamUrl: String,
    headers: Map<String, String>,
    viewModel: AnimePlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var useSoftwareDecode by remember(streamUrl) { mutableStateOf(false) }

    val bandwidthMeter = remember(context) {
        DefaultBandwidthMeter.Builder(context).build()
    }

    // Build a player with correct data source + cache + optional software renderer
    val exoPlayer = remember(streamUrl, headers, useSoftwareDecode, bandwidthMeter) {
        val cleanHeaders = headers.toMutableMap().apply {
            if (!containsKey("Accept")) put("Accept", "*/*")
            if (!containsKey("Accept-Language")) put("Accept-Language", "en-US,en;q=0.9")
            if (!containsKey("Sec-Fetch-Site")) put("Sec-Fetch-Site", "same-origin")
            if (!containsKey("Sec-Fetch-Mode")) put("Sec-Fetch-Mode", "cors")
            if (!containsKey("Sec-Fetch-Dest")) put("Sec-Fetch-Dest", "empty")
            if (!containsKey("Sec-CH-UA-Platform")) put("Sec-CH-UA-Platform", "\"Windows\"")
            if (!containsKey("Sec-CH-UA")) put("Sec-CH-UA", "\"Chromium\";v=\"124\", \"Not:A-Brand\";v=\"8\", \"Google Chrome\";v=\"124\"")
            if (!containsKey("Sec-CH-UA-Mobile")) put("Sec-CH-UA-Mobile", "?0")
        }

        val userAgent = cleanHeaders.remove("User-Agent")
            ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(cleanHeaders)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)

        // ⭐ Wrap with MediaCacheManager SimpleCache (512 MB disk cache)
        val cachedFactory = MediaCacheManager.createCacheDataSourceFactory(context, http)

        // LoadControl tuned for 12 sec RAM buffer + 48 sec device storage buffer (60 sec max total buffer)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 12_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setBackBuffer(0, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .apply {
                if (useSoftwareDecode) {
                    Log.w(TAG, "Building ExoPlayer with SOFTWARE codec preference")
                    setMediaCodecSelector(
                        MediaCodecSelector.PREFER_SOFTWARE
                    )
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                } else {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                }
            }

        ExoPlayer.Builder(context, renderersFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cachedFactory))
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredVideoMimeType(MimeTypes.VIDEO_H264)
                    .build()
            }
    }

    // Load media when URL changes
    LaunchedEffect(streamUrl, exoPlayer) {
        if (streamUrl.isNotBlank()) {
            exoPlayer.stop() // Cleanly flush hardware codec before loading new stream
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(streamUrl))
                .apply {
                    val lower = streamUrl.lowercase()
                    if (lower.contains(".m3u8") || lower.contains(".txt") || lower.contains("/hls/")) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (lower.contains(".mp4")) {
                        setMimeType(MimeTypes.VIDEO_MP4)
                    }
                }
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Seek
    val seekTarget by viewModel.seekEvent.collectAsState()
    LaunchedEffect(seekTarget) {
        seekTarget?.let {
            exoPlayer.seekTo(it)
            viewModel.onSeekEventConsumed()
        }
    }

    // Play/pause
    val playPause by viewModel.playPauseCommand.collectAsState()
    LaunchedEffect(playPause) {
        playPause?.let {
            if (it) exoPlayer.play() else exoPlayer.pause()
            viewModel.onPlayPauseCommandConsumed()
        }
    }

    // Audio language
    val selectedAudio by viewModel.selectedAudioTrack.collectAsState()
    LaunchedEffect(selectedAudio) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(selectedAudio.code)
            .build()
    }

    // Quality selection — when a specific quality is selected, disable ABR & lock track; when Auto, re-enable ABR
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    LaunchedEffect(selectedQuality) {
        val targetHeight = selectedQuality.height
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
            .setPreferredVideoMimeType(MimeTypes.VIDEO_H264)

        if (targetHeight != null) {
            // Specific quality selected -> DISABLE ABR and lock track via TrackSelectionOverride
            var overrideSet = false
            val currentTracks = exoPlayer.currentTracks
            for (group in currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        if (format.height == targetHeight) {
                            builder.setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, i)
                            )
                            overrideSet = true
                            break
                        }
                    }
                }
                if (overrideSet) break
            }
            builder.setMinVideoSize(1, targetHeight)
                .setMaxVideoSize(Int.MAX_VALUE, targetHeight)
                .setMaxVideoBitrate(Int.MAX_VALUE)
        } else {
            // "Auto" selected -> RE-ENABLE ABR Bandwidth Feeding
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .clearVideoSizeConstraints()
                .setMaxVideoBitrate(Int.MAX_VALUE)
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    // Listeners + diagnostics
    DisposableEffect(exoPlayer, useSoftwareDecode) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val msg = (error.cause?.message ?: error.message).orEmpty()
                Log.e(TAG, "Player error code=${error.errorCodeName} msg=$msg", error)

                // One automatic retry with software decoder for codec init failures
                if (!useSoftwareDecode && (
                        msg.contains("decoder", true) ||
                        msg.contains("codec", true) ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                    )) {
                    Log.w(TAG, "Retrying with software decoder…")
                    useSoftwareDecode = true
                } else {
                    viewModel.onExoPlayerPlaybackError(
                        "Playback error (${error.errorCodeName}): ${msg.take(180)}"
                    )
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val availableHeights = mutableListOf<Int>()
                for (g in tracks.groups) {
                    if (g.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until g.length) {
                            val f = g.getTrackFormat(i)
                            if (f.height > 0) {
                                availableHeights.add(f.height)
                            }
                            if (g.isTrackSelected(i)) {
                                val line = "SELECTED VIDEO: codecs=${f.codecs} ${f.width}x${f.height} " +
                                        "mime=${f.sampleMimeType} bitrate=${f.bitrate}"
                                Log.i(TAG, line)
                                viewModel.updateVideoFormat(
                                    "${f.width}x${f.height}",
                                    "${(f.bitrate / 1000).coerceAtLeast(0)} kbps • ${f.codecs ?: "?"}"
                                )
                            }
                        }
                    }
                }
                if (availableHeights.isNotEmpty()) {
                    viewModel.updateAvailableQualities(availableHeights)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Progress & network speed measurement loop
    LaunchedEffect(exoPlayer, bandwidthMeter) {
        while (true) {
            viewModel.updatePlaybackProgress(
                exoPlayer.currentPosition.coerceAtLeast(0L),
                exoPlayer.duration.coerceAtLeast(0L),
                exoPlayer.bufferedPosition.coerceAtLeast(0L)
            )
            val speedBps = bandwidthMeter.bitrateEstimate
            if (speedBps > 0) {
                viewModel.updateDownloadMetrics(speedBps / 8, 0L)
            }
            delay(500)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        PlayerSurface(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }
}
