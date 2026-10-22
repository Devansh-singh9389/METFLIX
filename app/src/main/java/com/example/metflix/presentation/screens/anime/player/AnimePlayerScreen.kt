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

package com.example.metflix.presentation.screens.anime.player

import android.app.ActivityManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.network.anime.AnimeStreamExtractor
import com.example.metflix.data.network.anime.models.AnimePlayerEngineType
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.screens.anime.player.components.AnimeAudioDialog
import com.example.metflix.presentation.screens.anime.player.components.AnimeExoPlayerView
import com.example.metflix.presentation.screens.anime.player.components.AnimePlayerHud
import com.example.metflix.presentation.screens.anime.player.components.AnimeQualityDialog
import com.example.metflix.presentation.screens.anime.player.components.AnimeServerDialog
import com.example.metflix.presentation.screens.anime.player.components.AnimeUnifiedSettingsDialog
import com.example.metflix.presentation.screens.anime.player.components.AnimeWebViewPlayerView
import kotlinx.coroutines.delay

@Composable
fun AnimePlayerScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimePlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val bufferedPositionMs by viewModel.bufferedPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val selectedServerNumber by viewModel.selectedServerNumber.collectAsStateWithLifecycle()
    val availableServers by viewModel.availableServers.collectAsStateWithLifecycle()
    val selectedAudioTrack by viewModel.selectedAudioTrack.collectAsStateWithLifecycle()
    val availableAudioTracks by viewModel.availableAudioTracks.collectAsStateWithLifecycle()
    val selectedQuality by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val availableQualities by viewModel.availableQualities.collectAsStateWithLifecycle()
    val isHudVisible by viewModel.isHudVisible.collectAsStateWithLifecycle()
    val isServerDialogOpen by viewModel.isServerDialogOpen.collectAsStateWithLifecycle()
    val isAudioDialogOpen by viewModel.isAudioDialogOpen.collectAsStateWithLifecycle()
    val isQualityDialogOpen by viewModel.isQualityDialogOpen.collectAsStateWithLifecycle()
    val isSettingsMenuOpen by viewModel.isSettingsMenuOpen.collectAsStateWithLifecycle()
    val engineType by viewModel.engineType.collectAsStateWithLifecycle()
    val extractedStream by viewModel.extractedStream.collectAsStateWithLifecycle()
    val clickFeedbackText by viewModel.clickFeedbackText.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activityManager = remember(context) {
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }
    val isLowRamDevice = remember(activityManager) {
        activityManager?.isLowRamDevice == true || Runtime.getRuntime().maxMemory() <= (192 * 1024 * 1024)
    }

    val rootFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }

    val anyDialogOpen = isServerDialogOpen || isAudioDialogOpen || isQualityDialogOpen || isSettingsMenuOpen

    BackHandler {
        if (anyDialogOpen) {
            viewModel.closeAllDialogs()
        } else if (isHudVisible) {
            viewModel.hideHud()
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
                    if (anyDialogOpen) {
                        if (event.key == Key.Back || event.key == Key.Escape) {
                            viewModel.closeAllDialogs()
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }

                    when {
                        // 1. Menu or Settings key: Opens the Settings UI regardless of HUD state
                        event.key == Key.Menu || event.key == Key.Settings -> {
                            viewModel.openSettingsMenu()
                            true
                        }

                        // 2. Play / Pause media hardware keys
                        event.key == Key.MediaPlayPause -> {
                            viewModel.togglePlayPause()
                            true
                        }
                        event.key == Key.MediaPlay -> {
                            if (!isPlaying) viewModel.togglePlayPause()
                            true
                        }
                        event.key == Key.MediaPause -> {
                            if (isPlaying) viewModel.togglePlayPause()
                            true
                        }

                        // 3. When HUD is HIDDEN:
                        !isHudVisible -> {
                            when (event.key) {
                                // Right / Left: ONLY video is seeking, NO interface pops up
                                Key.DirectionLeft, Key.MediaRewind -> {
                                    viewModel.seekBy(-10_000L)
                                    true
                                }
                                Key.DirectionRight, Key.MediaFastForward -> {
                                    viewModel.seekBy(10_000L)
                                    true
                                }

                                // OK / Center button:
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    if (engineType == AnimePlayerEngineType.WEBVIEW) {
                                        viewModel.dispatchCenterClick()
                                    } else {
                                        viewModel.showHud()
                                        runCatching { playButtonFocusRequester.requestFocus() }
                                    }
                                    true
                                }

                                // Up / Down: Interface pops up and focus lands on controls
                                Key.DirectionUp, Key.DirectionDown -> {
                                    viewModel.showHud()
                                    runCatching { playButtonFocusRequester.requestFocus() }
                                    true
                                }

                                // Back key when HUD is hidden exits the video
                                Key.Back, Key.Escape -> {
                                    onBackPressed()
                                    true
                                }

                                else -> false
                            }
                        }

                        // 4. When HUD is VISIBLE:
                        else -> {
                            viewModel.userInteracted()
                            when (event.key) {
                                // Exit (Back) hides the interface
                                Key.Back, Key.Escape -> {
                                    viewModel.hideHud()
                                    runCatching { rootFocusRequester.requestFocus() }
                                    true
                                }

                                // Arrow keys & Enter: Handled by Compose TV focus navigation between buttons
                                Key.DirectionLeft, Key.DirectionRight,
                                Key.DirectionUp, Key.DirectionDown,
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    false
                                }

                                else -> false
                            }
                        }
                    }
                } else false
            }
    ) {
        when (val state = uiState) {
            is AnimePlayerUiState.Loading -> {
                Loading(modifier = Modifier.fillMaxSize())
            }

            is AnimePlayerUiState.Error -> {
                AnimePlayerErrorView(
                    message = state.message,
                    onRetry = { viewModel.loadPlaybackData() },
                    onSwitchServer = { viewModel.switchToNextServer() },
                    onSwitchEngine = { viewModel.selectEngine(AnimePlayerEngineType.WEBVIEW) },
                    onBack = onBackPressed
                )
            }

            is AnimePlayerUiState.Ready -> {
                val stream = extractedStream

                // 1. Playback Engine: ExoPlayer (Native Hardware) vs WebView (Embed)
                if (stream == null) {
                    Loading(modifier = Modifier.fillMaxSize())
                } else if (engineType == AnimePlayerEngineType.EXOPLAYER) {
                    if (stream.isDirectStream) {
                        AnimeExoPlayerView(
                            streamUrl = stream.streamUrl,
                            headers = stream.headers,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AnimePlayerErrorView(
                            message = "Direct video stream not found for Server $selectedServerNumber.\nPlease switch to WebView Player or Server ${if (selectedServerNumber == 1) 2 else 1}.",
                            onRetry = { viewModel.loadPlaybackData() },
                            onSwitchServer = { viewModel.switchToNextServer() },
                            onSwitchEngine = { viewModel.selectEngine(AnimePlayerEngineType.WEBVIEW) },
                            onBack = onBackPressed
                        )
                    }
                } else {
                    // WebView Player with 512MB RAM optimizations & simulated remote clicks
                    AnimeWebViewPlayerView(
                        embedUrl = state.activeServerUrl,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Transient visual feedback badge for remote center clicks
                clickFeedbackText?.let { feedback ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                                .border(1.dp, Color(0xFFE50914), RoundedCornerShape(24.dp))
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = feedback,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. On-Screen TV HUD Overlay (Speed, Buffer, Data, Controls)
                AnimePlayerHud(
                    isVisible = isHudVisible,
                    title = state.title,
                    subtitle = state.subtitle,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    bufferedPositionMs = bufferedPositionMs,
                    durationMs = durationMs,
                    diagnostics = diagnostics,
                    selectedServerNumber = selectedServerNumber,
                    selectedAudioTrack = selectedAudioTrack,
                    selectedQuality = selectedQuality,
                    playButtonFocusRequester = playButtonFocusRequester,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onRewindClick = { viewModel.seekBy(-10_000L) },
                    onFastForwardClick = { viewModel.seekBy(10_000L) },
                    onServerClick = { viewModel.openServerDialog() },
                    onAudioClick = { viewModel.openAudioDialog() },
                    onQualityClick = { viewModel.openQualityDialog() },
                    onSettingsClick = { viewModel.openSettingsMenu() }
                )

                // 3. Unified Settings Pop-up (Triggered by TV Menu key or Settings button)
                if (isSettingsMenuOpen) {
                    AnimeUnifiedSettingsDialog(
                        engineType = engineType,
                        servers = availableServers,
                        selectedServerNumber = selectedServerNumber,
                        audioTracks = availableAudioTracks,
                        selectedAudioTrack = selectedAudioTrack,
                        qualities = availableQualities,
                        selectedQuality = selectedQuality,
                        onEngineSelect = { eng -> viewModel.selectEngine(eng) },
                        onServerSelect = { sNum -> viewModel.selectServer(sNum) },
                        onAudioSelect = { track -> viewModel.selectAudioTrack(track) },
                        onQualitySelect = { q -> viewModel.selectQuality(q) },
                        onDismiss = {
                            viewModel.closeSettingsMenu()
                            runCatching { playButtonFocusRequester.requestFocus() }
                        }
                    )
                }

                // 4. Modals: Server 1 vs Server 2
                if (isServerDialogOpen) {
                    AnimeServerDialog(
                        servers = availableServers,
                        selectedServerNumber = selectedServerNumber,
                        onServerSelect = { sNum -> viewModel.selectServer(sNum) },
                        onDismiss = {
                            viewModel.closeAllDialogs()
                            runCatching { playButtonFocusRequester.requestFocus() }
                        }
                    )
                }

                // 5. Modals: Audio Selection (Multi-Audio)
                if (isAudioDialogOpen) {
                    AnimeAudioDialog(
                        audioTracks = availableAudioTracks,
                        selectedTrack = selectedAudioTrack,
                        onAudioSelect = { track -> viewModel.selectAudioTrack(track) },
                        onDismiss = {
                            viewModel.closeAllDialogs()
                            runCatching { playButtonFocusRequester.requestFocus() }
                        }
                    )
                }

                // 6. Modals: Video Quality Selection
                if (isQualityDialogOpen) {
                    AnimeQualityDialog(
                        qualities = availableQualities,
                        selectedQuality = selectedQuality,
                        onQualitySelect = { q -> viewModel.selectQuality(q) },
                        onDismiss = {
                            viewModel.closeAllDialogs()
                            runCatching { playButtonFocusRequester.requestFocus() }
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is AnimePlayerUiState.Ready) {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(isHudVisible, anyDialogOpen) {
        if (isHudVisible && !anyDialogOpen) {
            delay(150)
            runCatching { playButtonFocusRequester.requestFocus() }
        } else if (!isHudVisible && !anyDialogOpen) {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }
}

@Composable
private fun AnimePlayerErrorView(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSwitchServer: (() -> Unit)? = null,
    onSwitchEngine: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val retryFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Unable to Stream Anime",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.focusRequester(retryFocusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = Color.White
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "Retry Stream",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                if (onSwitchEngine != null) {
                    Button(
                        onClick = onSwitchEngine,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFFE50914),
                            focusedContainerColor = Color.White
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "Play in WebView",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                if (onSwitchServer != null) {
                    Button(
                        onClick = onSwitchServer,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            focusedContainerColor = Color.White
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "Switch Server",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = Color.White
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "Go Back",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { retryFocusRequester.requestFocus() }
    }
}
