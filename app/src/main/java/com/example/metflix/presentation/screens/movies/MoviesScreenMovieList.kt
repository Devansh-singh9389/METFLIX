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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.settings.PerformancePreferences
import com.example.metflix.data.util.StringConstants
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import com.example.metflix.presentation.theme.MetFlixBorderWidth

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MoviesScreenMovieList(
    modifier: Modifier = Modifier,
    movieList: List<Movie>,
    startPadding: Dp = rememberChildPadding().start,
    endPadding: Dp = rememberChildPadding().end,
    onMovieClick: (movie: Movie) -> Unit
) {
    LazyRow(
        modifier = modifier.focusRestorer(),
        contentPadding = PaddingValues(start = startPadding, end = endPadding),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(
            movieList,
            key = { it.id },
            contentType = { "movie_list_item" }
        ) {
            MovieListItem(
                itemWidth = 380.dp,
                onMovieClick = onMovieClick,
                movie = it,
            )
        }
    }
}

@Composable
private fun MovieListItem(
    itemWidth: Dp,
    movie: Movie,
    modifier: Modifier = Modifier,
    onMovieClick: (movie: Movie) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(MetFlixBorderWidth))
        var isFocused by remember { mutableStateOf(false) }
        CompactCard(
            modifier = modifier
                .width(itemWidth)
                .aspectRatio(16 / 9f)
                .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
            scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(
                        width = MetFlixBorderWidth, color = MaterialTheme.colorScheme.onSurface
                    )
                ),
                pressedBorder = Border(
                    border = BorderStroke(
                        width = MetFlixBorderWidth, color = MaterialTheme.colorScheme.onSurface
                    )
                )
            ),
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            onClick = { onMovieClick(movie) },
            image = {
                val contentAlpha = if (isFocused) 1f else 0.75f
                val context = LocalContext.current
                val isHighQuality = PerformancePreferences.isHighQualityImagesActive
                val imageRequest = remember(movie.posterUri, isHighQuality) {
                    ImageRequest.Builder(context)
                        .data(movie.posterUri)
                        .size(if (isHighQuality) 600 else 380, if (isHighQuality) 338 else 214)
                        .bitmapConfig(if (isHighQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
                        .allowRgb565(!isHighQuality)
                        .precision(Precision.INEXACT)
                        .crossfade(false)
                        .build()
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = StringConstants
                            .Composable
                            .ContentDescription
                            .moviePoster(movie.name),
                        contentScale = ContentScale.Crop,
                        clipToBounds = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = contentAlpha }
                    )
                    // High-contrast vertical scrim gradient for text legibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.90f)
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            )
                    )
                }
            },
            title = {
                Column {
                    Text(
                        text = movie.description,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier
                            .graphicsLayer { alpha = 0.8f }
                            .padding(start = 20.dp, end = 20.dp)
                    )
                    Text(
                        text = movie.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 20.dp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }
}
