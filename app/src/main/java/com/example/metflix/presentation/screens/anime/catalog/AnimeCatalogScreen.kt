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

package com.example.metflix.presentation.screens.anime.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import com.example.metflix.data.settings.DownloadPreferences
import com.example.metflix.data.util.MediaDownloader
import com.example.metflix.data.util.MediaDownloaderProvider
import com.example.metflix.presentation.common.DownloadStreamOption
import com.example.metflix.presentation.common.MediaDownloadDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.metflix.data.downloads.DownloadStatus
import com.example.metflix.data.downloads.DownloadTracker
import com.example.metflix.data.downloads.DownloadedCatalogRepository
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.network.anime.AnimeBackendConfig
import com.example.metflix.data.network.anime.models.AnimeEpisodeItem
import com.example.metflix.data.network.anime.models.AnimeSeasonItem
import com.example.metflix.data.util.DownloadStreamResolver
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.common.MoviesRow
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import com.example.metflix.presentation.theme.MetFlixCardShape
import kotlinx.coroutines.delay

@Composable
fun AnimeCatalogScreen(
    onBackPressed: () -> Unit,
    onEpisodeClick: (episode: AnimeEpisodeItem, seriesTitle: String) -> Unit,
    onMoviePlayClick: (slug: String, title: String) -> Unit,
    onRecommendedMovieClick: (movie: Movie) -> Unit,
    onPlayOffline: (sessionId: String) -> Unit = {},
    viewModel: AnimeCatalogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBackPressed)

    when (val state = uiState) {
        is AnimeCatalogUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        is AnimeCatalogUiState.Error -> {
            AnimeCatalogError(
                message = state.message,
                fallbackTitle = state.fallbackTitle,
                fallbackPoster = state.fallbackPoster,
                onRetry = { viewModel.retry() },
                onBack = onBackPressed
            )
        }

        is AnimeCatalogUiState.Series -> {
            AnimeSeriesCatalogContent(
                state = state,
                onSeasonClick = { seasonNumber -> viewModel.onSeasonClicked(seasonNumber) },
                onEpisodeClick = { ep -> onEpisodeClick(ep, state.title) },
                onPlayOffline = onPlayOffline
            )
        }

        is AnimeCatalogUiState.MovieCatalog -> {
            AnimeMovieCatalogContent(
                state = state,
                onPlayClick = { onMoviePlayClick(state.slug, state.title) },
                onPlayOfflineClick = onPlayOffline,
                onRecommendedClick = onRecommendedMovieClick
            )
        }
    }
}

