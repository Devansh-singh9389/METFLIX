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

package com.example.metflix.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage

data class DownloadStreamOption(
    val quality: String,
    val sizeText: String = "Direct Stream",
    val directUrl: String,
    val availableLanguages: List<String> = listOf("Hindi", "English"),
    val format: String = "MP4"
)

sealed interface DownloadDialogState {
    data class Loading(val statusMessage: String = "Searching servers for direct stream links (3s - 30s)...") : DownloadDialogState
    data class Ready(val options: List<DownloadStreamOption>) : DownloadDialogState
    data class NotDownloadable(
        val checkedServers: List<String> = listOf("Server 1 (Primary Direct)", "Server 2 (Dual-Audio Cloud)", "Server 3 (HLS Scraper)")
    ) : DownloadDialogState
}

@Composable
fun MediaDownloadDialog(
    title: String,
    posterUrl: String? = null,
    resolveStreams: suspend () -> List<DownloadStreamOption>,
    onDismiss: () -> Unit,
    onStartDownload: (option: DownloadStreamOption, selectedLanguages: List<String>) -> Unit
) {
    var state by remember { mutableStateOf<DownloadDialogState>(DownloadDialogState.Loading()) }
    var selectedOptionIndex by remember { mutableStateOf(0) }
    var selectedLanguages by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(title) {
        state = DownloadDialogState.Loading("Searching servers for direct stream links (3s - 30s)...")
        val resolved = resolveStreams()
        if (resolved.isNotEmpty()) {
            state = DownloadDialogState.Ready(resolved)
            val defaultOption = resolved.first()
            selectedLanguages = defaultOption.availableLanguages.toSet()
        } else {
            state = DownloadDialogState.NotDownloadable()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(520.dp)
                .border(
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ),
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF1E1E24)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header (YouTube-Style Media Banner)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = title,
                            modifier = Modifier
                                .size(width = 54.dp, height = 76.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 54.dp, height = 76.dp)
                                .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.LightGray
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Download Video",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 2
                        )
                    }
                }

                when (val currentState = state) {
                    is DownloadDialogState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Loading()
                                Text(
                                    text = currentState.statusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = "Inspecting Server 1, Server 2 & Server 3 for direct links...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    is DownloadDialogState.NotDownloadable -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFC62828).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFEF5350),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Video Not Downloadable",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFEF5350)
                                    )
                                }
                                Text(
                                    text = "None of the scraped servers contain a direct download link for this title.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Servers checked: ${currentState.checkedServers.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("Close", fontWeight = FontWeight.Bold)
                        }
                    }

                    is DownloadDialogState.Ready -> {
                        val options = currentState.options

                        // 1. Dynamic Quality Selection (Only qualities present in resolved direct links)
                        Text(
                            text = "Select Quality",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )

                        options.forEachIndexed { idx, option ->
                            val isSelected = idx == selectedOptionIndex
                            Surface(
                                onClick = {
                                    selectedOptionIndex = idx
                                    selectedLanguages = option.availableLanguages.toSet()
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Color(0xFF2C2C35) else Color(0xFF141419)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    border = Border(
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
                                        )
                                    )
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Hd,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = option.quality,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) Color.White else Color.LightGray
                                            )
                                            Text(
                                                text = "${option.sizeText} • Direct ${option.format}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val activeOption = options.getOrNull(selectedOptionIndex) ?: options.first()

                        // 2. Multi-Select Audio Languages
                        if (activeOption.availableLanguages.isNotEmpty()) {
                            Text(
                                text = "Select Audio Languages (Multi-Select)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                activeOption.availableLanguages.forEach { lang ->
                                    val isLangSelected = selectedLanguages.contains(lang)
                                    Button(
                                        onClick = {
                                            val current = selectedLanguages.toMutableSet()
                                            if (current.contains(lang)) {
                                                if (current.size > 1) current.remove(lang)
                                            } else {
                                                current.add(lang)
                                            }
                                            selectedLanguages = current
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isLangSelected) MaterialTheme.colorScheme.primary else Color(0xFF2C2C35)
                                        )
                                    ) {
                                        Text(
                                            text = if (isLangSelected) "✓ $lang" else "+ $lang",
                                            fontSize = 12.sp,
                                            color = if (isLangSelected) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Action Buttons Row (Download & Cancel)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    onStartDownload(activeOption, selectedLanguages.toList())
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    focusedContainerColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Download", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFF2C2C35)
                                )
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}
