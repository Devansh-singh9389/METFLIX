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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.entities.Movie
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.common.MoviesRow
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import com.example.metflix.presentation.screens.movies.MoviesScreenMovieList

@Composable
fun AnimeScreen(
    onAnimeCategoryClick: (categoryType: String, slug: String) -> Unit = { _, _ -> },
    onAnimeSeriesClick: (slug: String) -> Unit = {},
    onAnimeMovieClick: (slug: String) -> Unit = {},
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: AnimeScreenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is AnimeScreenUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        is AnimeScreenUiState.Empty -> {
            AnimeEmptyState(modifier = Modifier.fillMaxSize())
        }

        is AnimeScreenUiState.Error -> {
            AnimeErrorState(
                message = state.message,
                onRetry = { viewModel.retry() },
                modifier = Modifier.fillMaxSize()
            )
        }

        is AnimeScreenUiState.Ready -> {
            if (state.homeData.isEmpty) {
                AnimeEmptyState(modifier = Modifier.fillMaxSize())
            } else {
                AnimeCatalog(
                    state = state,
                    onAnimeCategoryClick = onAnimeCategoryClick,
                    onAnimeSeriesClick = onAnimeSeriesClick,
                    onAnimeMovieClick = onAnimeMovieClick,
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
    onAnimeCategoryClick: (categoryType: String, slug: String) -> Unit,
    onAnimeSeriesClick: (slug: String) -> Unit,
    onAnimeMovieClick: (slug: String) -> Unit,
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

    val homeData = state.homeData

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = PaddingValues(top = childPadding.top, bottom = 104.dp)
    ) {
        // 1. Newest Drops Hero Section (Featured grid with episodes and badges)
        if (homeData.newestDrops.isNotEmpty()) {
            item(contentType = "anime_header") {
                Text(
                    text = "⚡ Newest Drops & Latest Episodes",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 30.sp
                    ),
                    modifier = Modifier.padding(start = childPadding.start, top = 8.dp, bottom = 16.dp)
                )
            }
            item(contentType = "newest_drops_grid") {
                MoviesScreenMovieList(
                    movieList = homeData.newestDrops,
                    onMovieClick = { movie ->
                        val slug = movie.id.substringAfterLast("/")
                        if (movie.id.startsWith("movie/") || movie.description.contains("Movie")) {
                            onAnimeMovieClick(slug)
                        } else {
                            onAnimeSeriesClick(slug)
                        }
                    }
                )
            }
        }

        // 2. Popular Franchises & Big Titles (Doraemon, Shinchan, Naruto, Ben 10, Pokemon...)
        if (homeData.bigTitles.isNotEmpty()) {
            item(contentType = "big_titles_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "🔥 Popular Anime & Cartoon Franchises",
                    movieList = homeData.bigTitles,
                    onMovieSelected = { movie ->
                        val parts = movie.id.removePrefix("/").removePrefix("category/").split("/").filter { it.isNotBlank() }
                        val type = if (parts.isNotEmpty()) parts[0] else "franchise"
                        val slug = if (parts.size > 1) parts[1] else parts.firstOrNull() ?: "doraemon"
                        onAnimeCategoryClick(type, slug)
                    }
                )
            }
        }

        // 3. New Anime Arrivals
        if (homeData.newAnimeArrivals.isNotEmpty()) {
            item(contentType = "new_anime_arrivals_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "🎌 New Anime Arrivals",
                    movieList = homeData.newAnimeArrivals,
                    onMovieSelected = { movie ->
                        val slug = movie.id.substringAfterLast("/")
                        onAnimeSeriesClick(slug)
                    }
                )
            }
        }

        // 4. Most Watched Anime Series
        if (homeData.mostWatchedShows.isNotEmpty()) {
            item(contentType = "most_watched_shows_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "⭐ Most Watched Anime Series",
                    movieList = homeData.mostWatchedShows,
                    onMovieSelected = { movie ->
                        val slug = movie.id.substringAfterLast("/")
                        onAnimeSeriesClick(slug)
                    }
                )
            }
        }

        // 5. Latest Anime Movies
        if (homeData.latestAnimeMovies.isNotEmpty()) {
            item(contentType = "latest_anime_movies_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "🍿 Latest Anime Movies",
                    movieList = homeData.latestAnimeMovies,
                    onMovieSelected = { movie ->
                        val slug = movie.id.substringAfterLast("/")
                        onAnimeMovieClick(slug)
                    }
                )
            }
        }

        // 6. Most Watched Films
        if (homeData.mostWatchedFilms.isNotEmpty()) {
            item(contentType = "most_watched_films_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "🏆 All-Time Popular Anime Movies",
                    movieList = homeData.mostWatchedFilms,
                    onMovieSelected = { movie ->
                        val slug = movie.id.substringAfterLast("/")
                        onAnimeMovieClick(slug)
                    }
                )
            }
        }

        // 7. Hindi Dubbed Cartoons & Kids Series
        if (homeData.cartoonSeries.isNotEmpty()) {
            item(contentType = "cartoon_series_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "🎙️ Hindi Dubbed Cartoons & Kids Shows",
                    movieList = homeData.cartoonSeries,
                    onMovieSelected = { movie ->
                        val slug = movie.id.substringAfterLast("/")
                        onAnimeSeriesClick(slug)
                    }
                )
            }
        }

        // 8. Cartoon & Animated Movies
        if (homeData.cartoonFilms.isNotEmpty()) {
            item(contentType = "cartoon_films_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "🎬 Cartoon & Animated Movies",
                    movieList = homeData.cartoonFilms,
                    onMovieSelected = { movie ->
                        val slug = movie.id.substringAfterLast("/")
                        onAnimeMovieClick(slug)
                    }
                )
            }
        }

        // 9. Stream In Your Language
        if (homeData.languages.isNotEmpty()) {
            item(contentType = "languages_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "🌐 Stream In Your Language",
                    movieList = homeData.languages,
                    onMovieSelected = { movie ->
                        val parts = movie.id.removePrefix("/").removePrefix("category/").split("/").filter { it.isNotBlank() }
                        val type = if (parts.isNotEmpty()) parts[0] else "language"
                        val slug = if (parts.size > 1) parts[1] else parts.firstOrNull() ?: "hindi"
                        onAnimeCategoryClick(type, slug)
                    }
                )
            }
        }

        // 10. Networks & Channels
        if (homeData.networks.isNotEmpty()) {
            item(contentType = "networks_row") {
                MoviesRow(
                    modifier = Modifier.padding(top = childPadding.top),
                    title = "📺 Networks & Channels",
                    movieList = homeData.networks,
                    onMovieSelected = { movie ->
                        val parts = movie.id.removePrefix("/").removePrefix("category/").split("/").filter { it.isNotBlank() }
                        val type = if (parts.isNotEmpty()) parts[0] else "network"
                        val slug = if (parts.size > 1) parts[1] else parts.firstOrNull() ?: "disney-channel"
                        onAnimeCategoryClick(type, slug)
                    }
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

@Composable
private fun AnimeErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(horizontal = 48.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "⚠️", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Failed to Load Anime",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = "Retry")
            }
        }
    }
}
