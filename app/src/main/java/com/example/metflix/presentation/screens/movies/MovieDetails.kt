/*
 * Copyright 2023 Google LLC
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

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.metflix.data.settings.DownloadPreferences
import com.example.metflix.data.util.MediaDownloader
import com.example.metflix.presentation.common.DownloadStreamOption
import com.example.metflix.presentation.common.MediaDownloadDialog
import com.example.metflix.presentation.utils.PlayerResolver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.metflix.R
import com.example.metflix.data.downloads.DownloadStatus
import com.example.metflix.data.downloads.DownloadTracker
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.data.util.DownloadStreamResolver
import com.example.metflix.data.util.StringConstants
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import com.example.metflix.presentation.theme.MetFlixButtonShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieDetails(
    movieDetails: MovieDetails,
    goToMoviePlayer: () -> Unit
) {
    val childPadding = rememberChildPadding()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(432.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        MovieImageWithGradients(
            movieDetails = movieDetails,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxWidth(0.55f)) {
            Spacer(modifier = Modifier.height(108.dp))
            Column(
                modifier = Modifier.padding(start = childPadding.start)
            ) {
                MovieLargeTitle(movieTitle = movieDetails.name)

                Column(
                    modifier = Modifier.alpha(0.75f)
                ) {
                    MovieDescription(description = movieDetails.description)
                    DotSeparatedRow(
                        modifier = Modifier.padding(top = 20.dp),
                        texts = listOf(
                            movieDetails.pgRating,
                            movieDetails.releaseDate,
                            movieDetails.categories.joinToString(", "),
                            movieDetails.duration
                        )
                    )
                    DirectorScreenplayMusicRow(
                        director = movieDetails.director,
                        screenplay = movieDetails.screenplay,
                        music = movieDetails.music
                    )
                }
                PlaybackActionButtonsRow(
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) {
                            coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    },
                    movieTitle = movieDetails.name,
                    isTvShow = movieDetails.isTvShow,
                    posterUri = movieDetails.posterUri,
                    goToMoviePlayer = goToMoviePlayer
                )
            }
        }
    }
}

@Composable
private fun PlaybackActionButtonsRow(
    movieTitle: String,
    isTvShow: Boolean = false,
    posterUri: String? = null,
    modifier: Modifier = Modifier,
    goToMoviePlayer: () -> Unit
) {
    val context = LocalContext.current
    var showDownloadDialog by remember { mutableStateOf(false) }
    val mediaDownloader = remember { MediaDownloader(context, DownloadPreferences(context)) }

    Row(
        modifier = modifier.padding(top = 24.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = goToMoviePlayer,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            shape = ButtonDefaults.shape(shape = MetFlixButtonShape),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = if (isTvShow) "Play S1 E1" else "Play Movie",
                style = MaterialTheme.typography.titleSmall
            )
        }

        Button(
            onClick = { showDownloadDialog = true },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            shape = ButtonDefaults.shape(shape = MetFlixButtonShape),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                focusedContainerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Download",
                style = MaterialTheme.typography.titleSmall
            )
        }

        Button(
            onClick = { PlayerResolver.launchYouTubeTrailer(context, movieTitle) },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            shape = ButtonDefaults.shape(shape = MetFlixButtonShape),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                focusedContainerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Videocam,
                contentDescription = null
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.watch_trailer),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }

    val downloadTracker = remember { DownloadTracker.instance ?: DownloadTracker(context) }
    val activeDownloads by downloadTracker.activeDownloads.collectAsStateWithLifecycle()
    val activeState = activeDownloads[movieTitle]

    DisposableEffect(movieTitle) {
        downloadTracker.registerObserver(movieTitle)
        onDispose { downloadTracker.unregisterObserver(movieTitle) }
    }

    if (activeState != null) {
        Spacer(modifier = Modifier.height(12.dp))
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color.DarkGray, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .fillMaxWidth(fraction = (activeState.progressPercent / 100f).coerceIn(0.01f, 1f))
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    )
                }

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

    if (showDownloadDialog) {
        MediaDownloadDialog(
            title = movieTitle,
            posterUrl = posterUri,
            resolveStreams = {
                DownloadStreamResolver.resolveActualStreams(
                    title = movieTitle,
                    availableLanguages = listOf("Hindi", "English")
                )
            },
            onDismiss = { showDownloadDialog = false },
            onStartDownload = { option, langs ->
                mediaDownloader.enqueueDownload(
                    catalogTitle = movieTitle,
                    directUrl = option.directUrl,
                    selectedQuality = option.quality,
                    selectedLanguages = langs,
                    mediaId = movieTitle,
                    mediaType = if (isTvShow) "SERIES" else "MOVIE"
                )
            }
        )
    }
}

@Composable
private fun DirectorScreenplayMusicRow(
    director: String,
    screenplay: String,
    music: String
) {
    Row(modifier = Modifier.padding(top = 32.dp)) {
        TitleValueText(
            modifier = Modifier
                .padding(end = 32.dp)
                .weight(1f),
            title = stringResource(R.string.director),
            value = director
        )

        TitleValueText(
            modifier = Modifier
                .padding(end = 32.dp)
                .weight(1f),
            title = stringResource(R.string.screenplay),
            value = screenplay
        )

        TitleValueText(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.music),
            value = music
        )
    }
}

@Composable
private fun MovieDescription(description: String) {
    Text(
        text = description,
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        ),
        modifier = Modifier.padding(top = 8.dp),
        maxLines = 2
    )
}

@Composable
private fun MovieLargeTitle(movieTitle: String) {
    Text(
        text = movieTitle,
        style = MaterialTheme.typography.displayMedium.copy(
            fontWeight = FontWeight.Bold
        ),
        maxLines = 1
    )
}

@Composable
private fun MovieImageWithGradients(
    movieDetails: MovieDetails,
    modifier: Modifier = Modifier,
    gradientColor: Color = MaterialTheme.colorScheme.surface,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(movieDetails.posterUri)
            .crossfade(true).build(),
        contentDescription = StringConstants
            .Composable
            .ContentDescription
            .moviePoster(movieDetails.name),
        contentScale = ContentScale.Crop,
        modifier = modifier.drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, gradientColor),
                    startY = 600f
                )
            )
            drawRect(
                Brush.horizontalGradient(
                    colors = listOf(gradientColor, Color.Transparent),
                    endX = 1000f,
                    startX = 300f
                )
            )
            drawRect(
                Brush.linearGradient(
                    colors = listOf(gradientColor, Color.Transparent),
                    start = Offset(x = 500f, y = 500f),
                    end = Offset(x = 1000f, y = 0f)
                )
            )
        }
    )
}
