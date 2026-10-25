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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.example.metflix.data.downloads.DownloadStatus
import com.example.metflix.data.downloads.DownloadTracker
import com.example.metflix.data.downloads.DownloadedCatalogRepository
import com.example.metflix.data.downloads.DownloadedItem
import com.example.metflix.data.downloads.MediaOptimizer
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
    val repository = remember { DownloadedCatalogRepository(context) }
    val downloadedItems by repository.downloadedItems.collectAsStateWithLifecycle()
    val downloadTracker = remember { DownloadTracker.instance ?: DownloadTracker(context) }
    val activeDownloadsMap by downloadTracker.activeDownloads.collectAsStateWithLifecycle()
    val activeDownloads = activeDownloadsMap.values
        .distinctBy { it.mediaId }
        .filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING }
    val childPadding = rememberChildPadding()
    val coroutineScope = rememberCoroutineScope()
    var optimizingId by remember { mutableStateOf<String?>(null) }

    DisposableEffect("DownloadsLibraryScreen") {
        downloadTracker.registerObserver("DownloadsLibraryScreen")
        onDispose { downloadTracker.unregisterObserver("DownloadsLibraryScreen") }
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
                text = "Downloads Library (${downloadedItems.size + activeDownloads.size})",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Offline catalog files & live active downloads. Click 'Catalog' to view details or 'Play' to watch.",
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

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                activeDownloads.forEach { active ->
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
                                    text = active.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "${active.formattedSpeed} • ${active.formattedEta}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .background(Color.DarkGray, RoundedCornerShape(3.dp))
                                    .clip(RoundedCornerShape(3.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .fillMaxWidth(fraction = (active.progressPercent / 100f).coerceIn(0.01f, 1f))
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = active.formattedProgress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (active.status == DownloadStatus.PAUSED) {
                                        Button(
                                            onClick = { downloadTracker.resumeDownloadByMediaId(active.mediaId) },
                                            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text("Resume", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { downloadTracker.pauseDownloadByMediaId(active.mediaId) },
                                            colors = ButtonDefaults.colors(containerColor = Color(0xFF388E3C))
                                        ) {
                                            Text("Pause", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Button(
                                        onClick = { downloadTracker.cancelDownloadByMediaId(active.mediaId) },
                                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        if (downloadedItems.isEmpty() && activeDownloads.isEmpty()) {
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
                        text = "No downloaded media yet.",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.LightGray
                    )
                    Text(
                        text = "Go to Catalog or Movies and click 'Download' to save content offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(downloadedItems.size, key = { downloadedItems[it].id }) { index ->
                    val item = downloadedItems[index]
                    DownloadedItemCard(
                        item = item,
                        isOptimizing = optimizingId == item.id,
                        onOpenCatalog = { onOpenCatalog(item) },
                        onPlayOffline = {
                            if (item.localFilePath.isNotBlank() && File(item.localFilePath).exists()) {
                                onPlayOffline(item.localFilePath, item.title)
                            } else if (item.directUrl.isNotBlank()) {
                                onPlayOffline(item.id, item.title)
                            } else {
                                Toast.makeText(context, "Local file not found at ${item.localFilePath}", Toast.LENGTH_LONG).show()
                            }
                        },
                        onOptimize = {
                            if (optimizingId == null) {
                                optimizingId = item.id
                                Toast.makeText(context, "Optimizing ${item.title}... Converting stream to standalone video", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    MediaOptimizer.optimize(
                                        context = context,
                                        item = item,
                                        onProgress = { _, status ->
                                            Log.d("DownloadsLibraryScreen", "Optimize [${item.title}]: $status")
                                        }
                                    ).onSuccess { file ->
                                        optimizingId = null
                                        Toast.makeText(context, "Optimized: ${file.name} saved! Ready for VLC", Toast.LENGTH_LONG).show()
                                    }.onFailure { err ->
                                        optimizingId = null
                                        Log.e("DownloadsLibraryScreen", "Optimization failed: ${err.message}", err)
                                        Toast.makeText(context, "Optimization failed: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onDelete = {
                            try {
                                if (item.localFilePath.isNotBlank()) {
                                    val f = File(item.localFilePath)
                                    if (f.exists()) {
                                        val parent = f.parentFile
                                        // If stored in dedicated folder, delete folder
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
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedItemCard(
    item: DownloadedItem,
    isOptimizing: Boolean = false,
    onOpenCatalog: () -> Unit,
    onPlayOffline: () -> Unit,
    onOptimize: () -> Unit,
    onDelete: () -> Unit
) {
    // Auto-fetch poster online via internet using posterUrl/mediaId/slug
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
                 item.localFilePath.endsWith(".mkv", ignoreCase = true) ||
                 item.localFilePath.endsWith(".ts", ignoreCase = true)) &&
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
                                text = "Standalone Video",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF66BB6A)
                            )
                        }
                    }

                    if (item.languages.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.languages.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayOffline,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onOpenCatalog,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF2C2C35)
                        )
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Catalog", fontSize = 11.sp)
                    }

                    // Optimize button appears ONLY when not yet optimized
                    if (!isOptimized) {
                        Button(
                            onClick = onOptimize,
                            enabled = !isOptimizing,
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF6200EA),
                                focusedContainerColor = Color(0xFF7C4DFF)
                            )
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isOptimizing) "Optimizing..." else "Optimize", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
