/*
 * Copyright 2023 Google LLC
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

package com.example.metflix.presentation.screens.videoPlayer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import com.example.metflix.data.network.resolvers.ResolvedStream
import com.example.metflix.data.network.resolvers.StreamServer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.presentation.common.Error
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerBufferingSpinner
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerControls
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerOverlay
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPlaylistOverlay
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPulse
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.BACK
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.FORWARD
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.NONE
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPulseState
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerSettingsOverlay
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerStatsOverlay
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerState
import com.example.metflix.presentation.screens.videoPlayer.components.WebStreamPlayer
import com.example.metflix.presentation.screens.videoPlayer.components.rememberPlayer
import com.example.metflix.presentation.screens.videoPlayer.components.rememberVideoPlayerPulseState
import com.example.metflix.presentation.screens.videoPlayer.components.rememberVideoPlayerState
import com.example.metflix.presentation.utils.handleDPadKeyEvents
import kotlinx.coroutines.delay

@androidx.compose.runtime.Immutable
data class ExoAudioTrack(
    val index: Int,
    val label: String,
    val languageCode: String,
    val group: Tracks.Group,
    val trackIndexInGroup: Int,
    val isHindi: Boolean,
    val isSelected: Boolean
)

object VideoPlayerScreen {
    const val MovieIdBundleKey = "movieId"
}

@Composable
fun VideoPlayerScreen(
    onBackPressed: () -> Unit,
    videoPlayerScreenViewModel: VideoPlayerScreenViewModel = hiltViewModel()
) {
    val uiState by videoPlayerScreenViewModel.uiState.collectAsStateWithLifecycle()

    when (val s = uiState) {
        is VideoPlayerScreenUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        is VideoPlayerScreenUiState.Error -> {
            Error(modifier = Modifier.fillMaxSize())
        }

        is VideoPlayerScreenUiState.Done -> {
            VideoPlayerScreenContent(
                movieDetails = s.movieDetails,
                resolvedStream = s.resolvedStream,
                currentServer = s.currentServer,
                availableServers = s.availableServers,
                isResolving = s.isResolving,
                onSelectServer = { videoPlayerScreenViewModel.selectServer(it) },
                onRetryNextServer = { videoPlayerScreenViewModel.retryNextServer() },
                onCapturedDirectStream = { url, headers ->
                    videoPlayerScreenViewModel.onCapturedDirectStream(url, headers)
                },
                onBackPressed = onBackPressed
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreenContent(
    movieDetails: MovieDetails,
    resolvedStream: ResolvedStream?,
    currentServer: StreamServer,
    availableServers: List<StreamServer>,
    isResolving: Boolean,
    onSelectServer: (StreamServer) -> Unit,
    onRetryNextServer: () -> Unit,
    onCapturedDirectStream: (String, Map<String, String>) -> Unit = { _, _ -> },
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val streamHeaders = resolvedStream?.headers ?: emptyMap()
    val exoPlayer = rememberPlayer(context, streamHeaders)

    val videoPlayerState = rememberVideoPlayerState(
        hideSeconds = 4,
    )

    var currentContentScale by remember { mutableStateOf(ContentScale.Fit) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var isSubtitlesEnabled by remember { mutableStateOf(false) }
    var currentCues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isPlaylistOpen by remember { mutableStateOf(false) }
    val settingsButtonFocusRequester = remember { FocusRequester() }

    var isWebPlayerForced by remember { mutableStateOf<Boolean?>(null) }
    val isDirectStream = resolvedStream?.isDirectStream == true
    val isWebPlayerActive = isWebPlayerForced ?: (!isDirectStream)

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    var currentQualityMaxHeight by remember { mutableIntStateOf(0) }
    var availableQualityHeights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var sourceResolutionLabel by remember { mutableStateOf("") }

    var availableAudioTracks by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAudioTrackIndex by remember { mutableIntStateOf(0) }
    var playerAudioTracks by remember { mutableStateOf<List<ExoAudioTrack>>(emptyList()) }
    var hasAutoSelectedHindi by remember { mutableStateOf(false) }
    var availableSubtitleTracks by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSubtitleTrackIndex by remember { mutableIntStateOf(-1) }
    var currentSleepTimerMinutes by remember { mutableIntStateOf(0) }
    var isStatsVisible by remember { mutableStateOf(false) }
    var bufferHealthMs by remember { mutableStateOf(0L) }
    var videoBitrate by remember { mutableStateOf("") }
    var audioInfo by remember { mutableStateOf("") }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                } else if (playbackState == Player.STATE_ENDED) {
                    videoPlayerState.showControls(isPlaying = false)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val heights = mutableSetOf<Int>()
                var highestFormat: Format? = null
                val audioTrackList = mutableListOf<ExoAudioTrack>()
                val subNames = mutableListOf<String>()
                var currentAudio: Format? = null
                var currentlySelectedIndex = 0
                var foundHindiIndex: Int? = null

                for (group in tracks.groups) {
                    when (group.type) {
                        C.TRACK_TYPE_VIDEO -> {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                if (format.height > 0) {
                                    heights.add(format.height)
                                }
                                if (highestFormat == null || format.height > (highestFormat?.height ?: 0)) {
                                    highestFormat = format
                                }
                            }
                        }
                        C.TRACK_TYPE_AUDIO -> {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val langCode = format.language.orEmpty()
                                val formatLabel = format.label.orEmpty()
                                val isHindi = langCode.lowercase() in listOf("hi", "hin") ||
                                        formatLabel.contains("hindi", ignoreCase = true)
                                val langName = when {
                                    isHindi -> "Hindi"
                                    langCode.lowercase() in listOf("en", "eng") || formatLabel.contains("english", ignoreCase = true) -> "English"
                                    langCode.lowercase() in listOf("ta", "tam") || formatLabel.contains("tamil", ignoreCase = true) -> "Tamil"
                                    langCode.lowercase() in listOf("te", "tel") || formatLabel.contains("telugu", ignoreCase = true) -> "Telugu"
                                    langCode.lowercase() in listOf("ml", "mal") || formatLabel.contains("malayalam", ignoreCase = true) -> "Malayalam"
                                    langCode.lowercase() in listOf("es", "spa") -> "Spanish"
                                    langCode.lowercase() in listOf("fr", "fra", "fre") -> "French"
                                    langCode.lowercase() in listOf("de", "deu", "ger") -> "German"
                                    langCode.lowercase() in listOf("it", "ita") -> "Italian"
                                    langCode.lowercase() in listOf("ja", "jpn") -> "Japanese"
                                    langCode.lowercase() in listOf("ko", "kor") -> "Korean"
                                    langCode.lowercase() in listOf("zh", "zho", "chi") -> "Chinese"
                                    langCode.lowercase() in listOf("ar", "ara") -> "Arabic"
                                    langCode.lowercase() in listOf("ru", "rus") -> "Russian"
                                    langCode.lowercase() in listOf("pt", "por") -> "Portuguese"
                                    formatLabel.isNotBlank() -> formatLabel
                                    langCode.isNotBlank() -> langCode.uppercase()
                                    else -> "Audio ${audioTrackList.size + 1}"
                                }
                                val channelText = when (format.channelCount) {
                                    6 -> "5.1 Surround"
                                    8 -> "7.1 Surround"
                                    2 -> "Stereo"
                                    1 -> "Mono"
                                    else -> if (format.channelCount > 0) "${format.channelCount}ch" else ""
                                }
                                val label = if (channelText.isNotBlank()) "$langName ($channelText)" else langName
                                val isTrackSelected = group.isTrackSelected(i)
                                val currentIdx = audioTrackList.size
                                if (isTrackSelected) {
                                    currentlySelectedIndex = currentIdx
                                    currentAudio = format
                                }
                                if (isHindi && foundHindiIndex == null) {
                                    foundHindiIndex = currentIdx
                                }

                                audioTrackList.add(
                                    ExoAudioTrack(
                                        index = currentIdx,
                                        label = label,
                                        languageCode = langCode,
                                        group = group,
                                        trackIndexInGroup = i,
                                        isHindi = isHindi,
                                        isSelected = isTrackSelected
                                    )
                                )
                            }
                        }
                        C.TRACK_TYPE_TEXT -> {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val lang = format.label ?: format.language?.replaceFirstChar { it.uppercase() } ?: "Sub ${subNames.size + 1}"
                                subNames.add(lang)
                            }
                        }
                    }
                }
                val directHeights = resolvedStream?.qualities?.map { it.height }?.filter { it > 0 } ?: emptyList()
                availableQualityHeights = (heights + directHeights).distinct().sortedDescending()
                sourceResolutionLabel = highestFormat?.let { fmt ->
                    val p = if (fmt.height > 0) "${fmt.height}p" else ""
                    val fps = if (fmt.frameRate > 0) " @ ${fmt.frameRate.toInt()}fps" else ""
                    "$p$fps".trim()
                } ?: resolvedStream?.qualities?.firstOrNull()?.quality ?: ""

                if (audioTrackList.isNotEmpty()) {
                    playerAudioTracks = audioTrackList
                    availableAudioTracks = audioTrackList.map { it.label }
                    selectedAudioTrackIndex = currentlySelectedIndex

                    // Auto-select Hindi audio track if available on stream startup
                    if (!hasAutoSelectedHindi && foundHindiIndex != null) {
                        val hindiTrack = audioTrackList[foundHindiIndex]
                        if (!hindiTrack.isSelected) {
                            val override = TrackSelectionOverride(
                                hindiTrack.group.mediaTrackGroup,
                                listOf(hindiTrack.trackIndexInGroup)
                            )
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                .setOverrideForType(override)
                                .build()
                            selectedAudioTrackIndex = foundHindiIndex
                            Log.d("VideoPlayerScreen", "Auto-switched to Hindi audio: ${hindiTrack.label}")
                        }
                        hasAutoSelectedHindi = true
                    }
                }
                availableSubtitleTracks = subNames
                if (subNames.isEmpty()) {
                    selectedSubtitleTrackIndex = -1
                    isSubtitlesEnabled = false
                }
                audioInfo = currentAudio?.let { fmt ->
                    val lang = fmt.language?.uppercase() ?: "STEREO"
                    val ch = if (fmt.channelCount > 0) " ${fmt.channelCount}ch" else ""
                    val sampleRate = if (fmt.sampleRate > 0) " ${fmt.sampleRate / 1000}kHz" else ""
                    "$lang$ch$sampleRate".trim()
                } ?: ""
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Unable to stream video. Please check your internet connection."
                isBuffering = false
            }

            override fun onCues(cueGroup: CueGroup) {
                currentCues = cueGroup.cues
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(isStatsVisible) {
        while (isStatsVisible) {
            bufferHealthMs = exoPlayer.totalBufferedDuration.coerceAtLeast(0L)
            val fmt = exoPlayer.videoFormat
            if (fmt != null && fmt.bitrate > 0) {
                videoBitrate = "${String.format("%.2f", fmt.bitrate / 1_000_000f)} Mbps"
            }
            delay(1000)
        }
    }

    LaunchedEffect(currentSleepTimerMinutes) {
        if (currentSleepTimerMinutes > 0) {
            delay(currentSleepTimerMinutes * 60 * 1000L)
            exoPlayer.pause()
            currentSleepTimerMinutes = 0
        }
    }

    LaunchedEffect(exoPlayer, movieDetails, resolvedStream, isWebPlayerActive) {
        playbackError = null
        hasAutoSelectedHindi = false
        if (isWebPlayerActive) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            isBuffering = false
        } else {
            isBuffering = true
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            val activeStreamUrl = resolvedStream?.streamUrl?.takeIf { it.isNotBlank() } ?: movieDetails.videoUri
            val items = buildList {
                add(movieDetails.intoMediaItem(activeStreamUrl, resolvedStream))
                movieDetails.similarMovies.forEach {
                    add(it.intoMediaItem())
                }
            }
            exoPlayer.setMediaItems(items)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    BackHandler(onBack = {
        if (isSettingsOpen) {
            isSettingsOpen = false
            videoPlayerState.showControls(isPlaying)
            runCatching { settingsButtonFocusRequester.requestFocus() }
        } else if (isPlaylistOpen) {
            isPlaylistOpen = false
        } else {
            onBackPressed()
        }
    })

    val pulseState = rememberVideoPlayerPulseState()
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(videoPlayerState.isControlsVisible, isWebPlayerActive) {
        if (!isWebPlayerActive && !videoPlayerState.isControlsVisible && !isSettingsOpen && !isPlaylistOpen) {
            rootFocusRequester.requestFocus()
        }
    }

    val rootModifier = if (!videoPlayerState.isControlsVisible && !isSettingsOpen && !isPlaylistOpen) {
        Modifier
            .focusRequester(rootFocusRequester)
            .dPadEvents(
                exoPlayer = exoPlayer,
                videoPlayerState = videoPlayerState,
                pulseState = pulseState,
                isPlaying = isPlaying
            )
            .mediaKeyEvents(
                exoPlayer = exoPlayer,
                videoPlayerState = videoPlayerState,
                pulseState = pulseState,
                onToggleSettings = {
                    val target = !isSettingsOpen
                    isSettingsOpen = target
                    if (target) {
                        isPlaylistOpen = false
                    } else {
                        videoPlayerState.showControls(isPlaying)
                        runCatching { settingsButtonFocusRequester.requestFocus() }
                    }
                }
            )
            .focusable()
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.Menu, Key.Settings -> {
                            val target = !isSettingsOpen
                            isSettingsOpen = target
                            if (target) {
                                isPlaylistOpen = false
                            } else {
                                videoPlayerState.showControls(isPlaying)
                                runCatching { settingsButtonFocusRequester.requestFocus() }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .then(rootModifier)
    ) {
        if (isWebPlayerActive) {
            val webUrl = resolvedStream?.streamUrl?.takeIf { it.isNotBlank() } ?: movieDetails.videoUri
            WebStreamPlayer(
                url = webUrl,
                onCapturedDirectStream = { directUrl, headers ->
                    onCapturedDirectStream(directUrl, headers)
                },
                onError = { err ->
                    playbackError = err
                },
                onToggleControls = {
                    videoPlayerState.showControls(isPlaying = true)
                }
            )
        } else {
            PlayerSurface(
                player = exoPlayer,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.resizeWithContentScale(
                    contentScale = currentContentScale,
                    sourceSizeDp = null
                )
            )
        }

        val focusRequester = remember { FocusRequester() }
        VideoPlayerOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            focusRequester = focusRequester,
            isPlaying = isPlaying,
            isControlsVisible = videoPlayerState.isControlsVisible && !isSettingsOpen && !isPlaylistOpen,
            centerButton = {
                if (pulseState.type != NONE) {
                    VideoPlayerPulse(pulseState)
                } else if (isBuffering && playbackError == null) {
                    VideoPlayerBufferingSpinner(
                        label = if (isResolving) "Connecting to ${currentServer.name}..." else "Buffering stream..."
                    )
                }
            },
            subtitles = {
                if (isSubtitlesEnabled && currentCues.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        currentCues.forEach { cue ->
                            cue.text?.let { text ->
                                Text(
                                    text = text.toString(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        shadow = Shadow(color = Color.Black, blurRadius = 4f)
                                    ),
                                    modifier = Modifier
                                        .background(
                                            color = Color.Black.copy(alpha = 0.75f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            showControls = { videoPlayerState.showControls(isPlaying) },
            controls = {
                VideoPlayerControls(
                    player = exoPlayer,
                    movieDetails = movieDetails,
                    focusRequester = focusRequester,
                    settingsButtonFocusRequester = settingsButtonFocusRequester,
                    isSubtitlesEnabled = isSubtitlesEnabled,
                    isSettingsOpen = isSettingsOpen,
                    onShowSettings = {
                        isSettingsOpen = true
                        isPlaylistOpen = false
                    },
                    onShowPlaylist = {
                        isPlaylistOpen = true
                        isSettingsOpen = false
                    },
                    onToggleSubtitles = {
                        val newEnabled = !isSubtitlesEnabled
                        isSubtitlesEnabled = newEnabled
                        selectedSubtitleTrackIndex = if (newEnabled) {
                            if (availableSubtitleTracks.isNotEmpty()) 0 else 0
                        } else {
                            -1
                        }
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !newEnabled)
                            .apply {
                                if (newEnabled) {
                                    val lang = availableSubtitleTracks.firstOrNull()?.lowercase() ?: "en"
                                    setPreferredTextLanguage(lang)
                                } else {
                                    setPreferredTextLanguage(null)
                                }
                            }
                            .build()
                    },
                    onShowControls = { videoPlayerState.showControls(isPlaying) },
                )
            }
        )

        // Error Prompt Overlay
        AnimatedVisibility(
            visible = playbackError != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Playback Issue on ${currentServer.name}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playbackError ?: "Unable to stream. Please switch to another server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                isWebPlayerForced = null
                                onRetryNextServer()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = "Try Next Server")
                        }

                        Button(
                            onClick = {
                                playbackError = null
                                isBuffering = true
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = "Retry Current")
                        }
                    }
                }
            }
        }

        // Real-Time Stream Diagnostics (Stats for Nerds)
        if (isStatsVisible && !isSettingsOpen && !isPlaylistOpen) {
            VideoPlayerStatsOverlay(
                movieTitle = movieDetails.name,
                videoResolution = sourceResolutionLabel,
                videoBitrate = videoBitrate,
                audioInfo = audioInfo,
                subtitleInfo = if (selectedSubtitleTrackIndex >= 0) {
                    availableSubtitleTracks.getOrNull(selectedSubtitleTrackIndex) ?: "English"
                } else "Off",
                playbackSpeed = "${currentSpeed}x",
                bufferHealthMs = bufferHealthMs,
                currentPositionText = formatDurationTime(exoPlayer.currentPosition),
                durationText = formatDurationTime(exoPlayer.duration),
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // Settings Dialog Overlay
        VideoPlayerSettingsOverlay(
            isPresented = isSettingsOpen,
            currentSpeed = currentSpeed,
            currentContentScale = currentContentScale,
            availableServers = availableServers,
            selectedServer = currentServer,
            onSelectServer = { server ->
                isWebPlayerForced = null
                onSelectServer(server)
                isSettingsOpen = false
                videoPlayerState.showControls(isPlaying)
                runCatching { settingsButtonFocusRequester.requestFocus() }
            },
            currentQualityMaxHeight = currentQualityMaxHeight,
            availableQualityHeights = availableQualityHeights,
            sourceResolutionLabel = sourceResolutionLabel,
            availableAudioTracks = availableAudioTracks,
            selectedAudioTrackIndex = selectedAudioTrackIndex,
            availableSubtitleTracks = availableSubtitleTracks,
            selectedSubtitleTrackIndex = selectedSubtitleTrackIndex,
            currentSleepTimerMinutes = currentSleepTimerMinutes,
            isStatsVisible = isStatsVisible,
            isWebPlayerMode = isWebPlayerActive,
            onTogglePlayerEngine = { isWeb ->
                isWebPlayerForced = isWeb
            },
            onSelectSpeed = { speed ->
                currentSpeed = speed
                exoPlayer.playbackParameters = PlaybackParameters(speed)
            },
            onSelectContentScale = { scale ->
                currentContentScale = scale
                exoPlayer.videoScalingMode = when (scale) {
                    ContentScale.Crop -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    else -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                }
            },
            onSelectQuality = { height ->
                currentQualityMaxHeight = height
                val matchingQuality = resolvedStream?.qualities?.firstOrNull { it.height == height }
                if (matchingQuality != null && matchingQuality.url != exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()) {
                    val currentPos = exoPlayer.currentPosition
                    val wasPlaying = exoPlayer.isPlaying
                    exoPlayer.setMediaItem(movieDetails.intoMediaItem(matchingQuality.url, resolvedStream), currentPos)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = wasPlaying
                } else {
                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                    if (height > 0) {
                        builder.setMaxVideoSize(Int.MAX_VALUE, height)
                    } else {
                        builder.clearVideoSizeConstraints()
                    }
                    exoPlayer.trackSelectionParameters = builder.build()
                }
            },
            onSelectAudioTrack = { index ->
                selectedAudioTrackIndex = index
                val targetTrack = playerAudioTracks.getOrNull(index)
                if (targetTrack != null) {
                    val override = TrackSelectionOverride(
                        targetTrack.group.mediaTrackGroup,
                        listOf(targetTrack.trackIndexInGroup)
                    )
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setOverrideForType(override)
                        .build()
                    Log.d("VideoPlayerScreen", "User switched audio track to: ${targetTrack.label}")
                } else if (index < availableAudioTracks.size) {
                    val trackName = availableAudioTracks[index]
                    val lang = when {
                        trackName.contains("hindi", ignoreCase = true) -> "hi"
                        trackName.contains("english", ignoreCase = true) -> "en"
                        else -> trackName.split(" ").firstOrNull()?.lowercase()
                    }
                    if (!lang.isNullOrBlank()) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setPreferredAudioLanguage(lang)
                            .build()
                    }
                }
            },
            onSelectSubtitleTrack = { index ->
                selectedSubtitleTrackIndex = index
                if (index < 0) {
                    isSubtitlesEnabled = false
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                } else {
                    isSubtitlesEnabled = true
                    val lang = availableSubtitleTracks.getOrNull(index)?.lowercase() ?: "en"
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(lang)
                        .build()
                }
            },
            onSelectSleepTimer = { currentSleepTimerMinutes = it },
            onToggleStats = { isStatsVisible = !isStatsVisible },
            onRestartMovie = {
                exoPlayer.seekTo(0L)
                exoPlayer.play()
                isSettingsOpen = false
                videoPlayerState.showControls(isPlaying)
                runCatching { settingsButtonFocusRequester.requestFocus() }
            },
            onDismiss = {
                isSettingsOpen = false
                videoPlayerState.showControls(isPlaying)
                runCatching { settingsButtonFocusRequester.requestFocus() }
            }
        )

        // Playlist Dialog Overlay
        VideoPlayerPlaylistOverlay(
            isPresented = isPlaylistOpen,
            movies = movieDetails.similarMovies,
            onSelectMovieIndex = { index ->
                // index 0 in player playlist is current movieDetails, 1..N are similarMovies
                exoPlayer.seekToDefaultPosition(index + 1)
                exoPlayer.play()
                isPlaylistOpen = false
            },
            onDismiss = {
                isPlaylistOpen = false
                videoPlayerState.showControls(isPlaying)
            }
        )
    }
}

private fun Modifier.dPadEvents(
    exoPlayer: ExoPlayer,
    videoPlayerState: VideoPlayerState,
    pulseState: VideoPlayerPulseState,
    isPlaying: Boolean
): Modifier = this.handleDPadKeyEvents(
    onLeft = {
        if (!videoPlayerState.isControlsVisible) {
            exoPlayer.seekBack()
            pulseState.setType(BACK)
        }
    },
    onRight = {
        if (!videoPlayerState.isControlsVisible) {
            exoPlayer.seekForward()
            pulseState.setType(FORWARD)
        }
    },
    onUp = { videoPlayerState.showControls(isPlaying) },
    onDown = { videoPlayerState.showControls(isPlaying) },
    onEnter = {
        if (isPlaying) {
            exoPlayer.pause()
            videoPlayerState.showControls(isPlaying = false)
        } else {
            exoPlayer.play()
            videoPlayerState.showControls(isPlaying = true)
        }
    }
)

private fun Modifier.mediaKeyEvents(
    exoPlayer: ExoPlayer,
    videoPlayerState: VideoPlayerState,
    pulseState: VideoPlayerPulseState,
    onToggleSettings: () -> Unit
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyUp) {
        when (event.key) {
            Key.Menu, Key.Settings -> {
                onToggleSettings()
                true
            }
            Key.MediaPlay -> {
                exoPlayer.play()
                videoPlayerState.showControls(isPlaying = true)
                true
            }
            Key.MediaPause -> {
                exoPlayer.pause()
                videoPlayerState.showControls(isPlaying = false)
                true
            }
            Key.MediaPlayPause -> {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    videoPlayerState.showControls(isPlaying = false)
                } else {
                    exoPlayer.play()
                    videoPlayerState.showControls(isPlaying = true)
                }
                true
            }
            Key.MediaFastForward -> {
                exoPlayer.seekForward()
                pulseState.setType(FORWARD)
                true
            }
            Key.MediaRewind -> {
                exoPlayer.seekBack()
                pulseState.setType(BACK)
                true
            }
            else -> false
        }
    } else false
}

private fun formatDurationTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun MovieDetails.intoMediaItem(
    streamUrlOverride: String? = null,
    resolvedStream: ResolvedStream? = null
): MediaItem {
    val targetUri = streamUrlOverride?.takeIf { it.isNotBlank() } ?: videoUri
    val subConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
    val allSubs = resolvedStream?.subtitles.orEmpty()
    val englishSub = allSubs.firstOrNull { it.language.equals("English", ignoreCase = true) || it.label.contains("English", ignoreCase = true) }
    val otherSubs = allSubs.filter { it != englishSub }.take(6)
    val subsToLoad = listOfNotNull(englishSub) + otherSubs

    subsToLoad.forEach { sub ->
        val mimeType = if (sub.uri.contains(".vtt")) {
            MimeTypes.TEXT_VTT
        } else {
            MimeTypes.APPLICATION_SUBRIP
        }
        val isDefault = sub.language.equals("English", ignoreCase = true) || sub.label.contains("English", ignoreCase = true)
        subConfigs.add(
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.uri))
                .setMimeType(mimeType)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        )
    }

    if (subConfigs.isEmpty() && subtitleUri != null) {
        subConfigs.add(
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUri))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        )
    }

    return MediaItem.Builder()
        .setUri(targetUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(director)
                .setDescription(description)
                .build()
        )
        .setSubtitleConfigurations(subConfigs)
        .build()
}

private fun Movie.intoMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(videoUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setDescription(description)
                .build()
        )
        .setSubtitleConfigurations(
            if (subtitleUri == null) {
                emptyList()
            } else {
                listOf(
                    MediaItem.SubtitleConfiguration
                        .Builder(Uri.parse(subtitleUri))
                        .setMimeType("application/vtt")
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            }
        )
        .build()
}
