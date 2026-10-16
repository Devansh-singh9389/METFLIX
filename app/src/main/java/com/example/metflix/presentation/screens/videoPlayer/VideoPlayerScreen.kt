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
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.presentation.common.Error
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerControls
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerOverlay
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPlaylistOverlay
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPulse
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.BACK
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.FORWARD
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerPulseState
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerSettingsOverlay
import com.example.metflix.presentation.screens.videoPlayer.components.VideoPlayerState
import com.example.metflix.presentation.screens.videoPlayer.components.rememberPlayer
import com.example.metflix.presentation.screens.videoPlayer.components.rememberVideoPlayerPulseState
import com.example.metflix.presentation.screens.videoPlayer.components.rememberVideoPlayerState
import com.example.metflix.presentation.utils.handleDPadKeyEvents

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
                onBackPressed = onBackPressed
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreenContent(movieDetails: MovieDetails, onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = rememberPlayer(context)

    val videoPlayerState = rememberVideoPlayerState(
        hideSeconds = 4,
    )

    var currentContentScale by remember { mutableStateOf(ContentScale.Fit) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var isSubtitlesEnabled by remember { mutableStateOf(false) }
    var currentCues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isPlaylistOpen by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                currentCues = cueGroup.cues
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(exoPlayer, movieDetails) {
        exoPlayer.addMediaItem(movieDetails.intoMediaItem())
        movieDetails.similarMovies.forEach {
            exoPlayer.addMediaItem(it.intoMediaItem())
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    BackHandler(onBack = {
        if (isSettingsOpen) {
            isSettingsOpen = false
        } else if (isPlaylistOpen) {
            isPlaylistOpen = false
        } else {
            onBackPressed()
        }
    })

    val pulseState = rememberVideoPlayerPulseState()
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(videoPlayerState.isControlsVisible) {
        if (!videoPlayerState.isControlsVisible && !isSettingsOpen && !isPlaylistOpen) {
            rootFocusRequester.requestFocus()
        }
    }

    val rootModifier = if (!videoPlayerState.isControlsVisible && !isSettingsOpen && !isPlaylistOpen) {
        Modifier
            .focusRequester(rootFocusRequester)
            .dPadEvents(
                exoPlayer,
                videoPlayerState,
                pulseState
            )
            .focusable()
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(rootModifier)
    ) {
        PlayerSurface(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(
                contentScale = currentContentScale,
                sourceSizeDp = null
            )
        )

        val focusRequester = remember { FocusRequester() }
        VideoPlayerOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            focusRequester = focusRequester,
            isPlaying = exoPlayer.isPlaying,
            isControlsVisible = videoPlayerState.isControlsVisible && !isSettingsOpen && !isPlaylistOpen,
            centerButton = { VideoPlayerPulse(pulseState) },
            subtitles = {
                if (isSubtitlesEnabled && currentCues.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
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
            showControls = videoPlayerState::showControls,
            controls = {
                VideoPlayerControls(
                    player = exoPlayer,
                    movieDetails = movieDetails,
                    focusRequester = focusRequester,
                    isSubtitlesEnabled = isSubtitlesEnabled,
                    onShowSettings = {
                        isSettingsOpen = true
                        isPlaylistOpen = false
                    },
                    onShowPlaylist = {
                        isPlaylistOpen = true
                        isSettingsOpen = false
                    },
                    onToggleSubtitles = {
                        isSubtitlesEnabled = !isSubtitlesEnabled
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitlesEnabled)
                            .setPreferredTextLanguage(if (isSubtitlesEnabled) "en" else null)
                            .build()
                    },
                    onShowControls = { videoPlayerState.showControls(exoPlayer.isPlaying) },
                )
            }
        )

        // Settings Dialog Overlay
        VideoPlayerSettingsOverlay(
            isPresented = isSettingsOpen,
            currentSpeed = currentSpeed,
            currentContentScale = currentContentScale,
            onSelectSpeed = { speed ->
                currentSpeed = speed
                exoPlayer.playbackParameters = PlaybackParameters(speed)
            },
            onSelectContentScale = { scale ->
                currentContentScale = scale
            },
            onDismiss = {
                isSettingsOpen = false
                videoPlayerState.showControls(exoPlayer.isPlaying)
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
                videoPlayerState.showControls(exoPlayer.isPlaying)
            }
        )
    }
}

private fun Modifier.dPadEvents(
    exoPlayer: ExoPlayer,
    videoPlayerState: VideoPlayerState,
    pulseState: VideoPlayerPulseState
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
    onUp = { videoPlayerState.showControls(exoPlayer.isPlaying) },
    onDown = { videoPlayerState.showControls(exoPlayer.isPlaying) },
    onEnter = {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            videoPlayerState.showControls(isPlaying = false)
        } else {
            exoPlayer.play()
            videoPlayerState.showControls(isPlaying = true)
        }
    }
)

private fun MovieDetails.intoMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(videoUri)
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
        ).build()
}

private fun Movie.intoMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(videoUri)
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
