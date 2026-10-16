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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.example.metflix.presentation.utils.handleDPadKeyEvents

@Composable
fun RowScope.VideoPlayerControllerIndicator(
    progress: Float,
    bufferedProgress: Float = 0f,
    onSeek: (seekProgress: Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeekingChanged: (isSeeking: Boolean, seekProgress: Float) -> Unit = { _, _ -> },
    onShowControls: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(progress) }

    // Keep scrubProgress in sync with playback when not actively scrubbing
    LaunchedEffect(progress, isScrubbing) {
        if (!isScrubbing) {
            scrubProgress = progress
        }
    }

    // Cancel scrubbing if user navigates away from seeker
    LaunchedEffect(isFocused) {
        if (!isFocused && isScrubbing) {
            isScrubbing = false
            onSeekingChanged(false, progress)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val activeTrackColor = if (isFocused) primaryColor else MaterialTheme.colorScheme.onSurface
    val bufferedColor = Color.White.copy(alpha = 0.35f)
    val backgroundTrackColor = Color.White.copy(alpha = 0.18f)

    val animatedIndicatorHeight by animateDpAsState(
        targetValue = if (isFocused) 8.dp else 4.dp,
        label = "indicatorHeight"
    )

    val thumbRadius by animateDpAsState(
        targetValue = if (isFocused) 7.dp else 0.dp,
        label = "thumbRadius"
    )

    val handleKeyEvents = Modifier.handleDPadKeyEvents(
        onLeft = {
            onShowControls()
            isScrubbing = true
            scrubProgress = (scrubProgress - 0.015f).coerceIn(0f, 1f)
            onSeekingChanged(true, scrubProgress)
        },
        onRight = {
            onShowControls()
            isScrubbing = true
            scrubProgress = (scrubProgress + 0.015f).coerceIn(0f, 1f)
            onSeekingChanged(true, scrubProgress)
        },
        onEnter = {
            onShowControls()
            if (isScrubbing) {
                onSeek(scrubProgress)
                isScrubbing = false
                onSeekingChanged(false, scrubProgress)
            } else {
                isScrubbing = true
                scrubProgress = progress
                onSeekingChanged(true, scrubProgress)
            }
        }
    )

    Canvas(
        modifier = modifier
            .weight(1f)
            .height(24.dp)
            .padding(horizontal = 6.dp)
            .then(handleKeyEvents)
            .focusable(interactionSource = interactionSource),
        onDraw = {
            val yOffset = size.height / 2f
            val trackHeight = animatedIndicatorHeight.toPx()
            val currentProgressValue = if (isScrubbing) scrubProgress else progress.coerceIn(0f, 1f)
            val currentBufferedValue = bufferedProgress.coerceIn(0f, 1f)

            // 1. Background track
            drawLine(
                color = backgroundTrackColor,
                start = Offset(x = 0f, y = yOffset),
                end = Offset(x = size.width, y = yOffset),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            // 2. Buffered streaming progress track
            if (currentBufferedValue > 0f) {
                drawLine(
                    color = bufferedColor,
                    start = Offset(x = 0f, y = yOffset),
                    end = Offset(x = size.width * currentBufferedValue, y = yOffset),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            // 3. Active playback / scrubbed track
            val playedEndX = size.width * currentProgressValue
            if (playedEndX > 0f) {
                drawLine(
                    color = activeTrackColor,
                    start = Offset(x = 0f, y = yOffset),
                    end = Offset(x = playedEndX, y = yOffset),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            // 4. Scrubber glowing thumb head when focused
            if (thumbRadius.toPx() > 0f) {
                val thumbCenter = Offset(x = playedEndX.coerceIn(0f, size.width), y = yOffset)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f),
                    radius = thumbRadius.toPx() + 3.dp.toPx(),
                    center = thumbCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius.toPx(),
                    center = thumbCenter
                )
            }
        }
    )
}
