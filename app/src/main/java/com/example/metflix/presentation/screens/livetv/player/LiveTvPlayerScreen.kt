package com.example.metflix.presentation.screens.livetv.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import android.util.Log
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.metflix.data.drm.DeviceCapabilityManager
import com.example.metflix.data.drm.WidevineLevel
import com.example.metflix.data.entities.LiveChannel
import com.example.metflix.presentation.common.Error
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.screens.livetv.components.LiveBadge
import com.example.metflix.presentation.screens.livetv.player.components.LiveTvAspectRatio
import com.example.metflix.presentation.screens.livetv.player.components.LiveTvChannelDrawer
import com.example.metflix.presentation.screens.livetv.player.components.LiveTvNumberInputOverlay
import com.example.metflix.presentation.screens.livetv.player.components.LiveTvSettingsOverlay
import com.example.metflix.presentation.screens.livetv.player.components.LiveTvStatsOverlay
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerBufferingSpinner
import com.example.metflix.presentation.screens.videoPlayer.components.rememberPlayer
import com.example.metflix.presentation.theme.MetFlixCardShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object LiveTvPlayerScreen {
    const val ChannelIdBundleKey = "channelId"
}

@Composable
fun LiveTvPlayerScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveTvPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is LiveTvPlayerUiState.Loading -> {
            Loading(modifier = modifier.fillMaxSize())
        }

        is LiveTvPlayerUiState.Error -> {
            Error(modifier = modifier.fillMaxSize())
        }

        is LiveTvPlayerUiState.Ready -> {
            LiveTvPlayerContent(
                state = state,
                onNextChannel = viewModel::nextChannel,
                onPreviousChannel = viewModel::previousChannel,
                onSelectChannel = viewModel::selectChannel,
                onSelectChannelByNumber = viewModel::selectChannelByNumber,
                onToggleFavorite = viewModel::toggleFavorite,
                onBackPressed = onBackPressed,
                modifier = modifier
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LiveTvPlayerContent(
    state: LiveTvPlayerUiState.Ready,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    onSelectChannel: (LiveChannel) -> Unit,
    onSelectChannelByNumber: (Int) -> Boolean,
    onToggleFavorite: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val deviceProfile = remember { DeviceCapabilityManager().profile }
    val exoPlayer = rememberPlayer(context, state.currentChannel.requestHeaders)

    var isDrawerOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isStatsVisible by remember { mutableStateOf(false) }
    var isOsdVisible by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // Stream Resilience & Auto-Reconnect States
    var isBuffering by remember { mutableStateOf(true) }
    var isReconnecting by remember { mutableStateOf(false) }
    var reconnectAttempt by remember { mutableIntStateOf(0) }
    var isManualError by remember { mutableStateOf(false) }

    var currentDownloadSpeedBps by remember { mutableLongStateOf(0L) }
    var totalStreamBytesDownloaded by remember { mutableLongStateOf(0L) }
    var lastUserInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun isCleartextPlaybackError(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is androidx.media3.datasource.HttpDataSource.CleartextNotPermittedException ||
                cause.javaClass.name.contains("CleartextNotPermitted", ignoreCase = true) ||
                cause.message?.contains("cleartext", ignoreCase = true) == true
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    fun isTerminalPlaybackError(error: PlaybackException): Boolean {
        return when {
            isCleartextPlaybackError(error) -> true
            error.errorCode in 3000..3999 -> true // Media/container/format errors
            error.errorCode in 4000..7999 -> true // Decoder, audio/video, and DRM errors
            error.cause?.javaClass?.name?.contains("Drm", ignoreCase = true) == true -> true
            error.cause?.javaClass?.name?.contains("Decoder", ignoreCase = true) == true -> true
            else -> false
        }
    }

    fun terminalErrorMessage(error: PlaybackException): String {
        val details = generateSequence<Throwable>(error) { it.cause }
            .joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }
            .lowercase()
        return when {
            isCleartextPlaybackError(error) ->
                "This channel uses an HTTP stream. Android allows it, but HTTPS is recommended."
            details.contains("invalidresponsecode") &&
                (details.contains("401") || details.contains("403")) ->
                "The channel rejected the request (HTTP 401/403). Check its authorized headers."
            details.contains("key") &&
                (details.contains("timeout") || details.contains("source error")) ->
                "The HLS encryption key could not be loaded. Check the key URL and headers."
            details.contains("parser") || details.contains("pesreader") ->
                "The channel format could not be parsed. The provider stream may be malformed."
            error.errorCode in 6000..6999 -> "DRM playback failed. Check the channel license or device support."
            error.errorCode in 3000..3999 -> "This channel uses an unsupported media format."
            error.errorCode in 4000..5999 || error.errorCode in 7000..7999 ->
                "This channel uses an unsupported audio or video codec."
            else -> error.localizedMessage ?: "Stream unavailable"
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val currentEpg = remember(state.currentChannel, currentTimeMs) {
        state.currentChannel.getCurrentEpgProgram(currentTimeMs)
    }
    val nextEpg = remember(state.currentChannel, currentTimeMs) {
        state.currentChannel.getNextEpgProgram(currentTimeMs)
    }

    // Settings States
    var currentAspectRatio by remember { mutableStateOf(LiveTvAspectRatio.Fit) }
    var currentQualityMaxHeight by remember { mutableIntStateOf(0) }
    var currentSleepTimerMinutes by remember { mutableIntStateOf(0) }

    // Stream Diagnostics States
    var videoResolution by remember { mutableStateOf("") }
    var videoBitrate by remember { mutableStateOf("") }
    var audioInfo by remember { mutableStateOf("") }
    var bufferHealthMs by remember { mutableLongStateOf(0L) }
    var availableAudioTracks by remember { mutableStateOf(listOf("Default Audio")) }
    var selectedAudioTrackIndex by remember { mutableIntStateOf(0) }
    var availableVideoHeights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var sourceResolutionLabel by remember { mutableStateOf("") }

    val rootFocusRequester = remember { FocusRequester() }
    val firstActionButtonFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Number Dialing (Set-Top-Box Remote Keypad) States
    var dialedDigits by remember { mutableStateOf("") }
    var isInvalidChannelNumber by remember { mutableStateOf(false) }
    var digitTimeoutJob by remember { mutableStateOf<Job?>(null) }
    var invalidDismissJob by remember { mutableStateOf<Job?>(null) }

    fun commitChannelNumber() {
        digitTimeoutJob?.cancel()
        digitTimeoutJob = null
        if (dialedDigits.isBlank()) return

        val targetNum = dialedDigits.toIntOrNull()
        if (targetNum != null) {
            val success = onSelectChannelByNumber(targetNum)
            if (success) {
                dialedDigits = ""
                isInvalidChannelNumber = false
                invalidDismissJob?.cancel()
            } else {
                isInvalidChannelNumber = true
                invalidDismissJob?.cancel()
                invalidDismissJob = scope.launch {
                    delay(1500)
                    isInvalidChannelNumber = false
                    dialedDigits = ""
                }
            }
        } else {
            dialedDigits = ""
        }
    }

    fun onDigitEntered(digitChar: Char) {
        if (isDrawerOpen || isSettingsOpen) return
        invalidDismissJob?.cancel()
        isInvalidChannelNumber = false

        if (dialedDigits.length >= 4) {
            dialedDigits = digitChar.toString()
        } else {
            dialedDigits += digitChar
        }

        lastUserInteraction = System.currentTimeMillis()
        digitTimeoutJob?.cancel()
        digitTimeoutJob = scope.launch {
            delay(2000) // Standard 2-second STB tuning delay
            commitChannelNumber()
        }
    }

    // Auto-hide OSD banner after 7 seconds of inactivity (5 to 10 seconds requirement)
    LaunchedEffect(lastUserInteraction, isOsdVisible, isDrawerOpen, isSettingsOpen) {
        if (isOsdVisible && !isDrawerOpen && !isSettingsOpen) {
            delay(7000)
            isOsdVisible = false
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // Direct focus to action buttons when OSD is visible, or root when hidden
    LaunchedEffect(isOsdVisible, isDrawerOpen, isSettingsOpen) {
        if (isOsdVisible && !isDrawerOpen && !isSettingsOpen) {
            delay(50)
            runCatching { firstActionButtonFocusRequester.requestFocus() }
        } else if (!isOsdVisible && !isDrawerOpen && !isSettingsOpen) {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // Sleep Timer countdown
    LaunchedEffect(currentSleepTimerMinutes) {
        if (currentSleepTimerMinutes > 0) {
            delay(currentSleepTimerMinutes * 60 * 1000L)
            exoPlayer.stop()
            onBackPressed()
        }
    }

    // Diagnostics periodic polling
    LaunchedEffect(isStatsVisible, state.currentChannel.id) {
        while (isStatsVisible) {
            bufferHealthMs = exoPlayer.totalBufferedDuration.coerceAtLeast(0)
            val format = exoPlayer.videoFormat
            if (format != null) {
                if (format.bitrate > 0) {
                    videoBitrate = "${format.bitrate / 1000} kbps"
                    if (currentDownloadSpeedBps == 0L) {
                        currentDownloadSpeedBps = format.bitrate.toLong()
                    }
                }
                if (format.width > 0 && format.height > 0) {
                    videoResolution = "${format.width}x${format.height}"
                }
            }
            delay(1000)
        }
    }

    fun loadCurrentStream(isAutoRetry: Boolean = false) {
        if (!isAutoRetry) {
            playbackError = null
            isManualError = false
            reconnectAttempt = 0
            isReconnecting = false
        }
        isBuffering = true
        isOsdVisible = true
        lastUserInteraction = System.currentTimeMillis()
        currentDownloadSpeedBps = 0L
        totalStreamBytesDownloaded = 0L

        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(Uri.parse(state.currentChannel.streamUrl))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(state.currentChannel.name)
                        .setArtist(state.currentChannel.currentProgram)
                        .build()
                )
            val drmScheme = state.currentChannel.drmScheme
            val licenseUrl = state.currentChannel.drmLicenseUrl
            if (drmScheme != null && licenseUrl != null) {
                val drmUuid = when (drmScheme.lowercase()) {
                    "widevine" -> C.WIDEVINE_UUID
                    "playready" -> C.PLAYREADY_UUID
                    else -> null
                }
                if (drmUuid != null) {
                    mediaItemBuilder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(drmUuid)
                            .setLicenseUri(licenseUrl)
                            .setLicenseRequestHeaders(state.currentChannel.drmLicenseRequestHeaders)
                            .build()
                    )
                }
            }
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .apply {
                    if (drmScheme.equals("widevine", ignoreCase = true) &&
                        deviceProfile.widevineLevel == WidevineLevel.L3
                    ) {
                        setMaxVideoSize(1024, 576)
                    } else {
                        clearVideoSizeConstraints()
                    }
                }
                .build()
            val mediaItem = mediaItemBuilder.build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            if (reconnectAttempt >= 8) {
                isManualError = true
                playbackError = e.localizedMessage ?: "Failed to load channel"
            } else {
                isReconnecting = true
            }
        }
    }

    // Exponential Auto-Reconnect Loop for Network Drops
    LaunchedEffect(isReconnecting, reconnectAttempt) {
        if (isReconnecting && reconnectAttempt < 8) {
            val backoffDelayMs = (1500L * (1 shl reconnectAttempt.coerceAtMost(2))).coerceAtMost(5000L)
            delay(backoffDelayMs)
            reconnectAttempt++
            try {
                loadCurrentStream()
            } catch (e: Exception) {
                Log.w("LiveTvPlayerScreen", "Auto-reconnect attempt $reconnectAttempt failed: ${e.message}")
            }
        } else if (reconnectAttempt >= 8) {
            isReconnecting = false
            isManualError = true
            playbackError = "Unable to reconnect to stream. Please check your internet connection."
        }
    }

    // Android Network Callback: Instantly recover when Wi-Fi / Ethernet reconnects
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    if (isReconnecting || isManualError) {
                        delay(500)
                        isReconnecting = false
                        reconnectAttempt = 0
                        isManualError = false
                        playbackError = null
                        loadCurrentStream()
                    }
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w("LiveTvPlayerScreen", "Failed to register network callback for Live TV: ${e.message}")
        }

        onDispose {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        }
    }

    // Set up ExoPlayer for current live channel
    LaunchedEffect(state.currentChannel.id) {
        loadCurrentStream()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        isReconnecting = false
                        reconnectAttempt = 0
                        isManualError = false
                        playbackError = null
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                    }
                    Player.STATE_IDLE -> {
                        if (!isReconnecting && !isManualError) {
                            isBuffering = false
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    isBuffering = false
                    isReconnecting = false
                    reconnectAttempt = 0
                    isManualError = false
                    playbackError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val causeChain = generateSequence<Throwable>(error) { it.cause }
                    .joinToString(" -> ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
                Log.e(
                    "LiveTvPlayerScreen",
                    "Playback failed code=${error.errorCode} name=${error.errorCodeName}: $causeChain"
                )
                isBuffering = false
                if (isTerminalPlaybackError(error)) {
                    isReconnecting = false
                    isManualError = true
                    playbackError = terminalErrorMessage(error)
                } else if (reconnectAttempt < 8) {
                    isReconnecting = true
                } else {
                    isReconnecting = false
                    isManualError = true
                    playbackError = error.localizedMessage ?: "Stream unavailable"
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoResolution = "${videoSize.width}x${videoSize.height}"
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audioNames = mutableListOf<String>()
                val vHeights = mutableSetOf<Int>()
                var highestFormat: Format? = null
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val lang = format.language?.uppercase() ?: "AUDIO ${audioNames.size + 1}"
                            val chCount = if (format.channelCount > 0) " (${format.channelCount}ch)" else ""
                            audioNames.add("$lang$chCount")
                        }
                    } else if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.height > 0) {
                                vHeights.add(format.height)
                            }
                            if (highestFormat == null || format.height > (highestFormat?.height ?: 0)) {
                                highestFormat = format
                            }
                        }
                    }
                }
                if (audioNames.isNotEmpty()) {
                    availableAudioTracks = audioNames
                }
                availableVideoHeights = vHeights.sortedDescending()
                sourceResolutionLabel = highestFormat?.let { fmt ->
                    val p = if (fmt.height > 0) "${fmt.height}p" else ""
                    val fps = if (fmt.frameRate > 0) " @ ${fmt.frameRate.toInt()}fps" else ""
                    "$p$fps".trim()
                } ?: videoResolution
            }
        }

        val analyticsListener = object : AnalyticsListener {
            override fun onLoadCompleted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
                if (loadEventInfo.bytesLoaded > 0) {
                    totalStreamBytesDownloaded += loadEventInfo.bytesLoaded
                    if (loadEventInfo.loadDurationMs > 0) {
                        currentDownloadSpeedBps = (loadEventInfo.bytesLoaded * 8000L) / loadEventInfo.loadDurationMs
                    }
                }
            }

            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long
            ) {
                if (bitrateEstimate > 0) {
                    currentDownloadSpeedBps = bitrateEstimate
                }
            }
        }

        exoPlayer.addListener(listener)
        exoPlayer.addAnalyticsListener(analyticsListener)

        onDispose {
            exoPlayer.removeAnalyticsListener(analyticsListener)
            exoPlayer.removeListener(listener)
        }
    }

    BackHandler {
        if (dialedDigits.isNotEmpty() || isInvalidChannelNumber) {
            digitTimeoutJob?.cancel()
            invalidDismissJob?.cancel()
            dialedDigits = ""
            isInvalidChannelNumber = false
            runCatching { rootFocusRequester.requestFocus() }
        } else if (isSettingsOpen) {
            isSettingsOpen = false
            runCatching { rootFocusRequester.requestFocus() }
        } else if (isDrawerOpen) {
            isDrawerOpen = false
            isOsdVisible = true
            lastUserInteraction = System.currentTimeMillis()
            runCatching { rootFocusRequester.requestFocus() }
        } else if (isOsdVisible) {
            isOsdVisible = false
            runCatching { rootFocusRequester.requestFocus() }
        } else {
            onBackPressed()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (!isDrawerOpen && !isSettingsOpen) {
                        lastUserInteraction = System.currentTimeMillis()
                    }
                    when (event.key) {
                        // Remote Numeric Keypad (STB Direct Channel Tuning)
                        Key.Zero, Key.NumPad0 -> { onDigitEntered('0'); true }
                        Key.One, Key.NumPad1 -> { onDigitEntered('1'); true }
                        Key.Two, Key.NumPad2 -> { onDigitEntered('2'); true }
                        Key.Three, Key.NumPad3 -> { onDigitEntered('3'); true }
                        Key.Four, Key.NumPad4 -> { onDigitEntered('4'); true }
                        Key.Five, Key.NumPad5 -> { onDigitEntered('5'); true }
                        Key.Six, Key.NumPad6 -> { onDigitEntered('6'); true }
                        Key.Seven, Key.NumPad7 -> { onDigitEntered('7'); true }
                        Key.Eight, Key.NumPad8 -> { onDigitEntered('8'); true }
                        Key.Nine, Key.NumPad9 -> { onDigitEntered('9'); true }

                        Key.DirectionUp, Key.ChannelUp, Key.PageUp -> {
                            if (dialedDigits.isNotEmpty()) {
                                commitChannelNumber()
                                true
                            } else if (!isDrawerOpen && !isSettingsOpen) {
                                isOsdVisible = true
                                onNextChannel()
                                true
                            } else false
                        }

                        Key.DirectionDown, Key.ChannelDown, Key.PageDown -> {
                            if (dialedDigits.isNotEmpty()) {
                                commitChannelNumber()
                                true
                            } else if (!isDrawerOpen && !isSettingsOpen) {
                                isOsdVisible = true
                                onPreviousChannel()
                                true
                            } else false
                        }

                        Key.Menu, Key.Settings -> {
                            if (!isDrawerOpen) {
                                isSettingsOpen = !isSettingsOpen
                                isOsdVisible = false
                                true
                            } else false
                        }

                        Key.Back, Key.Escape -> {
                            if (dialedDigits.isNotEmpty() || isInvalidChannelNumber) {
                                digitTimeoutJob?.cancel()
                                invalidDismissJob?.cancel()
                                dialedDigits = ""
                                isInvalidChannelNumber = false
                                runCatching { rootFocusRequester.requestFocus() }
                                true
                            } else if (isSettingsOpen) {
                                isSettingsOpen = false
                                runCatching { rootFocusRequester.requestFocus() }
                                true
                            } else if (isDrawerOpen) {
                                isDrawerOpen = false
                                isOsdVisible = true
                                lastUserInteraction = System.currentTimeMillis()
                                runCatching { rootFocusRequester.requestFocus() }
                                true
                            } else if (isOsdVisible) {
                                isOsdVisible = false
                                runCatching { rootFocusRequester.requestFocus() }
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (dialedDigits.isNotEmpty()) {
                                commitChannelNumber()
                                true
                            } else if (!isOsdVisible && !isDrawerOpen && !isSettingsOpen) {
                                // When OSD is hidden, pressing OK opens the channel list drawer
                                isDrawerOpen = true
                                isOsdVisible = false
                                true
                            } else {
                                // When OSD is visible OR drawer is open, let the focused item handle Enter/OK
                                false
                            }
                        }

                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (!isOsdVisible && !isDrawerOpen && !isSettingsOpen) {
                                // When OSD is hidden, Left/Right wakes up OSD and focuses the action buttons
                                isOsdVisible = true
                                scope.launch {
                                    delay(50)
                                    runCatching { firstActionButtonFocusRequester.requestFocus() }
                                }
                                true
                            } else if (isDrawerOpen) {
                                if (event.key == Key.DirectionRight) {
                                    isDrawerOpen = false
                                    isOsdVisible = true
                                    lastUserInteraction = System.currentTimeMillis()
                                    runCatching { rootFocusRequester.requestFocus() }
                                    true
                                } else false
                            } else {
                                // When OSD is visible, allow D-Pad to move naturally across action buttons
                                false
                            }
                        }

                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.Zero, Key.NumPad0,
                        Key.One, Key.NumPad1,
                        Key.Two, Key.NumPad2,
                        Key.Three, Key.NumPad3,
                        Key.Four, Key.NumPad4,
                        Key.Five, Key.NumPad5,
                        Key.Six, Key.NumPad6,
                        Key.Seven, Key.NumPad7,
                        Key.Eight, Key.NumPad8,
                        Key.Nine, Key.NumPad9 -> true

                        Key.DirectionUp, Key.ChannelUp, Key.PageUp,
                        Key.DirectionDown, Key.ChannelDown, Key.PageDown -> {
                            dialedDigits.isNotEmpty() || (!isDrawerOpen && !isSettingsOpen)
                        }

                        Key.Menu, Key.Settings -> !isDrawerOpen

                        Key.Back, Key.Escape -> {
                            dialedDigits.isNotEmpty() || isInvalidChannelNumber || isSettingsOpen || isDrawerOpen || isOsdVisible
                        }

                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            dialedDigits.isNotEmpty() || (!isOsdVisible && !isDrawerOpen && !isSettingsOpen)
                        }

                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (isDrawerOpen && event.key == Key.DirectionRight) true
                            else !isOsdVisible && !isDrawerOpen && !isSettingsOpen
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // Video Surface with dynamic Aspect Ratio
        PlayerSurface(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                .fillMaxSize()
                .resizeWithContentScale(
                    contentScale = currentAspectRatio.contentScale,
                    sourceSizeDp = null
                )
        )

        // Real-Time Stream Diagnostics (Stats for Nerds)
        if (isStatsVisible && !isDrawerOpen && !isSettingsOpen) {
            LiveTvStatsOverlay(
                channel = state.currentChannel,
                videoResolution = videoResolution,
                videoBitrate = videoBitrate,
                audioInfo = audioInfo,
                bufferHealthMs = bufferHealthMs,
                downloadSpeed = formatStreamSpeed(currentDownloadSpeedBps),
                dataConsumed = formatStreamDataUsage(totalStreamBytesDownloaded),
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // Set-Top-Box Remote Numeric Channel Dialer Overlay
        LiveTvNumberInputOverlay(
            digits = dialedDigits,
            isInvalid = isInvalidChannelNumber,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 48.dp)
        )

        // Video Buffering Spinner (during regular buffering)
        if (isBuffering && !isReconnecting && !isManualError) {
            VideoPlayerBufferingSpinner(
                label = "Buffering stream…",
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Auto-Reconnecting Spinner (during network drop recovery)
        if (isReconnecting) {
            VideoPlayerBufferingSpinner(
                label = "Connection lost. Reconnecting… (Attempt $reconnectAttempt/8)",
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Error message if stream fails persistently after all automatic retries
        if (isManualError && playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Stream Connection Lost",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Internet disconnected or source stream offline.\nAuto-reconnect will resume when connection returns.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                reconnectAttempt = 0
                                isManualError = false
                                playbackError = null
                                loadCurrentStream()
                            }
                        ) {
                            Text("Reconnect Stream")
                        }
                        Button(
                            onClick = onNextChannel,
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Next Channel")
                        }
                    }
                }
            }
        }

        // Bottom OSD Channel Banner & Interactive Action Controls
        AnimatedVisibility(
            visible = isOsdVisible && !isDrawerOpen && !isSettingsOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .padding(horizontal = 40.dp, vertical = 22.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Channel Logo
                        if (!state.currentChannel.logoUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp, 56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = state.currentChannel.logoUrl,
                                    contentDescription = state.currentChannel.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "CH ${state.currentChannel.channelNumber}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        // Channel info & current show
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LiveBadge()
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CH ${state.currentChannel.channelNumber}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "•", color = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.currentChannel.category.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.LightGray
                                )
                                if (videoResolution.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "•", color = Color.Gray)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = videoResolution,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Real-time Internet Speed & Video Stream Data Consumption Badges
                                StreamMetricBadge(
                                    icon = "⚡",
                                    label = "Speed",
                                    value = formatStreamSpeed(currentDownloadSpeedBps),
                                    accentColor = Color(0xFF00E5FF)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                StreamMetricBadge(
                                    icon = "📶",
                                    label = "Data Used",
                                    value = formatStreamDataUsage(totalStreamBytesDownloaded),
                                    accentColor = Color(0xFF69F0AE)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = state.currentChannel.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // JioTV Live Show Title
                            Text(
                                text = currentEpg.title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Show Time Slot, Elapsed & Remaining countdown
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentEpg.formattedTimeSlot,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.LightGray
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "•   ${currentEpg.elapsedMinutes(currentTimeMs)}m played   /   ${currentEpg.remainingMinutes(currentTimeMs)}m left",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF5252)
                                )
                                if (nextEpg != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "•   Next: ${nextEpg.formattedStartTime} ${nextEpg.title}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // JioTV Red Linear Progress Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = currentEpg.progress(currentTimeMs).coerceIn(0.02f, 1f))
                                        .height(3.dp)
                                        .background(Color(0xFFE50914))
                                )
                            }
                        }

                        // Navigation Remote Hints
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("▲ / ▼ / CH+/- ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                Text("Zap", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("◀ / ▶ ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                Text("Select", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("OK ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                Text("Enter / Guide", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("MENU ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                Text("Settings", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("EXIT ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary)
                                Text("Hide", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Interactive Action & Settings Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Channel Guide button
                        Button(
                            onClick = {
                                isDrawerOpen = true
                                isOsdVisible = false
                            },
                            modifier = Modifier.focusRequester(firstActionButtonFocusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Channels", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Favorite toggle button
                        Button(
                            onClick = {
                                onToggleFavorite()
                                lastUserInteraction = System.currentTimeMillis()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (state.currentChannel.isFavorite) Color(0xFFFFD700).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = if (state.currentChannel.isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color(0xFFFFD700),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (state.currentChannel.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    tint = if (state.currentChannel.isFavorite) Color(0xFFFFD700) else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (state.currentChannel.isFavorite) "Favorited" else "Favorite",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Reconnect / Reload button
                        Button(
                            onClick = {
                                loadCurrentStream()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reload", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Stats toggle button
                        Button(
                            onClick = {
                                isStatsVisible = !isStatsVisible
                                lastUserInteraction = System.currentTimeMillis()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isStatsVisible) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = if (isStatsVisible) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.secondary,
                                focusedContentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stats", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Settings Button
                        Button(
                            onClick = {
                                isSettingsOpen = true
                                isOsdVisible = false
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Settings", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }

        // Side Mini-Guide / Channel Switcher Drawer
        LiveTvChannelDrawer(
            isPresented = isDrawerOpen,
            currentChannel = state.currentChannel,
            channels = if (state.filteredChannels.isNotEmpty()) state.filteredChannels else state.channels,
            onSelectChannel = { ch ->
                onSelectChannel(ch)
                isDrawerOpen = false
                isOsdVisible = true
                lastUserInteraction = System.currentTimeMillis()
                runCatching { rootFocusRequester.requestFocus() }
            },
            onDismiss = {
                isDrawerOpen = false
                isOsdVisible = true
                lastUserInteraction = System.currentTimeMillis()
                runCatching { rootFocusRequester.requestFocus() }
            },
            currentTimeMs = currentTimeMs,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // Live TV Settings Modal
        LiveTvSettingsOverlay(
            isPresented = isSettingsOpen,
            currentAspectRatio = currentAspectRatio,
            currentQualityMaxHeight = currentQualityMaxHeight,
            currentSleepTimerMinutes = currentSleepTimerMinutes,
            isStatsVisible = isStatsVisible,
            availableAudioTracks = availableAudioTracks,
            selectedAudioTrackIndex = selectedAudioTrackIndex,
            availableVideoHeights = availableVideoHeights,
            sourceResolutionLabel = sourceResolutionLabel,
            onSelectAspectRatio = { currentAspectRatio = it },
            onSelectQuality = { height ->
                currentQualityMaxHeight = height
                val builder = exoPlayer.trackSelectionParameters.buildUpon()
                if (height > 0) {
                    builder.setMaxVideoSize(Int.MAX_VALUE, height)
                } else {
                    builder.clearVideoSizeConstraints()
                }
                exoPlayer.trackSelectionParameters = builder.build()
            },
            onSelectSleepTimer = { currentSleepTimerMinutes = it },
            onSelectAudioTrack = { index ->
                selectedAudioTrackIndex = index
                if (index < availableAudioTracks.size) {
                    val trackName = availableAudioTracks[index]
                    val lang = trackName.split(" ").firstOrNull()?.lowercase()
                    if (!lang.isNullOrBlank()) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setPreferredAudioLanguage(lang)
                            .build()
                    }
                }
            },
            onToggleStats = { isStatsVisible = !isStatsVisible },
            onReloadStream = { loadCurrentStream() },
            onDismiss = {
                isSettingsOpen = false
                runCatching { rootFocusRequester.requestFocus() }
            }
        )
    }
}

@Composable
private fun StreamMetricBadge(
    icon: String,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = icon,
            fontSize = 11.sp
        )
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = Color.LightGray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = accentColor
        )
    }
}

private fun formatStreamSpeed(bps: Long): String {
    return when {
        bps >= 1_000_000L -> String.format(java.util.Locale.US, "%.1f Mbps", bps / 1_000_000.0)
        bps >= 1_000L -> String.format(java.util.Locale.US, "%.0f kbps", bps / 1_000.0)
        bps > 0 -> "$bps bps"
        else -> "-- kbps"
    }
}

private fun formatStreamDataUsage(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        bytes > 0 -> "$bytes B"
        else -> "0 KB"
    }
}
