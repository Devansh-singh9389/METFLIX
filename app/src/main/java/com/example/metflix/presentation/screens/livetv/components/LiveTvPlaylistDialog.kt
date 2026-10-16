package com.example.metflix.presentation.screens.livetv.components

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

data class PresetPlaylist(
    val title: String,
    val description: String,
    val url: String,
    val icon: ImageVector
)

val PresetPlaylists = listOf(
    PresetPlaylist(
        title = "iptv-org English",
        description = "3,000+ English channels across all genres worldwide",
        url = "https://iptv-org.github.io/iptv/languages/eng.m3u",
        icon = Icons.Default.Language
    ),
    PresetPlaylist(
        title = "iptv-org News",
        description = "1,000+ 24/7 International news channels",
        url = "https://iptv-org.github.io/iptv/categories/news.m3u",
        icon = Icons.Default.Newspaper
    ),
    PresetPlaylist(
        title = "iptv-org Sports",
        description = "Live sports, motorsports, extreme sports & athletics",
        url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
        icon = Icons.Default.SportsSoccer
    ),
    PresetPlaylist(
        title = "iptv-org Movies",
        description = "Free cinema, classic films, blockbusters & indie movies",
        url = "https://iptv-org.github.io/iptv/categories/movies.m3u",
        icon = Icons.Default.Movie
    ),
    PresetPlaylist(
        title = "iptv-org Music",
        description = "Live concert channels, music videos & radio networks",
        url = "https://iptv-org.github.io/iptv/categories/music.m3u",
        icon = Icons.Default.MusicNote
    ),
    PresetPlaylist(
        title = "iptv-org Global Index",
        description = "Complete global library of public IPTV channels",
        url = "https://iptv-org.github.io/iptv/index.m3u",
        icon = Icons.Default.Public
    )
)

@Composable
fun LiveTvPlaylistDialog(
    isPresented: Boolean,
    currentPlaylistUrl: String,
    onSelectPlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPresented) return

    BackHandler(onBack = onDismiss)

    var customUrlInput by remember(currentPlaylistUrl) { mutableStateOf(currentPlaylistUrl) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(640.dp)
                .padding(vertical = 20.dp),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
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
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "IPTV Sources & Playlists",
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

                Text(
                    text = "Select a curated IPTV feed from iptv-org or enter your own custom M3U playlist URL.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Presets
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetPlaylists.forEach { preset ->
                        val isSelected = currentPlaylistUrl == preset.url
                        Surface(
                            onClick = {
                                onSelectPlaylist(preset.url)
                                onDismiss()
                            },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                },
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.secondary,
                                focusedContentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSecondary),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = preset.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = preset.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Custom M3U URL Section
                Text(
                    text = "Custom M3U Playlist URL",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (customUrlInput.isNotBlank()) {
                                        onSelectPlaylist(customUrlInput)
                                        onDismiss()
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {
                            if (customUrlInput.isNotBlank()) {
                                onSelectPlaylist(customUrlInput)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(text = "Load URL")
                    }
                }
            }
        }
    }
}
