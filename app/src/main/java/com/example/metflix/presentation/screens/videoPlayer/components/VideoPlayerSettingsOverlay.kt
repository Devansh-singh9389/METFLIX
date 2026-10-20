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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.metflix.data.network.resolvers.StreamServer

import kotlinx.coroutines.delay

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
    availableServers: List<StreamServer> = emptyList(),
    selectedServer: StreamServer? = null,
    onSelectServer: (StreamServer) -> Unit = {},
    currentQualityMaxHeight: Int = 0,
    availableQualityHeights: List<Int> = emptyList(),
    sourceResolutionLabel: String = "",
    availableAudioTracks: List<String> = emptyList(),
    selectedAudioTrackIndex: Int = 0,
    availableSubtitleTracks: List<String> = emptyList(),
    selectedSubtitleTrackIndex: Int = -1,
    currentSleepTimerMinutes: Int = 0,
    isStatsVisible: Boolean = false,
    isWebPlayerMode: Boolean = false,
    nativeControlsEnabled: Boolean = !isWebPlayerMode,
    onSelectSpeed: (Float) -> Unit,
    onSelectContentScale: (ContentScale) -> Unit,
    onSelectQuality: (Int) -> Unit = {},
    onSelectAudioTrack: (Int) -> Unit = {},
    onSelectSubtitleTrack: (Int) -> Unit = {},
    onSelectSleepTimer: (Int) -> Unit = {},
    onTogglePlayerEngine: (Boolean) -> Unit = {},
    onToggleStats: () -> Unit = {},
    onRestartMovie: () -> Unit = {},
    debridStatus: String = "",
    isDebridActive: Boolean = false,
    onSelectDebridProvider: (com.example.metflix.data.settings.DebridProviderType) -> Unit = {},
    onSaveDebridKey: (String) -> Unit = {},
    onToggleDebrid: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isPresented) return

    BackHandler(onBack = onDismiss)

    val dialogFirstFocusRequester = remember { FocusRequester() }
    var isEditingKey by remember { mutableStateOf(false) }
    var tempKey by remember { mutableStateOf("") }

    LaunchedEffect(isPresented) {
        if (isPresented) {
            runCatching { dialogFirstFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Menu, Key.Settings -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(640.dp)
                .padding(vertical = 16.dp),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            onClick = {}
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
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
                        modifier = Modifier.focusRequester(dialogFirstFocusRequester),
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
            }

                // 1. Streaming Server / Multi-Server Section
                if (availableServers.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsSectionHeader(
                                icon = Icons.Default.Dns,
                                title = "Streaming Server / Source"
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                availableServers.chunked(2).forEach { rowServers ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowServers.forEach { server ->
                                            val isSelected = selectedServer?.id == server.id

                                            ServerPillButton(
                                                server = server,
                                                isSelected = isSelected,
                                                onClick = { onSelectServer(server) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowServers.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                Text(
                                    text = "⚡ Servers are probed in parallel and dynamically ranked by response time, direct stream, and regional audio match.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // 1.5. Playback Engine Section (Web Embed vs Native ExoPlayer)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Playback Engine",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onTogglePlayerEngine(true) },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isWebPlayerMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text("Web Embed", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = { onTogglePlayerEngine(false) },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (!isWebPlayerMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text("Native (ExoPlayer)", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // 2. Video Quality / Multi-Resolution Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
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
                                    text = "Video Quality & Resolution",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
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
                                        text = "Live: $sourceResolutionLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (isWebPlayerMode) {
                            WebEmbedEngineNotice(
                                title = "Video Quality Managed in Web Frame",
                                description = "Quality & resolution are handled automatically by the web stream provider. Switch to Native engine if available for direct hardware resolution selection."
                            )
                        } else {
                            val dynamicQualityOptions = remember(availableQualityHeights) {
                                if (availableQualityHeights.isNotEmpty()) {
                                    listOf("Auto" to 0) + availableQualityHeights.sortedDescending().map { h ->
                                        val label = if (h >= 2160) "4K (${h}p)" else "${h}p"
                                        label to h
                                    }
                                } else {
                                    VideoQualityOptions
                                }
                            }

                            val qualityChunkSize = if (dynamicQualityOptions.size > 5) 4 else dynamicQualityOptions.size
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                dynamicQualityOptions.chunked(qualityChunkSize).forEach { rowOptions ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowOptions.forEach { (label, height) ->
                                            val isSelected = currentQualityMaxHeight == height
                                            OptionPillButton(
                                                label = label,
                                                isSelected = isSelected,
                                                onClick = { onSelectQuality(height) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(qualityChunkSize - rowOptions.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            val qualityDescription = when {
                                currentQualityMaxHeight == 0 -> "Auto: Dynamically adapts stream quality and bitrates to your network for smooth playback."
                                currentQualityMaxHeight >= 2160 -> "${currentQualityMaxHeight}p Ultra HD (4K): Maximum visual fidelity for 4K displays."
                                currentQualityMaxHeight >= 1080 -> "1080p Full HD: Maximum crispness, recommended for high-speed Wi-Fi & Ethernet."
                                currentQualityMaxHeight >= 720 -> "720p HD: High definition with balanced internet data consumption."
                                currentQualityMaxHeight >= 480 -> "480p SD: Data Saver mode — uses up to 80% less internet bandwidth."
                                currentQualityMaxHeight > 0 -> "${currentQualityMaxHeight}p: Minimum data usage for low-speed connections."
                                else -> "Adaptive streaming"
                            }

                            Text(
                                text = qualityDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // 2. AllDebrid Cloud Account & High-Speed Dual-Audio
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsSectionHeader(
                            icon = Icons.Default.VpnKey,
                            title = "AllDebrid Cloud Streaming (Instant Dual-Audio)"
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isDebridActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
                            onClick = {}
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isDebridActive) "Cloud Debrid Active" else "Cloud Debrid Disabled / Unconfigured",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDebridActive) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                        Text(
                                            text = if (debridStatus.isNotBlank()) debridStatus else "High-speed cached torrent unthrottling for Dual-Audio (Hindi+Eng) streams.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray
                                        )
                                    }
                                }

                                // Provider Selection Pills (AllDebrid vs TorBox)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    com.example.metflix.data.settings.DebridProviderType.entries.forEach { provider ->
                                        Button(
                                            onClick = { onSelectDebridProvider(provider) },
                                            colors = ButtonDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                focusedContainerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Text(provider.displayName, fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (isEditingKey) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BasicTextField(
                                            value = tempKey,
                                            onValueChange = { tempKey = it },
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            singleLine = true
                                        )

                                        Button(
                                            onClick = {
                                                onSaveDebridKey(tempKey)
                                            },
                                            colors = ButtonDefaults.colors(
                                                containerColor = Color(0xFF2E7D32),
                                                focusedContainerColor = Color(0xFF4CAF50)
                                            )
                                        ) {
                                            Text("Test & Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { isEditingKey = false },
                                            colors = ButtonDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Cancel", fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { isEditingKey = true },
                                            colors = ButtonDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                focusedContainerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Text(
                                                text = if (isDebridActive) "Change API Key" else "Enter AllDebrid API Key",
                                                fontSize = 12.sp
                                            )
                                        }

                                        if (isDebridActive) {
                                            Button(
                                                onClick = { onToggleDebrid(false) },
                                                colors = ButtonDefaults.colors(
                                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                    focusedContainerColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text("Disable", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Audio Track / Multi-Audio Selection
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsSectionHeader(
                            icon = Icons.Default.Audiotrack,
                            title = "Audio Track & Language"
                        )

                        if (isWebPlayerMode) {
                            WebEmbedEngineNotice(
                                title = "Audio Managed by Web Player",
                                description = "Audio language and multichannel streams are embedded within the web player interface."
                            )
                        } else if (availableAudioTracks.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                availableAudioTracks.chunked(3).forEachIndexed { chunkIndex, rowTracks ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowTracks.forEachIndexed { itemIndex, trackLabel ->
                                            val index = chunkIndex * 3 + itemIndex
                                            val isSelected = selectedAudioTrackIndex == index
                                            val displayLabel = if (isSelected) "✓ $trackLabel" else trackLabel
                                            OptionPillButton(
                                                label = displayLabel,
                                                isSelected = isSelected,
                                                onClick = { onSelectAudioTrack(index) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - rowTracks.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                if (availableAudioTracks.size <= 1) {
                                    val noticeText = when (selectedServer?.id) {
                                        "anime" -> "💡 AnimeWorld multi-audio stream active. If alternative dubs (Hindi/English) are available on the CDN for this episode, they appear above."
                                        "dualaudio" -> "💡 Server 1 searched Indian Dual-Audio catalogs. If no Hindi release has dropped online yet for this title, it plays in English."
                                        else -> "💡 This stream has 1 audio track. For Hindi & Regional dubs, switch to Server 1 (Dual-Audio) or Server 0 (AnimeWorld Multi-Audio)."
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Color.White.copy(alpha = 0.05f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = noticeText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            val noticeText = if (selectedServer?.id == "dualaudio") {
                                "Primary Direct Audio (Stereo / Multichannel Pass-Through) • No Hindi release found yet for this title; streaming in English"
                            } else {
                                "Primary Direct Audio (Stereo / Multichannel Pass-Through) • Switch to Server 1 (Dual-Audio) for Hindi / Regional dubs"
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
                                    text = noticeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // 4. Subtitle Selection
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsSectionHeader(
                            icon = Icons.Default.ClosedCaption,
                            title = "Subtitles / Captions"
                        )

                        if (isWebPlayerMode) {
                            WebEmbedEngineNotice(
                                title = "Subtitles Managed by Web Player",
                                description = "Subtitles are rendered directly within the web player stream iframe."
                            )
                        } else if (availableSubtitleTracks.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val allTracks = listOf("Off") + availableSubtitleTracks
                                allTracks.chunked(3).forEachIndexed { chunkIndex, rowTracks ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowTracks.forEachIndexed { itemIndex, trackLabel ->
                                            val index = chunkIndex * 3 + itemIndex - 1
                                            val isSelected = selectedSubtitleTrackIndex == index
                                            val displayLabel = if (isSelected) "✓ $trackLabel" else trackLabel
                                            OptionPillButton(
                                                label = displayLabel,
                                                isSelected = isSelected,
                                                onClick = { onSelectSubtitleTrack(index) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - rowTracks.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
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
                                    text = "No embedded subtitle tracks detected for this stream",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // 5. Screen Aspect Ratio / Scale
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                            scaleModes.forEach { (title, scale) ->
                                val isSelected = currentContentScale == scale
                                OptionPillButton(
                                    label = title,
                                    isSelected = isSelected,
                                    onClick = { onSelectContentScale(scale) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 6. Playback Speed
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsSectionHeader(
                            icon = Icons.Default.Speed,
                            title = "Playback Speed"
                        )

                        if (!nativeControlsEnabled) {
                            WebEmbedEngineNotice(
                                title = "Speed Managed by Web Player",
                                description = "Playback speed is controlled by the embedded provider."
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SpeedOptions.forEach { speed ->
                                    val isSelected = currentSpeed == speed
                                    OptionPillButton(
                                        label = "${speed}x",
                                        isSelected = isSelected,
                                        onClick = { onSelectSpeed(speed) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 7. Sleep Timer
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SettingsSectionHeader(
                                icon = Icons.Default.Timer,
                                title = "Sleep Timer"
                            )
                            if (currentSleepTimerMinutes > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color(0xFF4CAF50).copy(alpha = 0.2f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Active: ${currentSleepTimerMinutes}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF81C784),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

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
                    }
                }

                // 8. Stream Diagnostics & Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onToggleStats,
                            modifier = Modifier.weight(1f),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = if (isStatsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                focusedContainerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(text = if (isStatsVisible) "Hide Diagnostics HUD" else "Show Diagnostics HUD")
                        }

                        Button(
                            onClick = onRestartMovie,
                            modifier = Modifier.weight(1f),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                focusedContainerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Restart Video")
                        }
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
private fun ServerPillButton(
    server: StreamServer,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(44.dp),
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (isSelected) {
                    Text(
                        text = "✓",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                } else if (server.rank in 1..3) {
                    Text(
                        text = "#${server.rank}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                }
                Text(
                    text = server.name,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (server.isRegionalMatch) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFFFF9800).copy(alpha = 0.25f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "REGIONAL",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB74D)
                        )
                    }
                }
                val hasHindi = server.audioLanguages.any { it.equals("Hindi", ignoreCase = true) }
                val hasDual = server.audioLanguages.size > 1 || server.audioLanguages.any { it.contains("Dual", ignoreCase = true) }
                if (hasHindi) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFFE91E63).copy(alpha = 0.25f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "HINDI",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF80AB)
                        )
                    }
                } else if (hasDual) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFF00BCD4).copy(alpha = 0.25f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "DUAL",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80DEEA)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    val badgeText = if (server.latencyMs != null && server.latencyMs > 0) {
                        "${server.latencyMs}ms"
                    } else {
                        server.badge
                    }
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
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

@Composable
private fun WebEmbedEngineNotice(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 11.sp
            )
        }
    }
}
