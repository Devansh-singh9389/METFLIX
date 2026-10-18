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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.ui.Alignment
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
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
                lazyListState.firstVisibleItemScrollOffset < 300
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }

    LaunchedEffect(state.selectedFilter) {
        lazyListState.scrollToItem(0)
    }

    Column(
        modifier = modifier
    ) {
        // Pinned Category Filter Chips
        MovieCategoryFilterChips(
            selectedFilter = state.selectedFilter,
            onFilterSelected = onFilterSelected,
            modifier = Modifier.padding(top = childPadding.top, bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 104.dp)
        ) {
            when (state.selectedFilter) {
                "All" -> {
                    val allEmpty = state.heroMovieList.isEmpty() &&
                        state.trendingTodayList.isEmpty() &&
                        state.trendingThisWeekList.isEmpty() &&
                        state.popularMoviesList.isEmpty() &&
                        state.topRatedList.isEmpty() &&
                        state.upcomingList.isEmpty() &&
                        state.boxOfficeHitsList.isEmpty() &&
                        state.mostVotedList.isEmpty() &&
                        state.netflixList.isEmpty() &&
                        state.primeList.isEmpty() &&
                        state.disneyList.isEmpty()

                    if (allEmpty) {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "Movies")
                        }
                    } else {
                        // Featured In Theatres Hero Row
                        if (state.heroMovieList.isNotEmpty()) {
                            item(contentType = "hero_row") {
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
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "🔥 Trending Today",
                                    movieList = state.trendingTodayList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }

                        if (state.trendingThisWeekList.isNotEmpty()) {
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "📈 Trending This Week",
                                    movieList = state.trendingThisWeekList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }

                        if (state.popularMoviesList.isNotEmpty()) {
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "🍿 Popular Movies",
                                    movieList = state.popularMoviesList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }

                        if (state.topRatedList.isNotEmpty()) {
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "🌟 Top Rated Classics",
                                    movieList = state.topRatedList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }

                        if (state.upcomingList.isNotEmpty()) {
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "🗓️ Upcoming Releases",
                                    movieList = state.upcomingList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }

                        if (state.boxOfficeHitsList.isNotEmpty()) {
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "💰 Top Box Office & Revenue",
                                    movieList = state.boxOfficeHitsList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }

                        if (state.mostVotedList.isNotEmpty()) {
                            item(contentType = "movies_row") {
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
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "🔴 Trending on Netflix",
                                    movieList = state.netflixList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }

                        if (state.primeList.isNotEmpty()) {
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "🔷 Streaming on Prime Video",
                                    movieList = state.primeList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }

                        if (state.disneyList.isNotEmpty()) {
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "✨ Disney+ Hotstar Hits",
                                    movieList = state.disneyList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }
                    }
                }

                "In Theatres" -> {
                    if (state.heroMovieList.isNotEmpty()) {
                        item(contentType = "hero_row") {
                            MoviesScreenMovieList(
                                movieList = state.heroMovieList,
                                onMovieClick = onMovieClick
                            )
                        }
                    } else {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "In Theatres")
                        }
                    }
                }

                "Trending Today" -> {
                    if (state.trendingTodayList.isNotEmpty() || state.trendingThisWeekList.isNotEmpty()) {
                        if (state.trendingTodayList.isNotEmpty()) {
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "🔥 Trending Today",
                                    movieList = state.trendingTodayList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }
                        if (state.trendingThisWeekList.isNotEmpty()) {
                            item(contentType = "movies_row") {
                                MoviesRow(
                                    modifier = Modifier.padding(top = childPadding.top),
                                    title = "📈 Trending This Week",
                                    movieList = state.trendingThisWeekList,
                                    onMovieSelected = onMovieClick
                                )
                            }
                        }
                    } else {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "Trending Today")
                        }
                    }
                }

                "Trending Week" -> {
                    if (state.trendingThisWeekList.isNotEmpty()) {
                        item(contentType = "movies_row") {
                            MoviesRow(
                                modifier = Modifier.padding(top = childPadding.top),
                                title = "📈 Trending This Week",
                                movieList = state.trendingThisWeekList,
                                onMovieSelected = onMovieClick
                            )
                        }
                    } else {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "Trending Week")
                        }
                    }
                }

                "Top Rated" -> {
                    if (state.topRatedList.isNotEmpty()) {
                        item(contentType = "movies_row") {
                            MoviesRow(
                                modifier = Modifier.padding(top = childPadding.top),
                                title = "🌟 Top Rated Movies",
                                movieList = state.topRatedList,
                                onMovieSelected = onMovieClick
                            )
                        }
                    } else {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "Top Rated")
                        }
                    }
                }

                "Upcoming" -> {
                    if (state.upcomingList.isNotEmpty()) {
                        item(contentType = "movies_row") {
                            MoviesRow(
                                modifier = Modifier.padding(top = childPadding.top),
                                title = "🗓️ Upcoming Cinema Releases",
                                movieList = state.upcomingList,
                                onMovieSelected = onMovieClick
                            )
                        }
                    } else {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "Upcoming Releases")
                        }
                    }
                }

                "Box Office" -> {
                    if (state.boxOfficeHitsList.isNotEmpty()) {
                        item(contentType = "movies_row") {
                            MoviesRow(
                                modifier = Modifier.padding(top = childPadding.top),
                                title = "💰 Top Box Office Hits",
                                movieList = state.boxOfficeHitsList,
                                onMovieSelected = onMovieClick
                            )
                        }
                    } else {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "Box Office")
                        }
                    }
                }

                "Most Voted" -> {
                    if (state.mostVotedList.isNotEmpty()) {
                        item(contentType = "movies_row") {
                            MoviesRow(
                                modifier = Modifier.padding(top = childPadding.top),
                                title = "⭐ Highly Rated & Most Voted",
                                movieList = state.mostVotedList,
                                onMovieSelected = onMovieClick
                            )
                        }
                    } else {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "Most Voted")
                        }
                    }
                }

                "Netflix" -> {
                    if (state.netflixList.isNotEmpty()) {
                        item(contentType = "movies_row") {
                            MoviesRow(
                                modifier = Modifier.padding(top = childPadding.top),
                                title = "🔴 Popular on Netflix",
                                movieList = state.netflixList,
                                onMovieSelected = onMovieClick
                            )
                        }
                    } else {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "Netflix")
                        }
                    }
                }

                "Prime Video" -> {
                    if (state.primeList.isNotEmpty()) {
                        item(contentType = "movies_row") {
                            MoviesRow(
                                modifier = Modifier.padding(top = childPadding.top),
                                title = "🔷 Popular on Amazon Prime Video",
                                movieList = state.primeList,
                                onMovieSelected = onMovieClick
                            )
                        }
                    } else {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "Prime Video")
                        }
                    }
                }

                "Disney+" -> {
                    if (state.disneyList.isNotEmpty()) {
                        item(contentType = "movies_row") {
                            MoviesRow(
                                modifier = Modifier.padding(top = childPadding.top),
                                title = "✨ Disney+ Hotstar Specials",
                                movieList = state.disneyList,
                                onMovieSelected = onMovieClick
                            )
                        }
                    } else {
                        item(contentType = "empty_state") {
                            MoviesEmptyCategoryState(categoryName = "Disney+")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviesEmptyCategoryState(
    categoryName: String,
    modifier: Modifier = Modifier
) {
    val childPadding = rememberChildPadding()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = childPadding.start, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MovieFilter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No movies currently found in \"$categoryName\"",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Try selecting another category or check back later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
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
