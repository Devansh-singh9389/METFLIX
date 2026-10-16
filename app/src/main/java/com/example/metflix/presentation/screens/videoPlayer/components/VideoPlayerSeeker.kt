/*
 * Copyright 2024 Google LLC
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

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.example.metflix.data.util.StringConstants
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSeeker(
    player: Player,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    actionButtonsFocusRequester: FocusRequester? = null,
    state: PlayPauseButtonState = rememberPlayPauseButtonState(player),
    onSeek: (Float) -> Unit = {
        val targetMs = (player.duration.coerceAtLeast(0L) * it).toLong()
        player.seekTo(targetMs)
    },
    onShowControls: () -> Unit = {},
) {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var currentPositionMs by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var bufferedPositionMs by remember(player) { mutableLongStateOf(player.bufferedPosition.coerceAtLeast(0L)) }
    var durationMs by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = player.duration.coerceAtLeast(0L)
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (true) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(if (isPlaying) 500L else 1000L)
        }
    }

    val displayPositionMs = if (isScrubbing) {
        (durationMs * scrubProgress).toLong()
    } else {
        currentPositionMs
    }

    val currentPosition = displayPositionMs.milliseconds
    val totalDuration = durationMs.milliseconds

    val contentProgressString = currentPosition.toComponents { h, m, s, _ ->
        if (h > 0) {
            "$h:${m.padStartWith0()}:${s.padStartWith0()}"
        } else {
            "${m.padStartWith0()}:${s.padStartWith0()}"
        }
    }

    val contentDurationString = totalDuration.toComponents { h, m, s, _ ->
        if (h > 0) {
            "$h:${m.padStartWith0()}:${s.padStartWith0()}"
        } else {
            "${m.padStartWith0()}:${s.padStartWith0()}"
        }
    }

    val safeDuration = durationMs.coerceAtLeast(1L).toFloat()
    val playProgress = (currentPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val streamBufferedProgress = (bufferedPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoPlayerControlsIcon(
            modifier = Modifier
                .focusRequester(focusRequester)
                .then(
                    if (actionButtonsFocusRequester != null) {
                        Modifier.focusProperties {
                            up = actionButtonsFocusRequester
                        }
                    } else Modifier
                ),
            icon = if (state.showPlay) Icons.Default.PlayArrow else Icons.Default.Pause,
            onClick = state::onClick,
            isPlaying = isPlaying,
            contentDescription = StringConstants
                .Composable
                .VideoPlayerControlPlayPauseButton
        )
        VideoPlayerControllerText(text = contentProgressString)
        VideoPlayerControllerIndicator(
            progress = playProgress,
            bufferedProgress = streamBufferedProgress,
            onSeek = { seekTarget ->
                val targetMs = (durationMs * seekTarget).toLong()
                player.seekTo(targetMs)
                currentPositionMs = targetMs
                onSeek(seekTarget)
            },
            onSeekingChanged = { seeking, targetProgress ->
                isScrubbing = seeking
                scrubProgress = targetProgress
            },
            onShowControls = onShowControls,
            modifier = if (actionButtonsFocusRequester != null) {
                Modifier.focusProperties {
                    up = actionButtonsFocusRequester
                }
            } else Modifier
        )
        VideoPlayerControllerText(text = contentDurationString)
    }
}

private fun Number.padStartWith0() = this.toString().padStart(2, '0')
