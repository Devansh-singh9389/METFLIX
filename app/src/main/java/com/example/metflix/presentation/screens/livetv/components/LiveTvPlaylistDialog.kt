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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
        title = "Hindi Channels (India)",
        description = "100+ Live Hindi channels: News, Movies, Music, Entertainment & Devotional",
        url = "https://iptv-org.github.io/iptv/languages/hin.m3u",
        icon = Icons.Default.Tv
    ),
    PresetPlaylist(
        title = "India (All Regional Languages)",
        description = "200+ Live Indian channels across all regional languages & states",
        url = "https://iptv-org.github.io/iptv/countries/in.m3u",
        icon = Icons.Default.Public
    ),
    PresetPlaylist(
        title = "World / Global Channels",
        description = "Complete global library of public IPTV channels across 100+ countries",
        url = "https://iptv-org.github.io/iptv/index.m3u",
        icon = Icons.Default.Public
    ),
    PresetPlaylist(
        title = "English Worldwide",
        description = "3,000+ English channels across all genres worldwide",
        url = "https://iptv-org.github.io/iptv/languages/eng.m3u",
        icon = Icons.Default.Language
    ),
    PresetPlaylist(
        title = "Live Sports Worldwide",
        description = "Live sports, motorsports, extreme sports, athletics & combat sports",
        url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
        icon = Icons.Default.SportsSoccer
    ),
    PresetPlaylist(
        title = "Movies & Cinema",
        description = "Free cinema, classic films, blockbusters & indie movies",
        url = "https://iptv-org.github.io/iptv/categories/movies.m3u",
        icon = Icons.Default.Movie
    ),
    PresetPlaylist(
        title = "24/7 International News",
        description = "1,000+ continuous news channels from around the globe",
        url = "https://iptv-org.github.io/iptv/categories/news.m3u",
        icon = Icons.Default.Newspaper
    ),
    PresetPlaylist(
        title = "Music & Concerts",
        description = "Live concert channels, music videos & radio networks",
        url = "https://iptv-org.github.io/iptv/categories/music.m3u",
        icon = Icons.Default.MusicNote
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
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Request focus on the first/active preset so D-pad works immediately
        initialFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(680.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    text = "Select a curated feed (Hindi, World, Sports, News) or enter a custom M3U URL:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Presets List with TV D-Pad Focus & native scrolling
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(PresetPlaylists) { index, preset ->
                        val isSelected = currentPlaylistUrl == preset.url
                        val itemModifier = if (index == 0) {
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(initialFocusRequester)
                        } else {
                            Modifier.fillMaxWidth()
                        }

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
                            modifier = itemModifier
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = preset.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
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

                Spacer(modifier = Modifier.height(2.dp))

                // Custom M3U URL Section
                Text(
                    text = "Custom M3U Playlist URL",
                    style = MaterialTheme.typography.titleSmall,
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
