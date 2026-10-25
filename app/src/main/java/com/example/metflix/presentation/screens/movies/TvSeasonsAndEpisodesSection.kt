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

package com.example.metflix.presentation.screens.movies

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.size
import androidx.tv.material3.Icon
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.example.metflix.data.settings.DownloadPreferences
import com.example.metflix.data.util.MediaDownloader
import com.example.metflix.data.util.MediaDownloaderProvider
import com.example.metflix.presentation.common.DownloadStreamOption
import com.example.metflix.presentation.common.MediaDownloadDialog
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.metflix.data.downloads.DownloadTracker
import com.example.metflix.data.downloads.DownloadedCatalogRepository
import com.example.metflix.data.entities.TvEpisode
import com.example.metflix.data.entities.TvSeason
import com.example.metflix.data.util.DownloadStreamResolver
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSeasonsAndEpisodesSection(
    seasons: List<TvSeason>,
    selectedSeasonNumber: Int,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (seasonNumber: Int, episodeNumber: Int) -> Unit,
    onPlayOffline: (sessionId: String) -> Unit = {},
    seriesTitle: String = "",
    seriesTmdbId: String = "",
    seriesImdbId: String? = null,
    releaseYear: String = "",
    modifier: Modifier = Modifier
) {
    if (seasons.isEmpty()) return

    val childPadding = rememberChildPadding()
    val activeSeason = seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber } ?: seasons.first()
    val episodes = activeSeason.episodes

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .padding(vertical = 16.dp)
    ) {
        // Section Header
        Text(
            text = "Episodes",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = childPadding.start, bottom = 12.dp)
        )

        // Season Selector Tabs (if multi-season)
        if (seasons.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(start = childPadding.start, end = childPadding.end),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .focusRestorer()
            ) {
                items(seasons, key = { it.seasonNumber }) { season ->
                    val isSelected = season.seasonNumber == selectedSeasonNumber
                    Card(
                        onClick = { onSeasonSelected(season.seasonNumber) },
                        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
                        colors = CardDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = CardDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface))
                        )
                    ) {
                        Text(
                            text = season.name.ifBlank { "Season ${season.seasonNumber}" },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Episodes Horizontal Carousel
        var selectedDownloadEpisode by remember { mutableStateOf<TvEpisode?>(null) }
        val context = LocalContext.current
        val mediaDownloader = remember(context) { MediaDownloaderProvider.get(context) }

        if (episodes.isEmpty()) {
            Text(
                text = "Loading episodes for ${activeSeason.name.ifBlank { "Season $selectedSeasonNumber" }}...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ),
                modifier = Modifier.padding(start = childPadding.start, top = 8.dp, bottom = 16.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(start = childPadding.start, end = childPadding.end),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer()
            ) {
                items(episodes, key = { "${it.seasonNumber}_${it.episodeNumber}_${it.id}" }) { episode ->
                    val downloadedItem = remember(episode.id, episode.seasonNumber, episode.episodeNumber, seriesTitle) {
                        DownloadedCatalogRepository.instance?.findDownloaded(episode.id, episode.seasonNumber, episode.episodeNumber)
                            ?: DownloadedCatalogRepository.instance?.findDownloaded(seriesTitle, episode.seasonNumber, episode.episodeNumber)
                    }
                    val isEpDownloaded = downloadedItem != null
                    TvEpisodeCard(
                        episode = episode,
                        isDownloaded = isEpDownloaded,
                        onClick = {
                            if (downloadedItem != null) {
                                onPlayOffline(downloadedItem.id)
                            } else {
                                onEpisodeClick(episode.seasonNumber, episode.episodeNumber)
                            }
                        },
                        onDownloadClick = { selectedDownloadEpisode = episode }
                    )
                }
            }
        }

        selectedDownloadEpisode?.let { ep ->
            val displayTitle = if (seriesTitle.isNotBlank()) {
                "$seriesTitle S${ep.seasonNumber}E${ep.episodeNumber}: ${ep.name}"
            } else {
                "S${ep.seasonNumber} E${ep.episodeNumber}: ${ep.name}"
            }
            MediaDownloadDialog(
                title = displayTitle,
                posterUrl = ep.stillUri,
                resolveStreams = {
                    DownloadStreamResolver.resolveActualStreams(
                        context = context,
                        title = seriesTitle.ifBlank { ep.name },
                        availableLanguages = listOf("Hindi", "English"),
                        tmdbId = seriesTmdbId,
                        imdbId = seriesImdbId,
                        releaseYear = releaseYear,
                        isTvShow = true,
                        season = ep.seasonNumber,
                        episode = ep.episodeNumber
                    )
                },
                onDismiss = { selectedDownloadEpisode = null },
                onStartDownload = { option, langs ->
                    val epJson = org.json.JSONObject().apply {
                        put("title", displayTitle)
                        put("seriesTitle", seriesTitle)
                        put("seriesTmdbId", seriesTmdbId)
                        put("seasonNumber", ep.seasonNumber)
                        put("episodeNumber", ep.episodeNumber)
                        put("poster", ep.stillUri.orEmpty())
                        put("overview", ep.overview.orEmpty())
                        put("type", "series")
                    }.toString()

                    mediaDownloader.enqueueDownload(
                        catalogTitle = displayTitle,
                        directUrl = option.directUrl,
                        selectedQuality = option.quality,
                        selectedLanguages = langs,
                        mediaId = ep.id.ifBlank { "${seriesTmdbId.ifBlank { seriesTitle }}_s${ep.seasonNumber}e${ep.episodeNumber}" },
                        mediaType = "SERIES",
                        seasonNumber = ep.seasonNumber,
                        episodeNumber = ep.episodeNumber,
                        posterUrl = ep.stillUri ?: "",
                        catalogJson = epJson
                    )
                }
            )
        }
    }
}

@Composable
private fun TvEpisodeCard(
    episode: TvEpisode,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = modifier.width(260.dp)
    ) {
        Column {
            Card(
                onClick = onClick,
                shape = CardDefaults.shape(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)),
                colors = CardDefaults.colors(
                    containerColor = Color(0xFF1C1C1E),
                    focusedContainerColor = Color(0xFF2C2C2E)
                ),
                border = CardDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(146.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!episode.stillUri.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(episode.stillUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = episode.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    val isDownloaded = remember(episode.id, episode.seasonNumber, episode.episodeNumber) {
                        DownloadedCatalogRepository.instance?.isDownloaded(
                            mediaId = episode.id,
                            seasonNumber = episode.seasonNumber,
                            episodeNumber = episode.episodeNumber
                        ) ?: false
                    }

                    if (isDownloaded) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(Color(0xFF2E7D32), RoundedCornerShape(20.dp))
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = "Downloaded",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Episode tag & runtime pill
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EP ${episode.episodeNumber}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        if (episode.duration.isNotBlank()) {
                            Text(
                                text = "• ${episode.duration}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.LightGray
                                )
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = "${episode.episodeNumber}. ${episode.name}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.overview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = episode.overview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Live Episode Download Progress Bar
                val epKey = "${episode.seasonNumber}_${episode.episodeNumber}_${episode.id}"
                val context = LocalContext.current
                val downloadTracker = remember { DownloadTracker.instance ?: DownloadTracker(context) }
                val activeDownloads by downloadTracker.activeDownloads.collectAsStateWithLifecycle()
                val epActiveState = remember(activeDownloads, epKey, episode.name) {
                    downloadTracker.findActiveDownload(
                        epKey,
                        "S${episode.seasonNumber}E${episode.episodeNumber}",
                        "s${episode.seasonNumber}e${episode.episodeNumber}",
                        episode.name
                    )
                }

                DisposableEffect(epKey) {
                    downloadTracker.registerObserver(epKey)
                    onDispose { downloadTracker.unregisterObserver(epKey) }
                }

                if (epActiveState != null) {
                    Spacer(Modifier.height(4.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${epActiveState.progressPercent}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = epActiveState.formattedSpeed,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.DarkGray, RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .fillMaxWidth(fraction = (epActiveState.progressPercent / 100f).coerceIn(0.01f, 1f))
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }

                val effectiveDownloaded = isDownloaded || remember(episode.id, episode.seasonNumber, episode.episodeNumber) {
                    DownloadedCatalogRepository.instance?.isDownloaded(
                        mediaId = episode.id,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber
                    ) ?: false
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (effectiveDownloaded) "Play Offline" else "Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!effectiveDownloaded) {
                        Button(
                            onClick = onDownloadClick,
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF2C2C35)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Save", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
