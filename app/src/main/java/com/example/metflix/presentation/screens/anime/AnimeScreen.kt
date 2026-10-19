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

package com.example.metflix.presentation.screens.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.entities.MovieList
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.common.MoviesRow
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import com.example.metflix.presentation.screens.movies.MoviesScreenMovieList

@Composable
fun AnimeScreen(
    onAnimeClick: (movie: Movie) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: AnimeScreenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is AnimeScreenUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        is AnimeScreenUiState.Ready -> {
            if (state.trendingAnimeList.isEmpty() &&
                state.animeMoviesList.isEmpty() &&
                state.cartoonsList.isEmpty() &&
                state.topRatedAnimeList.isEmpty()
            ) {
                AnimeEmptyState(modifier = Modifier.fillMaxSize())
            } else {
                AnimeCatalog(
                    state = state,
                    onAnimeClick = onAnimeClick,
                    onScroll = onScroll,
                    isTopBarVisible = isTopBarVisible,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun AnimeCatalog(
    state: AnimeScreenUiState.Ready,
    onAnimeClick: (movie: Movie) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val childPadding = rememberChildPadding()
    val lazyListState = rememberLazyListState()
    val shouldShowTopBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset < 300
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = PaddingValues(top = childPadding.top, bottom = 104.dp)
    ) {
        // 1. Trending Anime Hero Section
        if (state.trendingAnimeList.isNotEmpty()) {
            item(contentType = "anime_header") {
                Text(
                    text = "🎌 Trending Anime Series (Sub & Dub)",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 30.sp
                    ),
                    modifier = Modifier.padding(start = childPadding.start, top = 8.dp, bottom = 16.dp)
                )
            }
            item(contentType = "trending_anime_grid") {
                MoviesScreenMovieList(
                    movieList = state.trendingAnimeList,
                    onMovieClick = onAnimeClick
                )
            }
        }

        // 2. Animated Cartoons & Kids Favorites (Doraemon, Shinchan, Tom & Jerry, etc.)
        if (state.cartoonsList.isNotEmpty()) {
            item(contentType = "cartoons_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "🎙️ Hindi Dubbed Cartoons & Kids Shows",
                    movieList = state.cartoonsList,
                    onMovieSelected = onAnimeClick
                )
            }
        }

        // 3. Popular Anime Movies (Studio Ghibli, Makoto Shinkai, Doraemon Movies)
        if (state.animeMoviesList.isNotEmpty()) {
            item(contentType = "anime_movies_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "🍿 Popular Anime Movies (Multi-Audio)",
                    movieList = state.animeMoviesList,
                    onMovieSelected = onAnimeClick
                )
            }
        }

        // 4. Top Rated Anime All Time
        if (state.topRatedAnimeList.isNotEmpty()) {
            item(contentType = "top_rated_anime_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "⭐ All-Time Greatest Anime Series",
                    movieList = state.topRatedAnimeList,
                    onMovieSelected = onAnimeClick
                )
            }
        }
    }
}

@Composable
private fun AnimeEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 48.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🎌", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Anime Available",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please check your network connection or try again later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
