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
import com.example.metflix.data.entities.MovieList
import com.example.metflix.data.repositories.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@HiltViewModel
class AnimeScreenViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnimeScreenUiState>(AnimeScreenUiState.Loading)
    val uiState: StateFlow<AnimeScreenUiState> = _uiState.asStateFlow()

    private var trendingAnimeList: MovieList = emptyList()
    private var animeMoviesList: MovieList = emptyList()
    private var cartoonsList: MovieList = emptyList()
    private var topRatedAnimeList: MovieList = emptyList()

    init {
        loadData()
    }

    private fun emitState() {
        _uiState.value = AnimeScreenUiState.Ready(
            trendingAnimeList = trendingAnimeList,
            animeMoviesList = animeMoviesList,
            cartoonsList = cartoonsList,
            topRatedAnimeList = topRatedAnimeList
        )
    }

    private fun loadData() {
        viewModelScope.launch {
            movieRepository.getTrendingAnime()
                .catch { emit(emptyList()) }
                .collect { list ->
                    trendingAnimeList = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getAnimeMovies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    animeMoviesList = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getCartoons()
                .catch { emit(emptyList()) }
                .collect { list ->
                    cartoonsList = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getTopRatedAnime()
                .catch { emit(emptyList()) }
                .collect { list ->
                    topRatedAnimeList = list
                    emitState()
                }
        }
    }
}

@Immutable
sealed interface AnimeScreenUiState {
    data object Loading : AnimeScreenUiState
    @Immutable
    data class Ready(
        val trendingAnimeList: MovieList,
        val animeMoviesList: MovieList,
        val cartoonsList: MovieList,
        val topRatedAnimeList: MovieList
    ) : AnimeScreenUiState
}