@Composable
private fun AnimeSeriesCatalogContent(
    state: AnimeCatalogUiState.Series,
    onSeasonClick: (seasonNumber: Int) -> Unit,
    onEpisodeClick: (episode: AnimeEpisodeItem) -> Unit,
    onPlayOffline: (sessionId: String) -> Unit = {}
) {
    val childPadding = rememberChildPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = childPadding.top, bottom = 64.dp)
    ) {
        // TOP SECTION: Left = Name & Details, Right = Poster Photo
        item(contentType = "series_hero") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = childPadding.start, end = childPadding.end, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top
            ) {
                // LEFT SIDE: Name, Meta tags, Synopsis
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Badges row: Languages (Hindi, Jap, etc.) and Genres
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.languages.isNotEmpty()) {
                            BadgeChip(text = state.languages.take(3).joinToString(" • "), isHighlight = true)
                        }
                        if (state.totalSeasons > 0) {
                            BadgeChip(text = "${state.totalSeasons} Season${if (state.totalSeasons > 1) "s" else ""}")
                        }
                        if (state.genres.isNotEmpty()) {
                            BadgeChip(text = state.genres.take(2).joinToString(", "))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Synopsis
                    if (state.synopsis.isNotBlank()) {
                        Text(
                            text = state.synopsis,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // DEBUGGING INFO
                    CatalogDebugCard(
                        mediaType = "Series",
                        slug = state.slug,
                        title = state.title,
                        extraInfo = "Seasons: ${state.totalSeasons} | Active: S${state.selectedSeasonNumber} (${state.currentEpisodes.size} eps)"
                    )
                }

                // RIGHT SIDE: Photo of the poster
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .aspectRatio(2f / 3f)
                        .clip(MetFlixCardShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    if (state.poster.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(state.poster)
                                .crossfade(true)
                                .build(),
                            contentDescription = state.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // BOTTOM SECTION: Season Selector & Episodes
        item(contentType = "episodes_section") {
            SeriesEpisodesSection(
                seasons = state.seasons,
                selectedSeasonNumber = state.selectedSeasonNumber,
                episodes = state.currentEpisodes,
                onSeasonClick = onSeasonClick,
                onEpisodeClick = onEpisodeClick,
                onPlayOffline = onPlayOffline,
                seriesTitle = state.title,
                releaseYear = "",
                languages = state.languages,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeriesEpisodesSection(
    seasons: List<AnimeSeasonItem>,
    selectedSeasonNumber: Int,
    episodes: List<AnimeEpisodeItem>,
    onSeasonClick: (Int) -> Unit,
    onEpisodeClick: (AnimeEpisodeItem) -> Unit,
    onPlayOffline: (sessionId: String) -> Unit = {},
    seriesTitle: String = "",
    releaseYear: String = "",
    languages: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val childPadding = rememberChildPadding()

    Column(
        modifier = modifier
            .focusGroup()
            .padding(top = 8.dp)
    ) {
        Text(
            text = "Episodes",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = childPadding.start, bottom = 12.dp)
        )

        // Season Selector Tabs: ONLY fire request / change on user CLICK, never on focus move!
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
                        onClick = { onSeasonClick(season.seasonNumber) },
                        shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = CardDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = CardDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface))
                        )
                    ) {
                        Text(
                            text = season.seasonName ?: "Season ${season.seasonNumber}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Horizontal Episode Cards Carousel
        var selectedDownloadEp by remember { mutableStateOf<AnimeEpisodeItem?>(null) }
        val context = LocalContext.current
        val mediaDownloader = remember(context) { MediaDownloaderProvider.get(context) }

        if (episodes.isEmpty()) {
            Text(
                text = "No episodes listed for this season.",
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
                items(episodes, key = { it.route.ifEmpty { it.episodeCode.orEmpty() } }) { ep ->
                    val epSlug = ep.route.removePrefix("/episode/").removePrefix("/").ifEmpty { ep.episodeCode.orEmpty() }
                    val epNum = ep.episodeCode?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                    val epTitle = if (seriesTitle.isNotBlank()) {
                        "$seriesTitle - ${ep.title ?: "Episode ${ep.episodeCode ?: epNum}"}"
                    } else {
                        ep.title ?: ep.episodeCode ?: "Anime Episode"
                    }
                    val downloadedItem = remember(epSlug, epTitle, selectedSeasonNumber, epNum) {
                        DownloadedCatalogRepository.instance?.findDownloaded(epSlug, selectedSeasonNumber, epNum)
                            ?: DownloadedCatalogRepository.instance?.findDownloaded(epTitle, selectedSeasonNumber, epNum)
                            ?: DownloadedCatalogRepository.instance?.findDownloaded(epSlug)
                            ?: DownloadedCatalogRepository.instance?.findDownloaded(epTitle)
                    }

                    EpisodeCard(
                        episode = ep,
                        isDownloaded = downloadedItem != null,
                        onClick = {
                            if (downloadedItem != null) {
                                onPlayOffline(downloadedItem.id)
                            } else {
                                onEpisodeClick(ep)
                            }
                        },
                        onDownloadClick = { selectedDownloadEp = ep }
                    )
                }
            }
        }

        selectedDownloadEp?.let { ep ->
            val epNum = ep.episodeCode?.filter { it.isDigit() }?.toIntOrNull() ?: 1
            val epTitle = if (seriesTitle.isNotBlank()) {
                "$seriesTitle - ${ep.title ?: "Episode ${ep.episodeCode ?: epNum}"}"
            } else {
                ep.title ?: ep.episodeCode ?: "Anime Episode"
            }
            val epSlug = ep.route.removePrefix("/episode/").removePrefix("/").ifEmpty { ep.episodeCode.orEmpty() }
            MediaDownloadDialog(
                title = epTitle,
                posterUrl = ep.thumbnail,
                resolveStreams = {
                    DownloadStreamResolver.resolveActualStreams(
                        context = context,
                        title = seriesTitle.ifBlank { epTitle },
                        availableLanguages = languages.ifEmpty { listOf("Hindi", "Japanese", "English") },
                        isAnime = true,
                        season = selectedSeasonNumber,
                        episode = epNum,
                        releaseYear = releaseYear,
                        animeSlug = epSlug
                    )
                },
                onDismiss = { selectedDownloadEp = null },
                onStartDownload = { option, langs ->
                    val epJson = org.json.JSONObject().apply {
                        put("title", epTitle)
                        put("slug", epSlug)
                        put("seriesTitle", seriesTitle)
                        put("seasonNumber", selectedSeasonNumber)
                        put("episodeNumber", epNum)
                        put("thumbnail", ep.thumbnail.orEmpty())
                        
                        put("type", "series")
                    }.toString()

                    mediaDownloader.enqueueDownload(
                        catalogTitle = epTitle,
                        directUrl = option.directUrl,
                        selectedQuality = option.quality,
                        selectedLanguages = langs,
                        mediaId = epSlug.ifBlank { epTitle },
                        mediaType = "ANIME_EPISODE",
                        seasonNumber = selectedSeasonNumber,
                        episodeNumber = epNum,
                        posterUrl = ep.thumbnail.orEmpty(),
                        catalogJson = epJson
                    )
                }
            )
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: AnimeEpisodeItem,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = modifier.width(220.dp)
    ) {
        Column {
            Card(
                onClick = onClick,
                shape = CardDefaults.shape(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)),
                colors = CardDefaults.colors(
                    containerColor = Color.DarkGray,
                    focusedContainerColor = Color(0xFF3C3C3E)
                ),
                border = CardDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!episode.thumbnail.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(episode.thumbnail)
                                .size(320, 180)
                                .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                                .allowRgb565(true)
                                .precision(coil.size.Precision.INEXACT)
                                .crossfade(false)
                                .build(),
                            contentDescription = episode.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Overlay gradient and episode code
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                    startY = 60f
                                )
                            )
                    )

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

                    if (!episode.episodeCode.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = episode.episodeCode,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Episode Title
            Text(
                text = episode.title ?: episode.episodeCode ?: "Episode",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )

            // Live Episode Download Progress Bar
            val epKey = episode.title ?: episode.episodeCode ?: episode.route
            val context = LocalContext.current
            val downloadTracker = remember { DownloadTracker.instance ?: DownloadTracker(context) }
            val activeDownloads by downloadTracker.activeDownloads.collectAsStateWithLifecycle()
            val epActiveState = remember(activeDownloads, epKey, episode.title, episode.episodeCode) {
                downloadTracker.findActiveDownload(epKey, episode.title, episode.episodeCode, episode.route)
            }

            DisposableEffect(epKey) {
                downloadTracker.registerObserver(epKey)
                onDispose { downloadTracker.unregisterObserver(epKey) }
            }

            if (epActiveState != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
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
                    Spacer(modifier = Modifier.height(2.dp))
                    LiveProgressBar(
                        progress = epActiveState.progressPercent / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }

            val effectiveDownloaded = isDownloaded || remember(episode.route, episode.episodeCode, episode.title) {
                val key = episode.route.removePrefix("/episode/").removePrefix("/").ifEmpty { episode.episodeCode.orEmpty() }
                DownloadedCatalogRepository.instance?.findDownloaded(key) != null
                    || DownloadedCatalogRepository.instance?.findDownloaded(episode.title ?: "") != null
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(if (effectiveDownloaded) "Play Offline" else "Play", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("Save", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.DarkGray
) {
    Box(
        modifier = modifier
            .background(trackColor, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0.01f, 1f))
                .background(color, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun AnimeMovieCatalogContent(
    state: AnimeCatalogUiState.MovieCatalog,
    onPlayClick: () -> Unit,
    onPlayOfflineClick: (sessionId: String) -> Unit,
    onRecommendedClick: (Movie) -> Unit
) {
    val childPadding = rememberChildPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = childPadding.top, bottom = 64.dp)
    ) {
        // TOP SECTION: Left = Title & Details, Right = Poster Photo (same layout as series, no seasons)
        item(contentType = "movie_hero") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = childPadding.start, end = childPadding.end, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top
            ) {
                // LEFT SIDE: Name, Meta tags, Synopsis, Play Button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BadgeChip(text = "Movie", isHighlight = true)
                        if (state.duration.isNotBlank()) {
                            BadgeChip(text = state.duration)
                        }
                        if (state.releaseYear.isNotBlank()) {
                            BadgeChip(text = state.releaseYear)
                        }
                        if (state.languages.isNotEmpty()) {
                            BadgeChip(text = state.languages.take(3).joinToString(" • "))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (state.synopsis.isNotBlank()) {
                        Text(
                            text = state.synopsis,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // DEBUGGING INFO
                    CatalogDebugCard(
                        mediaType = "Movie",
                        slug = state.slug,
                        title = state.title,
                        extraInfo = "Duration: ${state.duration.ifBlank { "N/A" }} | Year: ${state.releaseYear.ifBlank { "N/A" }} | Recs: ${state.recommendations.size}"
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val context = LocalContext.current
                    var showDownloadDialog by remember { mutableStateOf(false) }
                    val mediaDownloader = remember(context) { MediaDownloaderProvider.get(context) }
                    val downloadedItem = remember(state.slug, state.title) {
                        DownloadedCatalogRepository.instance?.findDownloaded(state.slug)
                            ?: DownloadedCatalogRepository.instance?.findDownloaded(state.title)
                    }
                    val isDownloaded = downloadedItem != null

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isDownloaded) {
                            Button(
                                onClick = { onPlayOfflineClick(downloadedItem!!.id) },
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                    Text(text = "Play Offline", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Button(
                                onClick = onPlayClick,
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                    Text(text = "Watch Online", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        } else {
                            Button(
                                onClick = onPlayClick,
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                    Text(text = "Watch Movie", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Button(
                                onClick = { showDownloadDialog = true },
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                                    Text(text = "Download Movie", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    if (showDownloadDialog) {
                        MediaDownloadDialog(
                            title = state.title,
                            posterUrl = state.poster,
                            resolveStreams = {
                                DownloadStreamResolver.resolveActualStreams(
                                    context = context,
                                    title = state.title,
                                    availableLanguages = state.languages.ifEmpty { listOf("Hindi", "Japanese", "English") },
                                    isAnime = true,
                                    isTvShow = false,
                                    releaseYear = state.releaseYear,
                                    animeSlug = state.slug
                                )
                            },
                            onDismiss = { showDownloadDialog = false },
                            onStartDownload = { option, langs ->
                                val movieJson = org.json.JSONObject().apply {
                                    put("title", state.title)
                                    put("slug", state.slug)
                                    put("poster", state.poster.orEmpty())
                                    put("genres", org.json.JSONArray(state.genres))
                                    put("synopsis", state.synopsis)
                                    
                                    put("releaseYear", state.releaseYear)
                                    put("type", "movie")
                                }.toString()

                                mediaDownloader.enqueueDownload(
                                    catalogTitle = state.title,
                                    directUrl = option.directUrl,
                                    selectedQuality = option.quality,
                                    selectedLanguages = langs,
                                    mediaId = state.slug.ifBlank { state.title },
                                    mediaType = "ANIME_MOVIE",
                                    posterUrl = state.poster.orEmpty(),
                                    catalogJson = movieJson
                                )
                            }
                        )
                    }

                    // Live Download Progress Card for Anime Movie
                    val downloadTracker = remember { DownloadTracker.instance ?: DownloadTracker(context) }
                    val activeDownloads by downloadTracker.activeDownloads.collectAsStateWithLifecycle()
                    val activeState = remember(activeDownloads, state.title, state.slug) {
                        downloadTracker.findActiveDownload(state.title, state.slug)
                    }

                    DisposableEffect(state.title) {
                        downloadTracker.registerObserver(state.title)
                        onDispose { downloadTracker.unregisterObserver(state.title) }
                    }

                    if (activeState != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2C2C35), RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Downloading: ${activeState.formattedProgress}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${activeState.formattedSpeed} • ${activeState.formattedEta}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray
                                    )
                                }

                                LiveProgressBar(
                                    progress = activeState.progressPercent / 100f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (activeState.status == DownloadStatus.PAUSED) {
                                        Button(
                                            onClick = { downloadTracker.resumeDownload(activeState.downloadId) },
                                            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text("Resume", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { downloadTracker.pauseDownload(activeState.downloadId) },
                                            colors = ButtonDefaults.colors(containerColor = Color(0xFF388E3C))
                                        ) {
                                            Text("Pause", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Button(
                                        onClick = { downloadTracker.cancelDownload(activeState.downloadId) },
                                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // RIGHT SIDE: Photo of the poster
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .aspectRatio(2f / 3f)
                        .clip(MetFlixCardShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    if (state.poster.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(state.poster)
                                .crossfade(true)
                                .build(),
                            contentDescription = state.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // BOTTOM SECTION: Recommended Movies / Suggestions (as requested by user)
        if (state.recommendations.isNotEmpty()) {
            item(contentType = "recommendations") {
                MoviesRow(
                    title = "🍿 You May Also Like",
                    movieList = state.recommendations,
                    onMovieSelected = onRecommendedClick,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String, isHighlight: Boolean = false) {
    Box(
        modifier = Modifier
            .background(
                color = if (isHighlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AnimeCatalogError(
    message: String,
    fallbackTitle: String,
    fallbackPoster: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val childPadding = rememberChildPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = childPadding.start),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "⚠️", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (fallbackTitle.isNotBlank()) fallbackTitle else "Content Unavailable",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Could not fetch streaming catalog: $message",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "⚙️ DEBUG: Target Base URL: ${AnimeBackendConfig.BASE_URL}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFFF80AB)
                    )
                    Text(
                        text = "Error detail: $message",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onRetry) {
                    Text(text = "Retry")
                }
                Button(onClick = onBack) {
                    Text(text = "Go Back")
                }
            }
        }
    }
}

@Composable
private fun CatalogDebugCard(
    mediaType: String,
    slug: String,
    title: String,
    extraInfo: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚙️ METFLIX ANIME DEBUG",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = "BASE: ${AnimeBackendConfig.BASE_URL}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF69F0AE)
                )
            }
            Text(
                text = "Target API: ${AnimeBackendConfig.BASE_URL}api/${mediaType.lowercase()}/$slug",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color.White
            )
            Text(
                text = "Title: $title | Type: $mediaType | $extraInfo",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFFFFD54F)
            )
        }
    }
}
