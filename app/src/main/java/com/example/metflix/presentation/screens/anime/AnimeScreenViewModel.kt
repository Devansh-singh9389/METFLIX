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

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.AnimeHomeData
import com.example.metflix.data.entities.MovieList
import com.example.metflix.data.repositories.AnimeDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AnimeScreenViewModel @Inject constructor(
    private val animeDataSource: AnimeDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnimeScreenUiState>(AnimeScreenUiState.Loading)
    val uiState: StateFlow<AnimeScreenUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun retry() {
        _uiState.value = AnimeScreenUiState.Loading
        loadData(forceRefresh = true)
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                val data = animeDataSource.getHomeData(forceRefresh)
                if (data.isEmpty) {
                    _uiState.value = AnimeScreenUiState.Empty
                } else {
                    _uiState.value = AnimeScreenUiState.Ready(homeData = data)
                }
            } catch (e: Exception) {
                val cached = animeDataSource.getHomeData(forceRefresh = false)
                if (!cached.isEmpty) {
                    _uiState.value = AnimeScreenUiState.Ready(homeData = cached)
                } else {
                    _uiState.value = AnimeScreenUiState.Error(e.message ?: "Failed to load anime data")
                }
            }
        }
    }
}

@Immutable
sealed interface AnimeScreenUiState {
    data object Loading : AnimeScreenUiState
    data object Empty : AnimeScreenUiState
    @Immutable
    data class Error(val message: String) : AnimeScreenUiState
    @Immutable
    data class Ready(
        val homeData: AnimeHomeData,
        val trendingAnimeList: MovieList = homeData.newAnimeArrivals.ifEmpty { homeData.newestDrops },
        val animeMoviesList: MovieList = homeData.latestAnimeMovies.ifEmpty { homeData.mostWatchedFilms },
        val cartoonsList: MovieList = homeData.cartoonSeries.ifEmpty { homeData.cartoonFilms },
        val topRatedAnimeList: MovieList = homeData.mostWatchedShows
    ) : AnimeScreenUiState
}
