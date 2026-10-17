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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.entities.Movie
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.common.MoviesRow
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding

@Composable
fun MoviesScreen(
    onMovieClick: (movie: Movie) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    moviesScreenViewModel: MoviesScreenViewModel = hiltViewModel(),
) {
    val uiState by moviesScreenViewModel.uiState.collectAsStateWithLifecycle()
    when (val s = uiState) {
        is MoviesScreenUiState.Loading -> Loading()
        is MoviesScreenUiState.Ready -> {
            Catalog(
                state = s,
                onMovieClick = onMovieClick,
                onScroll = onScroll,
                onFilterSelected = { moviesScreenViewModel.setFilter(it) },
                isTopBarVisible = isTopBarVisible,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Catalog(
    state: MoviesScreenUiState.Ready,
    onMovieClick: (movie: Movie) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    onFilterSelected: (String) -> Unit,
    isTopBarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val childPadding = rememberChildPadding()
    val lazyListState = rememberLazyListState()
    val shouldShowTopBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
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
        // 1. TMDb Helper Category Filter Chips
        item {
            MovieCategoryFilterChips(
                selectedFilter = state.selectedFilter,
                onFilterSelected = onFilterSelected,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        when (state.selectedFilter) {
            "All" -> {
                // Featured In Theatres Hero Row
                if (state.heroMovieList.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "🎬 In Theatres & Featured",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(start = childPadding.start, bottom = 12.dp)
                            )
                            MoviesScreenMovieList(
                                movieList = state.heroMovieList,
                                onMovieClick = onMovieClick
                            )
                        }
                    }
                }

                // TMDb Helper Standard Rows
                if (state.trendingTodayList.isNotEmpty()) {
                    item {
                        MoviesRow(
                            modifier = Modifier.padding(top = childPadding.top),
                            title = "🔥 Trending Today",
                            movieList = state.trendingTodayList,
                            onMovieSelected = onMovieClick
                        )
                    }
                }

                if (state.trendingThisWeekList.isNotEmpty()) {
                    item {
                        MoviesRow(
                            modifier = Modifier.padding(top = childPadding.top),
                            title = "📈 Trending This Week",
                            movieList = state.trendingThisWeekList,
                            onMovieSelected = onMovieClick
                        )
                    }
                }

                if (state.popularMoviesList.isNotEmpty()) {
                    item {
                        MoviesRow(
                            modifier = Modifier.padding(top = childPadding.top),
                            title = "🍿 Popular Movies",
                            movieList = state.popularMoviesList,
                            onMovieSelected = onMovieClick
                        )
                    }
                }

                if (state.topRatedList.isNotEmpty()) {
                    item {
                        MoviesRow(
                            modifier = Modifier.padding(top = childPadding.top),
                            title = "🌟 Top Rated Classics",
                            movieList = state.topRatedList,
                            onMovieSelected = onMovieClick
                        )
                    }
                }

                if (state.upcomingList.isNotEmpty()) {
                    item {
                        MoviesRow(
                            modifier = Modifier.padding(top = childPadding.top),
                            title = "🗓️ Upcoming Releases",
                            movieList = state.upcomingList,
                            onMovieSelected = onMovieClick
                        )
                    }
                }

                if (state.boxOfficeHitsList.isNotEmpty()) {
                    item {
                        MoviesRow(
                            modifier = Modifier.padding(top = childPadding.top),
                            title = "💰 Top Box Office & Revenue",
                            movieList = state.boxOfficeHitsList,
                            onMovieSelected = onMovieClick
                        )
                    }
                }

                if (state.mostVotedList.isNotEmpty()) {
                    item {
                        MoviesRow(
                            modifier = Modifier.padding(top = childPadding.top),
                            title = "⭐ Fan Favorites (Most Voted)",
                            movieList = state.mostVotedList,
                            onMovieSelected = onMovieClick
                        )
                    }
                }

                // Streaming Provider Rows (TMDb Helper Watch Providers)
                if (state.netflixList.isNotEmpty()) {
                    item {
                        MoviesRow(
                            modifier = Modifier.padding(top = childPadding.top),
                            title = "🔴 Trending on Netflix",
                            movieList = state.netflixList,
                            onMovieSelected = onMovieClick
                        )
                    }
                }

                if (state.primeList.isNotEmpty()) {
                    item {
                        MoviesRow(
                            modifier = Modifier.padding(top = childPadding.top),
                            title = "🔷 Streaming on Prime Video",
                            movieList = state.primeList,
                            onMovieSelected = onMovieClick
                        )
                    }
                }

                if (state.disneyList.isNotEmpty()) {
                    item {
                        MoviesRow(
                            modifier = Modifier.padding(top = childPadding.top),
                            title = "✨ Disney+ Hotstar Hits",
                            movieList = state.disneyList,
                            onMovieSelected = onMovieClick
                        )
                    }
                }
            }

            "In Theatres" -> {
                item {
                    MoviesScreenMovieList(
                        movieList = state.heroMovieList,
                        onMovieClick = onMovieClick
                    )
                }
            }

            "Trending Today" -> {
                item {
                    MoviesRow(
                        modifier = Modifier.padding(top = childPadding.top),
                        title = "🔥 Trending Today",
                        movieList = state.trendingTodayList,
                        onMovieSelected = onMovieClick
                    )
                }
                item {
                    MoviesRow(
                        modifier = Modifier.padding(top = childPadding.top),
                        title = "📈 Trending This Week",
                        movieList = state.trendingThisWeekList,
                        onMovieSelected = onMovieClick
                    )
                }
            }

            "Trending Week" -> {
                item {
                    MoviesRow(
                        modifier = Modifier.padding(top = childPadding.top),
                        title = "📈 Trending This Week",
                        movieList = state.trendingThisWeekList,
                        onMovieSelected = onMovieClick
                    )
                }
            }

            "Top Rated" -> {
                item {
                    MoviesRow(
                        modifier = Modifier.padding(top = childPadding.top),
                        title = "🌟 Top Rated Movies",
                        movieList = state.topRatedList,
                        onMovieSelected = onMovieClick
                    )
                }
            }

            "Upcoming" -> {
                item {
                    MoviesRow(
                        modifier = Modifier.padding(top = childPadding.top),
                        title = "🗓️ Upcoming Cinema Releases",
                        movieList = state.upcomingList,
                        onMovieSelected = onMovieClick
                    )
                }
            }

            "Box Office" -> {
                item {
                    MoviesRow(
                        modifier = Modifier.padding(top = childPadding.top),
                        title = "💰 Top Box Office Hits",
                        movieList = state.boxOfficeHitsList,
                        onMovieSelected = onMovieClick
                    )
                }
            }

            "Most Voted" -> {
                item {
                    MoviesRow(
                        modifier = Modifier.padding(top = childPadding.top),
                        title = "⭐ Highly Rated & Most Voted",
                        movieList = state.mostVotedList,
                        onMovieSelected = onMovieClick
                    )
                }
            }

            "Netflix" -> {
                item {
                    MoviesRow(
                        modifier = Modifier.padding(top = childPadding.top),
                        title = "🔴 Popular on Netflix",
                        movieList = state.netflixList,
                        onMovieSelected = onMovieClick
                    )
                }
            }

            "Prime Video" -> {
                item {
                    MoviesRow(
                        modifier = Modifier.padding(top = childPadding.top),
                        title = "🔷 Popular on Amazon Prime Video",
                        movieList = state.primeList,
                        onMovieSelected = onMovieClick
                    )
                }
            }

            "Disney+" -> {
                item {
                    MoviesRow(
                        modifier = Modifier.padding(top = childPadding.top),
                        title = "✨ Disney+ Hotstar Specials",
                        movieList = state.disneyList,
                        onMovieSelected = onMovieClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MovieCategoryFilterChips(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val childPadding = rememberChildPadding()
    val categories = remember {
        listOf(
            "All",
            "In Theatres",
            "Trending Today",
            "Trending Week",
            "Top Rated",
            "Upcoming",
            "Box Office",
            "Most Voted",
            "Netflix",
            "Prime Video",
            "Disney+"
        )
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer(),
        contentPadding = PaddingValues(start = childPadding.start, end = childPadding.end),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(categories, key = { it }) { cat ->
            val isSelected = selectedFilter == cat
            Card(
                onClick = { onFilterSelected(cat) },
                shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
                colors = CardDefaults.colors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = CardDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface))
                )
            ) {
                Text(
                    text = cat,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
