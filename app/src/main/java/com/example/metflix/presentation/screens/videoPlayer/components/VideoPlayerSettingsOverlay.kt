/*
 * Copyright 2025 Google LLC
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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val SpeedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

val VideoQualityOptions = listOf(
    "Auto" to 0,
    "1080p" to 1080,
    "720p" to 720,
    "480p" to 480,
    "360p" to 360
)

val VideoSleepTimerOptions = listOf(
    "Off" to 0,
    "15m" to 15,
    "30m" to 30,
    "45m" to 45,
    "60m" to 60,
    "90m" to 90,
    "120m" to 120
)

@Composable
fun VideoPlayerSettingsOverlay(
    isPresented: Boolean,
    currentSpeed: Float,
    currentContentScale: ContentScale,
    currentQualityMaxHeight: Int = 0,
    availableQualityHeights: List<Int> = emptyList(),
    sourceResolutionLabel: String = "",
    availableAudioTracks: List<String> = emptyList(),
    selectedAudioTrackIndex: Int = 0,
    availableSubtitleTracks: List<String> = emptyList(),
    selectedSubtitleTrackIndex: Int = -1,
    currentSleepTimerMinutes: Int = 0,
    isStatsVisible: Boolean = false,
    onSelectSpeed: (Float) -> Unit,
    onSelectContentScale: (ContentScale) -> Unit,
    onSelectQuality: (Int) -> Unit = {},
    onSelectAudioTrack: (Int) -> Unit = {},
    onSelectSubtitleTrack: (Int) -> Unit = {},
    onSelectSleepTimer: (Int) -> Unit = {},
    onToggleStats: () -> Unit = {},
    onRestartMovie: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isPresented) return

    BackHandler(onBack = onDismiss)

    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { initialFocusRequester.requestFocus() }
    }

    val isSingleQuality = availableQualityHeights.size <= 1

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(620.dp)
                .padding(vertical = 20.dp),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            onClick = {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "Movie Playback Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 1. Video Aspect Ratio / Scale section
                SettingsSectionHeader(
                    icon = Icons.Default.AspectRatio,
                    title = "Screen Aspect Ratio"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scaleModes = listOf(
                        "Fit (Original)" to ContentScale.Fit,
                        "Zoom / Crop" to ContentScale.Crop,
                        "Stretch" to ContentScale.FillBounds
                    )

                    scaleModes.forEachIndexed { index, (title, scale) ->
                        val isSelected = currentContentScale == scale
                        val buttonModifier = if (index == 0) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else Modifier

                        OptionPillButton(
                            label = title,
                            isSelected = isSelected,
                            onClick = { onSelectContentScale(scale) },
                            modifier = buttonModifier.weight(1f)
                        )
                    }
                }

                // 2. Video Quality Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Video Quality",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (sourceResolutionLabel.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Source: $sourceResolutionLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (isSingleQuality) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptionPillButton(
                            label = if (sourceResolutionLabel.isNotBlank()) "Original ($sourceResolutionLabel)" else "Auto (Source Feed)",
                            isSelected = true,
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Stream provided at direct source quality with zero transcoding overhead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VideoQualityOptions.forEach { (label, height) ->
                            val isSelected = currentQualityMaxHeight == height
                            OptionPillButton(
                                label = label,
                                isSelected = isSelected,
                                onClick = { onSelectQuality(height) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val qualityDescription = when (currentQualityMaxHeight) {
                        0 -> "Auto: Dynamically adapts video quality to your internet speed for smooth playback."
                        1080 -> "1080p Full HD: Maximum crispness, recommended for high-speed Wi-Fi."
                        720 -> "720p HD: High definition with moderate bandwidth consumption."
                        480 -> "480p SD: Data Saver mode — uses up to 80% less internet data."
                        360 -> "360p Low: Minimum data usage for low-speed connections."
                        else -> "Adaptive streaming"
                    }

                    Text(
                        text = qualityDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }

                // 3. Audio Track Selection
                if (availableAudioTracks.isNotEmpty()) {
                    SettingsSectionHeader(
                        icon = Icons.Default.Audiotrack,
                        title = "Audio Track / Language"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableAudioTracks.forEachIndexed { index, trackLabel ->
                            val isSelected = selectedAudioTrackIndex == index
                            OptionPillButton(
                                label = trackLabel,
                                isSelected = isSelected,
                                onClick = { onSelectAudioTrack(index) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 4. Subtitle Selection
                SettingsSectionHeader(
                    icon = Icons.Default.ClosedCaption,
                    title = "Subtitles / Captions"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isOffSelected = selectedSubtitleTrackIndex < 0
                    OptionPillButton(
                        label = "Off",
                        isSelected = isOffSelected,
                        onClick = { onSelectSubtitleTrack(-1) },
                        modifier = Modifier.weight(1f)
                    )

                    val subTracks = if (availableSubtitleTracks.isEmpty()) listOf("English") else availableSubtitleTracks
                    subTracks.forEachIndexed { index, subLabel ->
                        val isSelected = selectedSubtitleTrackIndex == index
                        OptionPillButton(
                            label = subLabel,
                            isSelected = isSelected,
                            onClick = { onSelectSubtitleTrack(index) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 5. Playback Speed Section
                SettingsSectionHeader(
                    icon = Icons.Default.Speed,
                    title = "Playback Speed"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpeedOptions.forEach { speed ->
                        val isSelected = currentSpeed == speed
                        val label = if (speed == 1.0f) "1.0x" else "${speed}x"

                        OptionPillButton(
                            label = label,
                            isSelected = isSelected,
                            onClick = { onSelectSpeed(speed) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 6. Sleep Timer (Auto Off)
                SettingsSectionHeader(
                    icon = Icons.Default.Timer,
                    title = "Sleep Timer (Auto Pause)"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VideoSleepTimerOptions.forEach { (label, minutes) ->
                        val isSelected = currentSleepTimerMinutes == minutes
                        OptionPillButton(
                            label = label,
                            isSelected = isSelected,
                            onClick = { onSelectSleepTimer(minutes) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 7. Stream Actions & Diagnostics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onRestartMovie,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Restart Movie", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = onToggleStats,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = if (isStatsVisible) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            contentColor = if (isStatsVisible) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isStatsVisible) "Hide Stream Stats" else "Show Stream Stats",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun OptionPillButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(38.dp),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            focusedContainerColor = MaterialTheme.colorScheme.secondary,
            focusedContentColor = MaterialTheme.colorScheme.onSecondary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSecondary),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
