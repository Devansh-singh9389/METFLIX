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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.data.repositories.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailsScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MovieRepository,
) : ViewModel() {
    private val movieId = savedStateHandle.get<String>(MovieDetailsScreen.MovieIdBundleKey)

    private val _uiState = MutableStateFlow<MovieDetailsScreenUiState>(MovieDetailsScreenUiState.Loading)
    val uiState: StateFlow<MovieDetailsScreenUiState> = _uiState.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        val rawId = movieId
        if (rawId == null) {
            _uiState.value = MovieDetailsScreenUiState.Error
            return
        }
        val isExplicitTv = rawId.startsWith("tv:")
        val cleanId = if (isExplicitTv) rawId.removePrefix("tv:") else rawId

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val details = repository.getMovieDetails(movieId = cleanId, isTvShow = if (isExplicitTv) true else null)
                val initialSeason = details.seasons.firstOrNull()?.seasonNumber ?: 1
                _selectedSeason.value = initialSeason
                _uiState.value = MovieDetailsScreenUiState.Done(movieDetails = details)
            } catch (e: Exception) {
                _uiState.value = MovieDetailsScreenUiState.Error
            }
        }
    }

    fun onSeasonSelected(seasonNumber: Int) {
        _selectedSeason.value = seasonNumber
        val currentState = _uiState.value
        if (currentState !is MovieDetailsScreenUiState.Done) return
        val currentDetails = currentState.movieDetails
        if (!currentDetails.isTvShow) return

        val targetSeason = currentDetails.seasons.firstOrNull { it.seasonNumber == seasonNumber }
        if (targetSeason != null && targetSeason.episodes.isNotEmpty()) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val episodes = repository.getTvSeasonEpisodes(seriesId = currentDetails.id, seasonNumber = seasonNumber)
                val updatedSeasons = currentDetails.seasons.map { s ->
                    if (s.seasonNumber == seasonNumber) {
                        s.copy(episodes = episodes)
                    } else {
                        s
                    }
                }
                _uiState.value = MovieDetailsScreenUiState.Done(
                    movieDetails = currentDetails.copy(seasons = updatedSeasons)
                )
            } catch (e: Exception) {
                // Keep current state on error
            }
        }
    }
}

sealed class MovieDetailsScreenUiState {
    data object Loading : MovieDetailsScreenUiState()
    data object Error : MovieDetailsScreenUiState()
    data class Done(val movieDetails: MovieDetails) : MovieDetailsScreenUiState()
}
