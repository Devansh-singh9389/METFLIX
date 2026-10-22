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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.network.anime.models.AnimeAudioTrack
import com.example.metflix.data.network.anime.models.AnimePlaybackServer
import com.example.metflix.data.network.anime.models.AnimePlayerEngineType
import com.example.metflix.data.network.anime.models.AnimeQualityOption

@Composable
fun AnimeServerDialog(
    servers: List<AnimePlaybackServer>,
    selectedServerNumber: Int,
    onServerSelect: (serverNumber: Int) -> Unit,
    onDismiss: () -> Unit
) {
    AnimeSettingsModal(
        title = "Choose Streaming Server",
        subtitle = "Select between Server 1 and Server 2",
        icon = Icons.Default.Dns,
        onDismiss = onDismiss
    ) {
        val firstRequester = remember { FocusRequester() }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            servers.take(2).forEachIndexed { index, server ->
                val isSelected = server.serverNumber == selectedServerNumber
                val focusRequester = if (index == 0) firstRequester else remember { FocusRequester() }

                Button(
                    onClick = { onServerSelect(server.serverNumber) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) Color(0xFFE50914).copy(alpha = 0.25f) else Color(0xFF262626),
                        focusedContainerColor = Color(0xFFE50914)
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = server.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = server.subtitle,
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            runCatching { firstRequester.requestFocus() }
        }
    }
}

@Composable
fun AnimeAudioDialog(
    audioTracks: List<AnimeAudioTrack>,
    selectedTrack: AnimeAudioTrack,
    onAudioSelect: (track: AnimeAudioTrack) -> Unit,
    onDismiss: () -> Unit
) {
    AnimeSettingsModal(
        title = "Audio Language",
        subtitle = "Choose audio track for this anime",
        icon = Icons.Default.RecordVoiceOver,
        onDismiss = onDismiss
    ) {
        val firstRequester = remember { FocusRequester() }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(audioTracks) { track ->
                val isSelected = track.code == selectedTrack.code
                val requester = if (track.code == audioTracks.firstOrNull()?.code) firstRequester else remember { FocusRequester() }

                Button(
                    onClick = { onAudioSelect(track) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(requester),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF262626),
                        focusedContainerColor = Color(0xFF00E5FF)
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.DarkGray)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = track.badge,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = track.displayName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            runCatching { firstRequester.requestFocus() }
        }
    }
}

@Composable
fun AnimeQualityDialog(
    qualities: List<AnimeQualityOption>,
    selectedQuality: AnimeQualityOption,
    onQualitySelect: (quality: AnimeQualityOption) -> Unit,
    onDismiss: () -> Unit
) {
    AnimeSettingsModal(
        title = "Video Quality",
        subtitle = "Select streaming resolution",
        icon = Icons.Default.HighQuality,
        onDismiss = onDismiss
    ) {
        val firstRequester = remember { FocusRequester() }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(qualities) { q ->
                val isSelected = q.label == selectedQuality.label
                val requester = if (q.label == qualities.firstOrNull()?.label) firstRequester else remember { FocusRequester() }

                Button(
                    onClick = { onQualitySelect(q) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(requester),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) Color(0xFF69F0AE).copy(alpha = 0.25f) else Color(0xFF262626),
                        focusedContainerColor = Color(0xFF69F0AE)
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = q.label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            runCatching { firstRequester.requestFocus() }
        }
    }
}

@Composable
fun AnimeUnifiedSettingsDialog(
    engineType: AnimePlayerEngineType,
    servers: List<AnimePlaybackServer>,
    selectedServerNumber: Int,
    audioTracks: List<AnimeAudioTrack>,
    selectedAudioTrack: AnimeAudioTrack,
    qualities: List<AnimeQualityOption>,
    selectedQuality: AnimeQualityOption,
    onEngineSelect: (AnimePlayerEngineType) -> Unit,
    onServerSelect: (Int) -> Unit,
    onAudioSelect: (AnimeAudioTrack) -> Unit,
    onQualitySelect: (AnimeQualityOption) -> Unit,
    onDismiss: () -> Unit
) {
    AnimeSettingsModal(
        title = "Playback Settings",
        subtitle = "Streaming Engine, Server, Audio & Quality",
        icon = Icons.Default.HighQuality,
        onDismiss = onDismiss
    ) {
        val firstRequester = remember { FocusRequester() }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // SECTION 0: PLAYBACK ENGINE (EXOPLAYER VS WEBVIEW)
            item {
                Text(
                    text = "PLAYBACK ENGINE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isExo = engineType == AnimePlayerEngineType.EXOPLAYER
                    Button(
                        onClick = { onEngineSelect(AnimePlayerEngineType.EXOPLAYER) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(firstRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = if (isExo) Color(0xFFFFD54F).copy(alpha = 0.25f) else Color(0xFF262626),
                            focusedContainerColor = Color(0xFFFFD54F)
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "🚀 ExoPlayer (Native)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isExo) Color.White else Color.LightGray
                        )
                    }

                    val isWeb = engineType == AnimePlayerEngineType.WEBVIEW
                    Button(
                        onClick = { onEngineSelect(AnimePlayerEngineType.WEBVIEW) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = if (isWeb) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF262626),
                            focusedContainerColor = Color(0xFF00E5FF)
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "🌐 WebView",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isWeb) Color.White else Color.LightGray
                        )
                    }
                }
            }

            // SECTION 1: SERVERS
            item {
                Text(
                    text = "STREAMING SERVER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    servers.take(2).forEachIndexed { index, server ->
                        val isSelected = server.serverNumber == selectedServerNumber
                        val req = if (index == 0) firstRequester else remember { FocusRequester() }

                        Button(
                            onClick = { onServerSelect(server.serverNumber) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(req),
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) Color(0xFFE50914).copy(alpha = 0.25f) else Color(0xFF262626),
                                focusedContainerColor = Color(0xFFE50914)
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = server.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = server.subtitle,
                                        fontSize = 12.sp,
                                        color = Color.LightGray
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2: AUDIO LANGUAGE
            item {
                Text(
                    text = "AUDIO LANGUAGE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    audioTracks.forEach { track ->
                        val isSelected = track.code == selectedAudioTrack.code
                        Button(
                            onClick = { onAudioSelect(track) },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF262626),
                                focusedContainerColor = Color(0xFF00E5FF)
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = track.badge,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 3: VIDEO QUALITY
            item {
                Text(
                    text = "VIDEO QUALITY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF69F0AE),
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    qualities.forEach { q ->
                        val isSelected = q.label == selectedQuality.label
                        Button(
                            onClick = { onQualitySelect(q) },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) Color(0xFF69F0AE).copy(alpha = 0.25f) else Color(0xFF262626),
                                focusedContainerColor = Color(0xFF69F0AE)
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = q.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            runCatching { firstRequester.requestFocus() }
        }
    }
}

@Composable
private fun AnimeSettingsModal(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161616))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                content()
            }
        }
    }
}
