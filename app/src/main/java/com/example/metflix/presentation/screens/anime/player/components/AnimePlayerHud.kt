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

package com.example.metflix.presentation.screens.anime.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.network.anime.models.AnimeAudioTrack
import com.example.metflix.data.network.anime.models.AnimePlaybackServer
import com.example.metflix.data.network.anime.models.AnimeQualityOption
import com.example.metflix.data.network.anime.models.AnimeStreamDiagnostics
import java.util.Locale

@Composable
fun AnimePlayerHud(
    isVisible: Boolean,
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    diagnostics: AnimeStreamDiagnostics,
    selectedServerNumber: Int,
    selectedAudioTrack: AnimeAudioTrack,
    selectedQuality: AnimeQualityOption,
    playButtonFocusRequester: FocusRequester,
    onPlayPauseClick: () -> Unit,
    onRewindClick: () -> Unit,
    onFastForwardClick: () -> Unit,
    onServerClick: () -> Unit,
    onAudioClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.82f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {
            // TOP BAR: Titles & Real-Time Stream Diagnostics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = Color.White
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB0B0B0)
                            )
                        }
                    }

                    // REAL-TIME STREAM METRICS ROW (Speed, Buffer, Data, Server, Audio)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Live Download Speed
                        AnimeMetricBadge(
                            icon = "⚡",
                            label = "Speed",
                            value = formatStreamSpeed(diagnostics.downloadSpeedBps),
                            accentColor = Color(0xFF00E5FF)
                        )

                        // Live Buffer Health
                        AnimeMetricBadge(
                            icon = "⏳",
                            label = "Buffer",
                            value = formatBufferHealth(diagnostics.bufferHealthMs),
                            accentColor = Color(0xFFFFD54F)
                        )

                        // Data Consumed
                        AnimeMetricBadge(
                            icon = "📶",
                            label = "Data Used",
                            value = formatStreamDataUsage(diagnostics.totalBytesDownloaded),
                            accentColor = Color(0xFF69F0AE)
                        )

                        // Active Server Badge
                        AnimeMetricBadge(
                            icon = "🖥️",
                            label = "Server",
                            value = "Server $selectedServerNumber",
                            accentColor = Color(0xFFE50914)
                        )

                        // Audio Badge
                        AnimeMetricBadge(
                            icon = "🔊",
                            label = "Audio",
                            value = selectedAudioTrack.displayName,
                            accentColor = Color(0xFFB388FF)
                        )
                    }
                }
            }

            // BOTTOM BAR: Dual-Track Progress Bar & TV Action Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                // Dual-Track Progress Bar (Shows Current Playback + Buffered Progress Ahead)
                DualTrackProgressBar(
                    currentMs = currentPositionMs,
                    bufferedMs = bufferedPositionMs,
                    durationMs = durationMs
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Time counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (durationMs > 0) formatTime(durationMs) else "--:--",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // TV Action Buttons (Play/Pause, Rewind 10s, FastForward 10s, Server, Audio, Quality)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    TvPlayerActionButton(
                        icon = Icons.Default.FastRewind,
                        label = "-10s",
                        onClick = onRewindClick
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Play / Pause (Primary Action)
                    Button(
                        onClick = onPlayPauseClick,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = Color.White
                        ),
                        shape = ButtonDefaults.shape(shape = CircleShape),
                        modifier = Modifier
                            .size(54.dp)
                            .focusRequester(playButtonFocusRequester)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (isPlaying) Color.White else Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Fast Forward 10s
                    TvPlayerActionButton(
                        icon = Icons.Default.FastForward,
                        label = "+10s",
                        onClick = onFastForwardClick
                    )

                    Spacer(modifier = Modifier.width(28.dp))

                    // Server Selection (Server 1 / Server 2)
                    TvPlayerMenuButton(
                        icon = Icons.Default.Dns,
                        title = "Server $selectedServerNumber",
                        onClick = onServerClick
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Audio Selection
                    TvPlayerMenuButton(
                        icon = Icons.Default.RecordVoiceOver,
                        title = selectedAudioTrack.displayName,
                        onClick = onAudioClick
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Quality Selection
                    TvPlayerMenuButton(
                        icon = Icons.Default.HighQuality,
                        title = selectedQuality.label,
                        onClick = onQualityClick
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Settings / Menu
                    TvPlayerMenuButton(
                        icon = Icons.Default.Settings,
                        title = "Settings",
                        onClick = onSettingsClick
                    )
                }
            }
        }
    }
}

@Composable
private fun DualTrackProgressBar(
    currentMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val total = durationMs.coerceAtLeast(1L).toFloat()
    val playProgress = (currentMs.toFloat() / total).coerceIn(0f, 1f)
    val bufferProgress = (bufferedMs.toFloat() / total).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF333333))
    ) {
        // Buffer track (shows buffered video ahead of playback)
        if (bufferProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(bufferProgress)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.45f))
            )
        }

        // Active playback track
        if (playProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(playProgress)
                    .background(Color(0xFFE50914))
            )
        }
    }
}

@Composable
private fun AnimeMetricBadge(
    icon: String,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = icon, fontSize = 11.sp)
        Text(
            text = "$label:",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color.LightGray
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = accentColor
        )
    }
}

@Composable
private fun TvPlayerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF262626),
            focusedContainerColor = MaterialTheme.colorScheme.primary
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = Modifier.height(44.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
        }
    }
}

@Composable
private fun TvPlayerMenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF1E1E1E),
            focusedContainerColor = Color(0xFF00E5FF)
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = Modifier.height(44.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
        }
    }
}

fun formatStreamSpeed(bps: Long): String {
    return when {
        bps >= 1_000_000L -> String.format(Locale.US, "%.1f MB/s", bps / 1_000_000.0)
        bps >= 1_000L -> String.format(Locale.US, "%.0f KB/s", bps / 1_000.0)
        bps > 0L -> "$bps B/s"
        else -> "-- KB/s"
    }
}

fun formatBufferHealth(ms: Long): String {
    return when {
        ms >= 1000L -> String.format(Locale.US, "%.1fs", ms / 1000.0)
        ms > 0L -> "${ms}ms"
        else -> "0s"
    }
}

fun formatStreamDataUsage(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        bytes > 0L -> "$bytes B"
        else -> "0 KB"
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
