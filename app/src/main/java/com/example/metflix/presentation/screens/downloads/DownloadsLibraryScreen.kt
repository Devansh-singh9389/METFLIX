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

package com.example.metflix.presentation.screens.downloads

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.metflix.data.downloads.DownloadedCatalogRepository
import com.example.metflix.data.downloads.DownloadedItem
import com.example.metflix.data.downloads.DownloadedMediaGroup
import com.example.metflix.data.downloads.DownloadStatus
import com.example.metflix.data.downloads.DownloadTracker
import com.example.metflix.data.downloads.MediaOptimizer
import com.example.metflix.data.downloads.formatFileSize
import com.example.metflix.data.downloads.groupDownloadedItems
import com.example.metflix.data.settings.DownloadPreferences
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import com.example.metflix.presentation.theme.MetFlixCardShape
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DownloadsLibraryScreen(
    onOpenCatalog: (item: DownloadedItem) -> Unit = {},
    onPlayOffline: (localPath: String, title: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val repository = remember { DownloadedCatalogRepository.instance ?: DownloadedCatalogRepository(context) }
    val downloadedItems by repository.downloadedItems.collectAsStateWithLifecycle()
    val downloadTracker = remember { DownloadTracker.instance ?: DownloadTracker(context) }
    val activeDownloadsMap by downloadTracker.activeDownloads.collectAsStateWithLifecycle()
    val activeDownloads = activeDownloadsMap.values
        .distinctBy { it.mediaId }
        .filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.FAILED }

    val downloadPreferences = remember { DownloadPreferences.from(context) }
    val downloadLocation by downloadPreferences.downloadLocation.collectAsStateWithLifecycle()

    val childPadding = rememberChildPadding()
    val coroutineScope = rememberCoroutineScope()
    var optimizingId by remember { mutableStateOf<String?>(null) }
    var selectedShowTitle by remember { mutableStateOf<String?>(null) }

    // Auto-discover and sync local downloads across all storage drives
    LaunchedEffect(downloadLocation) {
        repository.scanAllStorageLocations(coroutineScope)
    }

    DisposableEffect("DownloadsLibraryScreen") {
        downloadTracker.registerObserver("DownloadsLibraryScreen")
        onDispose { downloadTracker.unregisterObserver("DownloadsLibraryScreen") }
    }

    val mediaGroups = remember(downloadedItems) { groupDownloadedItems(downloadedItems) }
    val selectedShow = remember(mediaGroups, selectedShowTitle) {
        mediaGroups.filterIsInstance<DownloadedMediaGroup.SeriesShow>().firstOrNull { it.seriesTitle == selectedShowTitle }
    }

    // Handle drill-down state: When a show is opened, show its episodes view
    if (selectedShow != null) {
        DownloadedShowDetailsView(
            show = selectedShow,
            onBack = { selectedShowTitle = null },
            optimizingId = optimizingId,
            onOpenCatalog = onOpenCatalog,
            onPlayOffline = { item ->
                playDownloadedItem(context, item, onPlayOffline)
            },
            onOptimize = { item ->
                startOptimize(context, item, coroutineScope, onOptimizingState = { optimizingId = it })
            },
            onDeleteEpisode = { item ->
                deleteDownloadedItem(context, item, repository)
            },
            onDeleteAllEpisodes = { showToDelete ->
                showToDelete.episodes.forEach { ep ->
                    deleteDownloadedItem(context, ep, repository)
                }
                selectedShowTitle = null
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = childPadding.start, vertical = childPadding.top)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DownloadDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "Downloads Library (${mediaGroups.size + activeDownloads.size})",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Offline downloads across all storage drives. Click on a series to view episodes, or click Play to watch.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Active Downloads Section
        if (activeDownloads.isNotEmpty()) {
            Text(
                text = "Active Downloads (${activeDownloads.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                activeDownloads.forEach { active ->
                    IdmDownloadCard(
                        active = active,
                        onPause = { downloadTracker.pauseDownloadByMediaId(active.mediaId) },
                        onResume = { downloadTracker.resumeDownloadByMediaId(active.mediaId) },
                        onRetry = { downloadTracker.resumeDownloadByMediaId(active.mediaId) },
                        onCancel = { downloadTracker.cancelDownloadByMediaId(active.mediaId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        if (mediaGroups.isEmpty() && activeDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "No downloaded media found.",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.LightGray
                    )
                    Text(
                        text = "Download content from Movies, Shows, or Anime to watch offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            // Netflix-Style Grouped Downloads Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 64.dp)
            ) {
                items(mediaGroups, key = { it.id }) { group ->
                    when (group) {
                        is DownloadedMediaGroup.SeriesShow -> {
                            NetflixSeriesCard(
                                show = group,
                                onClick = { selectedShowTitle = group.seriesTitle }
                            )
                        }
                        is DownloadedMediaGroup.StandaloneMovie -> {
                            DownloadedMovieCard(
                                item = group.item,
                                isOptimizing = optimizingId == group.item.id,
                                onOpenCatalog = { onOpenCatalog(group.item) },
                                onPlayOffline = {
                                    playDownloadedItem(context, group.item, onPlayOffline)
                                },
                                onOptimize = {
                                    startOptimize(context, group.item, coroutineScope, onOptimizingState = { optimizingId = it })
                                },
                                onDelete = {
                                    deleteDownloadedItem(context, group.item, repository)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Netflix-Style Show Card for Series / Anime.
 * Displays total episodes, seasons, total size, and clicks into episode list.
 */
@Composable
private fun NetflixSeriesCard(
    show: DownloadedMediaGroup.SeriesShow,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(MetFlixCardShape),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 85.dp, height = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                if (show.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(show.posterUrl)
                            .crossfade(false)
                            .build(),
                        contentDescription = show.seriesTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = show.seriesTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${show.seasonsCount} Season${if (show.seasonsCount > 1) "s" else ""} • ${show.episodesCount} Episodes",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatFileSize(show.totalSizeBytes),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (show.isAllOptimized) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2E7D32).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Optimized",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF81C784)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Click to view episodes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Detailed Episode List Drill-down for a Series Show.
 */
@Composable
private fun DownloadedShowDetailsView(
    show: DownloadedMediaGroup.SeriesShow,
    onBack: () -> Unit,
    optimizingId: String?,
    onOpenCatalog: (item: DownloadedItem) -> Unit,
    onPlayOffline: (item: DownloadedItem) -> Unit,
    onOptimize: (item: DownloadedItem) -> Unit,
    onDeleteEpisode: (item: DownloadedItem) -> Unit,
    onDeleteAllEpisodes: (show: DownloadedMediaGroup.SeriesShow) -> Unit
) {
    BackHandler(onBack = onBack)
    val childPadding = rememberChildPadding()

    val seasons = remember(show.episodes) {
        show.episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
    }
    var selectedSeason by remember { mutableIntStateOf(seasons.firstOrNull() ?: 1) }

    val filteredEpisodes = remember(show.episodes, selectedSeason, seasons) {
        if (seasons.size > 1) {
            show.episodes.filter { (it.seasonNumber ?: 1) == selectedSeason }
        } else {
            show.episodes
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = childPadding.start, vertical = childPadding.top)
    ) {
        // Header with Back Button and Series Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onBack,
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Downloads", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = show.seriesTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${show.episodesCount} Episodes • ${formatFileSize(show.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = { onDeleteAllEpisodes(show) },
                colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Delete All", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Season Filter Chips (if multi-season)
        if (seasons.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                items(seasons.size) { index ->
                    val sNum = seasons[index]
                    val isSelected = sNum == selectedSeason
                    Card(
                        onClick = { selectedSeason = sNum },
                        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = CardDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = "Season $sNum",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Episodes Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 64.dp)
        ) {
            items(filteredEpisodes, key = { it.id }) { ep ->
                DownloadedItemCard(
                    item = ep,
                    isOptimizing = optimizingId == ep.id,
                    onOpenCatalog = { onOpenCatalog(ep) },
                    onPlayOffline = { onPlayOffline(ep) },
                    onOptimize = { onOptimize(ep) },
                    onDelete = { onDeleteEpisode(ep) }
                )
            }
        }
    }
}

/**
 * Standalone Movie Card.
 */
@Composable
private fun DownloadedMovieCard(
    item: DownloadedItem,
    isOptimizing: Boolean = false,
    onOpenCatalog: () -> Unit,
    onPlayOffline: () -> Unit,
    onOptimize: () -> Unit,
    onDelete: () -> Unit
) {
    DownloadedItemCard(
        item = item,
        isOptimizing = isOptimizing,
        onOpenCatalog = onOpenCatalog,
        onPlayOffline = onPlayOffline,
        onOptimize = onOptimize,
        onDelete = onDelete
    )
}

/**
 * Generic Card for a downloaded item (standalone movie or single episode).
 */
@Composable
private fun DownloadedItemCard(
    item: DownloadedItem,
    isOptimizing: Boolean = false,
    onOpenCatalog: () -> Unit,
    onPlayOffline: () -> Unit,
    onOptimize: () -> Unit,
    onDelete: () -> Unit
) {
    val onlinePosterUrl = remember(item.posterUrl, item.mediaId) {
        when {
            item.posterUrl.isNotBlank() -> item.posterUrl
            item.mediaId.startsWith("http") -> item.mediaId
            item.mediaId.startsWith("/") -> "https://image.tmdb.org/t/p/w500${item.mediaId}"
            else -> null
        }
    }

    val isOptimized = remember(item.localFilePath) {
        item.localFilePath.isNotBlank() &&
                (item.localFilePath.endsWith(".mp4", ignoreCase = true) ||
                 item.localFilePath.endsWith(".mkv", ignoreCase = true)) &&
                File(item.localFilePath).exists()
    }

    val posterModel = remember(item.localFilePath, onlinePosterUrl) {
        if (item.localFilePath.isNotBlank()) {
            val localPoster = File(File(item.localFilePath).parentFile, "poster.jpg")
            if (localPoster.exists() && localPoster.length() > 0) localPoster else onlinePosterUrl
        } else {
            onlinePosterUrl
        }
    }

    Surface(
        shape = MetFlixCardShape,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = posterModel,
                contentDescription = item.title,
                modifier = Modifier
                    .size(width = 80.dp, height = 110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.quality,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isOptimized) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2E7D32).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Optimized",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF81C784)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFF57C00).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Stream Cache",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFFB74D)
                            )
                        }
                    }
                }

                if (item.languages.isNotEmpty()) {
                    Text(
                        text = item.languages.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayOffline,
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!isOptimized) {
                        Button(
                            onClick = onOptimize,
                            enabled = !isOptimizing,
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = if (isOptimizing) Color.Gray else Color(0xFFE65100)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(if (isOptimizing) "Optimizing..." else "Optimize", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = onOpenCatalog,
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Catalog", fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = onDelete,
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

private fun playDownloadedItem(
    context: android.content.Context,
    item: DownloadedItem,
    onPlayOffline: (localPath: String, title: String) -> Unit
) {
    if (item.localFilePath.isNotBlank() && File(item.localFilePath).exists()) {
        onPlayOffline(item.localFilePath, item.title)
    } else if (item.directUrl.isNotBlank()) {
        onPlayOffline(item.id, item.title)
    } else {
        Toast.makeText(context, "Local file not found at ${item.localFilePath}", Toast.LENGTH_LONG).show()
    }
}

private fun startOptimize(
    context: android.content.Context,
    item: DownloadedItem,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onOptimizingState: (String?) -> Unit
) {
    onOptimizingState(item.id)
    Toast.makeText(context, "Optimizing ${item.title}... Converting stream to standalone video", Toast.LENGTH_SHORT).show()
    coroutineScope.launch {
        MediaOptimizer.optimize(
            context = context,
            item = item,
            onProgress = { _, status ->
                Log.d("DownloadsLibraryScreen", "Optimize [${item.title}]: $status")
            }
        ).onSuccess { file ->
            onOptimizingState(null)
            Toast.makeText(context, "Optimized: ${file.name} saved! Ready for VLC", Toast.LENGTH_LONG).show()
        }.onFailure { err ->
            onOptimizingState(null)
            Log.e("DownloadsLibraryScreen", "Optimization failed: ${err.message}", err)
            Toast.makeText(context, "Optimization failed: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun deleteDownloadedItem(
    context: android.content.Context,
    item: DownloadedItem,
    repository: DownloadedCatalogRepository
) {
    try {
        if (item.localFilePath.isNotBlank()) {
            val f = File(item.localFilePath)
            if (f.exists()) {
                val parent = f.parentFile
                if (parent != null && parent.name.equals(MediaOptimizer.sanitizeFolderName(item.title), ignoreCase = true)) {
                    parent.deleteRecursively()
                } else {
                    f.delete()
                }
            }
        }
    } catch (_: Exception) {}
    repository.removeDownloadedItem(item.id)
    Toast.makeText(context, "Removed ${item.title} from downloads", Toast.LENGTH_SHORT).show()
}

@Composable
fun IdmDownloadCard(
    active: com.example.metflix.data.downloads.ActiveDownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF22222B), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF353544), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. Header: Title, Status Badge, Resume capability, Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = active.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val (statusText, statusBg, statusColor) = when (active.status) {
                            DownloadStatus.RUNNING -> Triple("Downloading (8 Parts Active)", Color(0xFF1B5E20).copy(alpha = 0.5f), Color(0xFF4CAF50))
                            DownloadStatus.PAUSED -> Triple("Paused", Color(0xFFE65100).copy(alpha = 0.35f), Color(0xFFFFB74D))
                            DownloadStatus.FAILED -> Triple("Network Interrupted", Color(0xFFB71C1C).copy(alpha = 0.35f), Color(0xFFFF5252))
                            DownloadStatus.COMPLETED -> Triple("Complete", Color(0xFF1B5E20).copy(alpha = 0.5f), Color(0xFF81C784))
                            DownloadStatus.PENDING -> Triple("Starting (Connecting)", Color(0xFF0D47A1).copy(alpha = 0.35f), Color(0xFF64B5F6))
                        }
                        Box(
                            modifier = Modifier
                                .background(statusBg, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(statusText, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                        }

                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2D303E), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("Resume: Yes", fontSize = 10.sp, color = Color(0xFFB0BEC5))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (active.status == DownloadStatus.PAUSED) {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (active.status == DownloadStatus.FAILED) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.colors(containerColor = Color(0xFFE65100))
                        ) {
                            Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onPause,
                            colors = ButtonDefaults.colors(containerColor = Color(0xFF388E3C))
                        ) {
                            Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }

            // 2. IDM Metrics Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A22), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Downloaded", fontSize = 10.sp, color = Color.Gray)
                    Text(
                        text = active.formattedProgress,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Transfer Rate", fontSize = 10.sp, color = Color.Gray)
                    Text(
                        text = if (active.status == DownloadStatus.RUNNING) active.formattedSpeed else "0 KB/s",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Time Left", fontSize = 10.sp, color = Color.Gray)
                    val etaStr = if (active.status == DownloadStatus.RUNNING) {
                        if (active.formattedEta.isNotBlank()) active.formattedEta else "Estimating..."
                    } else {
                        "-"
                    }
                    Text(
                        text = etaStr,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3. Main Overall Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0xFF30303D), RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
            ) {
                val progressFraction = (active.progressPercent / 100f).coerceIn(0.01f, 1f)
                val barColor = when (active.status) {
                    DownloadStatus.FAILED -> Color(0xFFFF5252)
                    DownloadStatus.PAUSED -> Color(0xFFFFB74D)
                    else -> Color(0xFF00E5FF)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth(fraction = progressFraction)
                        .background(barColor, RoundedCornerShape(4.dp))
                )
            }

            // 4. IDM 8-Part Parallel Multi-Segment Visualizer
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("IDM Multi-Part Streams (8 Parallel Threads)", fontSize = 10.sp, color = Color.Gray)
                    Text("${active.progressPercent}% Overall", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 0 until 8) {
                        val partProgress = active.getPartProgress(i)
                        val partPct = (partProgress * 100).toInt()
                        val isCurrentActive = partProgress > 0f && partProgress < 1f && active.status == DownloadStatus.RUNNING
                        val partBg = when {
                            partProgress >= 1f -> Color(0xFF4CAF50)
                            partProgress > 0f -> Color(0xFF00E5FF)
                            else -> Color(0xFF2C2C38)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF181820), RoundedCornerShape(4.dp))
                                .border(
                                    width = if (isCurrentActive) 1.dp else 0.5.dp,
                                    color = if (isCurrentActive) Color(0xFF00E5FF) else Color(0xFF333342),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("P${i + 1}", fontSize = 9.sp, color = if (partProgress >= 1f) Color(0xFF81C784) else Color.LightGray)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .height(4.dp)
                                        .background(Color(0xFF2C2C38), RoundedCornerShape(2.dp))
                                        .clip(RoundedCornerShape(2.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .fillMaxWidth(partProgress.coerceIn(0f, 1f))
                                            .background(partBg, RoundedCornerShape(2.dp))
                                    )
                                }
                                Text("$partPct%", fontSize = 8.sp, color = if (partProgress >= 1f) Color(0xFF81C784) else Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
