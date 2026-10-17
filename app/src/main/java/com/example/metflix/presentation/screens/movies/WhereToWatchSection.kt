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

package com.example.metflix.presentation.screens.movies

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.metflix.R
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.data.entities.WatchOptionType
import com.example.metflix.data.entities.WatchProvider
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import com.example.metflix.presentation.utils.PlayerResolver

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhereToWatchSection(
    movieDetails: MovieDetails,
    onPlayInMetFlix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val childPadding = rememberChildPadding()
    val watchInfo = movieDetails.watchProviders
    val allProviders = remember(watchInfo) {
        val list = mutableListOf<WatchProvider>()
        watchInfo?.streamProviders?.let { list.addAll(it) }
        watchInfo?.freeProviders?.let { list.addAll(it) }
        watchInfo?.rentProviders?.let { list.addAll(it) }
        watchInfo?.buyProviders?.let { list.addAll(it) }
        list.distinctBy { it.name }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = childPadding.start)
            .padding(top = 28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.where_to_watch),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = stringResource(R.string.streaming_data_powered_by_justwatch),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (allProviders.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Internal Player Option (First priority)
                Card(
                    onClick = onPlayInMetFlix,
                    colors = CardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        focusedContainerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                    border = CardDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary))
                    ),
                    modifier = Modifier.height(64.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "MetFlix Stream",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Native ExoPlayer (4K/HD)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Watch Providers (TMDb Helper Player integration)
                allProviders.take(6).forEach { provider ->
                    val installedPkg = remember(provider.name, provider.appPackage) {
                        PlayerResolver.findInstalledPackage(context, provider.name, provider.appPackage)
                    }
                    val isInstalled = installedPkg != null

                    Card(
                        onClick = {
                            PlayerResolver.launchProvider(
                                context = context,
                                provider = provider,
                                movieTitle = movieDetails.name,
                                tmdbLink = watchInfo?.tmdbLink
                            )
                        },
                        colors = CardDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                        border = CardDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface))
                        ),
                        modifier = Modifier.height(64.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (provider.logoUri.isNotBlank()) {
                                AsyncImage(
                                    model = provider.logoUri,
                                    contentDescription = provider.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.DarkGray)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = provider.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val badge = when (provider.type) {
                                        WatchOptionType.Stream -> stringResource(R.string.stream_subscription)
                                        WatchOptionType.Rent -> stringResource(R.string.rent)
                                        WatchOptionType.Buy -> stringResource(R.string.buy)
                                        WatchOptionType.Free -> stringResource(R.string.free_with_ads)
                                    }
                                    Text(
                                        text = "$badge • ${if (isInstalled) "Installed" else "App / Store"}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = if (isInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    if (isInstalled) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // YouTube Trailer Button
                Card(
                    onClick = {
                        PlayerResolver.launchYouTubeTrailer(context, movieDetails.name)
                    },
                    colors = CardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        focusedContainerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                    border = CardDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface))
                    ),
                    modifier = Modifier.height(64.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF0000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "YouTube Trailer",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Official Clip / Teaser",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        } else {
            // Fallback when no JustWatch flatrate providers are returned
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayInMetFlix,
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Play in MetFlix ExoPlayer")
                }

                Button(
                    onClick = { PlayerResolver.launchYouTubeTrailer(context, movieDetails.name) },
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Watch Trailer on YouTube")
                }
            }
        }
    }
}
