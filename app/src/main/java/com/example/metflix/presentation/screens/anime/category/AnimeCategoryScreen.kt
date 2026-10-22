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

package com.example.metflix.presentation.screens.anime.category

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.entities.Movie
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.common.MovieCard
import com.example.metflix.presentation.common.PosterImage
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import com.example.metflix.presentation.theme.MetFlixBottomListPadding
import com.example.metflix.presentation.utils.focusOnInitialVisibility

@Composable
fun AnimeCategoryScreen(
    onBackPressed: () -> Unit,
    onMovieClick: (mediaType: String, slug: String) -> Unit,
    viewModel: AnimeCategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBackPressed)

    when (val state = uiState) {
        is AnimeCategoryUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        is AnimeCategoryUiState.Empty -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📁", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No titles found in this category",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackPressed) {
                        Text(text = "Go Back")
                    }
                }
            }
        }

        is AnimeCategoryUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "⚠️", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Failed to load category",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { viewModel.retry() }) {
                            Text(text = "Retry")
                        }
                        Button(onClick = onBackPressed) {
                            Text(text = "Go Back")
                        }
                    }
                }
            }
        }

        is AnimeCategoryUiState.Ready -> {
            val childPadding = rememberChildPadding()
            val isFirstItemVisible = remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = childPadding.top)
            ) {
                // Header with title and count (no search bar, clean grid header)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = childPadding.start, end = childPadding.end, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${state.movies.size} Titles Available",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 6-Column Grid matching Search tab layout
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(
                        start = childPadding.start,
                        end = childPadding.end,
                        bottom = MetFlixBottomListPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = state.movies,
                        key = { _, item -> item.id }
                    ) { index, movie ->
                        CategoryMovieGridItem(
                            movie = movie,
                            isFirst = index == 0,
                            isFirstItemVisible = isFirstItemVisible,
                            onClick = {
                                val cleanId = movie.id.removePrefix("movie/").removePrefix("series/").removePrefix("category/")
                                val mediaType = if (movie.id.startsWith("movie/") || movie.description.contains("Movie")) {
                                    "movie"
                                } else {
                                    "series"
                                }
                                onMovieClick(mediaType, cleanId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryMovieGridItem(
    movie: Movie,
    isFirst: Boolean,
    isFirstItemVisible: androidx.compose.runtime.MutableState<Boolean>,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    MovieCard(
        onClick = onClick,
        title = {
            Text(
                text = movie.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocused) MaterialTheme.colorScheme.onSurface else Color.Transparent
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                maxLines = 1
            )
        },
        modifier = Modifier
            .aspectRatio(1 / 1.5f)
            .then(if (isFirst) Modifier.focusOnInitialVisibility(isFirstItemVisible) else Modifier)
    ) {
        PosterImage(
            movie = movie,
            modifier = Modifier.fillMaxSize()
        )
    }
}
