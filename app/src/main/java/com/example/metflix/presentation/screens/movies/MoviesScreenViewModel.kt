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
class MoviesScreenViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _uiState = MutableStateFlow<MoviesScreenUiState>(MoviesScreenUiState.Loading)
    val uiState: StateFlow<MoviesScreenUiState> = _uiState.asStateFlow()

    private var nowPlaying: MovieList = emptyList()
    private var trendingDay: MovieList = emptyList()
    private var trendingWeek: MovieList = emptyList()
    private var topRated: MovieList = emptyList()
    private var popular: MovieList = emptyList()
    private var upcoming: MovieList = emptyList()
    private var boxOffice: MovieList = emptyList()
    private var mostVoted: MovieList = emptyList()
    private var netflix: MovieList = emptyList()
    private var prime: MovieList = emptyList()
    private var disney: MovieList = emptyList()

    init {
        loadData()
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
        val current = _uiState.value
        if (current is MoviesScreenUiState.Ready) {
            _uiState.value = current.copy(selectedFilter = filter)
        } else {
            emitState()
        }
    }

    private fun emitState() {
        _uiState.value = MoviesScreenUiState.Ready(
            heroMovieList = nowPlaying.ifEmpty { popular },
            trendingTodayList = trendingDay.ifEmpty { trendingWeek },
            trendingThisWeekList = trendingWeek,
            topRatedList = topRated,
            popularMoviesList = popular,
            upcomingList = upcoming,
            boxOfficeHitsList = boxOffice,
            mostVotedList = mostVoted,
            netflixList = netflix,
            primeList = prime,
            disneyList = disney,
            selectedFilter = _selectedFilter.value
        )
    }

    private fun loadData() {
        viewModelScope.launch {
            movieRepository.getNowPlayingMovies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    nowPlaying = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getTrendingDayMovies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    trendingDay = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getTrendingMovies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    trendingWeek = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getTop10Movies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    topRated = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getPopularFilmsThisWeek()
                .catch { emit(emptyList()) }
                .collect { list ->
                    popular = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getUpcomingMovies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    upcoming = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getTopRevenueMovies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    boxOffice = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getMostVotedMovies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    mostVoted = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getNetflixMovies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    netflix = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getPrimeMovies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    prime = list
                    emitState()
                }
        }

        viewModelScope.launch {
            movieRepository.getDisneyMovies()
                .catch { emit(emptyList()) }
                .collect { list ->
                    disney = list
                    emitState()
                }
        }
    }
}

@Immutable
sealed interface MoviesScreenUiState {
    data object Loading : MoviesScreenUiState

    @Immutable
    data class Ready(
        val heroMovieList: MovieList,
        val trendingTodayList: MovieList,
        val trendingThisWeekList: MovieList,
        val topRatedList: MovieList,
        val popularMoviesList: MovieList,
        val upcomingList: MovieList,
        val boxOfficeHitsList: MovieList,
        val mostVotedList: MovieList,
        val netflixList: MovieList,
        val primeList: MovieList,
        val disneyList: MovieList,
        val selectedFilter: String = "All"
    ) : MoviesScreenUiState
}
