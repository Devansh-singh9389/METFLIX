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

package com.example.metflix.presentation.screens.home

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
import kotlinx.coroutines.launch

@HiltViewModel
class HomeScreeViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeScreenUiState>(HomeScreenUiState.Loading)
    val uiState: StateFlow<HomeScreenUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        var featured: MovieList = emptyList()
        var trending: MovieList = emptyList()
        var top10: MovieList = emptyList()
        var nowPlaying: MovieList = emptyList()
        var popularThisWeek: MovieList = emptyList()
        var netflix: MovieList = emptyList()
        var prime: MovieList = emptyList()
        var disney: MovieList = emptyList()
        var upcoming: MovieList = emptyList()
        var topRated: MovieList = emptyList()
        var bingeDramas: MovieList = emptyList()

        fun emitState() {
            if (featured.isNotEmpty() || trending.isNotEmpty() || top10.isNotEmpty() || nowPlaying.isNotEmpty()) {
                _uiState.value = HomeScreenUiState.Ready(
                    featuredMovieList = featured,
                    trendingMovieList = trending,
                    top10MovieList = top10,
                    nowPlayingMovieList = nowPlaying,
                    popularThisWeekList = popularThisWeek,
                    netflixMovieList = netflix,
                    primeMovieList = prime,
                    disneyMovieList = disney,
                    upcomingMovieList = upcoming,
                    topRatedMovieList = topRated,
                    bingeDramasMovieList = bingeDramas
                )
            }
        }

        viewModelScope.launch {
            movieRepository.getFeaturedMovies().collect { list ->
                featured = list
                emitState()
            }
        }

        viewModelScope.launch {
            movieRepository.getTrendingMovies().collect { list ->
                trending = list
                emitState()
            }
        }

        viewModelScope.launch {
            movieRepository.getTop10Movies().collect { list ->
                top10 = list
                emitState()
            }
        }

        viewModelScope.launch {
            movieRepository.getNowPlayingMovies().collect { list ->
                nowPlaying = list
                emitState()
            }
        }

        viewModelScope.launch {
            movieRepository.getPopularFilmsThisWeek().collect { list ->
                popularThisWeek = list
                emitState()
            }
        }

        viewModelScope.launch {
            movieRepository.getNetflixMovies().collect { list ->
                netflix = list
                emitState()
            }
        }

        viewModelScope.launch {
            movieRepository.getPrimeMovies().collect { list ->
                prime = list
                emitState()
            }
        }

        viewModelScope.launch {
            movieRepository.getDisneyMovies().collect { list ->
                disney = list
                emitState()
            }
        }

        viewModelScope.launch {
            movieRepository.getUpcomingMovies().collect { list ->
                upcoming = list
                emitState()
            }
        }

        viewModelScope.launch {
            movieRepository.getMostVotedMovies().collect { list ->
                topRated = list
                emitState()
            }
        }

        viewModelScope.launch {
            movieRepository.getBingeWatchDramas().collect { list ->
                bingeDramas = list
                emitState()
            }
        }
    }
}

@Immutable
sealed interface HomeScreenUiState {
    data object Loading : HomeScreenUiState
    data object Error : HomeScreenUiState
    @Immutable
    data class Ready(
        val featuredMovieList: MovieList,
        val trendingMovieList: MovieList,
        val top10MovieList: MovieList,
        val nowPlayingMovieList: MovieList,
        val popularThisWeekList: MovieList = emptyList(),
        val netflixMovieList: MovieList = emptyList(),
        val primeMovieList: MovieList = emptyList(),
        val disneyMovieList: MovieList = emptyList(),
        val upcomingMovieList: MovieList = emptyList(),
        val topRatedMovieList: MovieList = emptyList(),
        val bingeDramasMovieList: MovieList = emptyList()
    ) : HomeScreenUiState
}
