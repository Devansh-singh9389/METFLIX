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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
        player.seekTo(player.duration.times(it).toLong())
    },
    onShowControls: () -> Unit = {},
) {
    val contentDuration = player.contentDuration.milliseconds

    var currentPositionMs by remember(player) { mutableLongStateOf(0L) }
    val currentPosition = currentPositionMs.milliseconds

    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying) {
                currentPositionMs = player.currentPosition
                delay(1000)
            } else {
                currentPositionMs = player.currentPosition
                delay(500)
            }
        }
    }

    val contentProgressString =
        currentPosition.toComponents { h, m, s, _ ->
            if (h > 0) {
                "$h:${m.padStartWith0()}:${s.padStartWith0()}"
            } else {
                "${m.padStartWith0()}:${s.padStartWith0()}"
            }
        }
    val contentDurationString =
        contentDuration.toComponents { h, m, s, _ ->
            if (h > 0) {
                "$h:${m.padStartWith0()}:${s.padStartWith0()}"
            } else {
                "${m.padStartWith0()}:${s.padStartWith0()}"
            }
        }

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
            isPlaying = player.isPlaying,
            contentDescription = StringConstants
                .Composable
                .VideoPlayerControlPlayPauseButton
        )
        VideoPlayerControllerText(text = contentProgressString)
        VideoPlayerControllerIndicator(
            progress = if (contentDuration.inWholeMilliseconds > 0) {
                (currentPositionMs.toFloat() / contentDuration.inWholeMilliseconds.toFloat()).coerceIn(0f, 1f)
            } else 0f,
            onSeek = onSeek,
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
