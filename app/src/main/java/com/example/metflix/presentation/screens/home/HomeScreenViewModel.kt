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

package com.example.metflix.presentation.screens.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.MovieList
import com.example.metflix.data.repositories.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class HomeScreeViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeScreenUiState>(HomeScreenUiState.Loading)
    val uiState: StateFlow<HomeScreenUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadData()
    }

    fun retry() {
        _uiState.value = HomeScreenUiState.Loading
        loadData()
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            var featured: MovieList = emptyList()
            var trending: MovieList = emptyList()
            var top10: MovieList = emptyList()
            var nowPlaying: MovieList = emptyList()
            var popularThisWeek: MovieList = emptyList()
            var bollywood: MovieList = emptyList()
            var netflix: MovieList = emptyList()
            var prime: MovieList = emptyList()
            var disney: MovieList = emptyList()
            var upcoming: MovieList = emptyList()
            var topRated: MovieList = emptyList()
            var bingeDramas: MovieList = emptyList()

            val essentialCompleted = AtomicInteger(0)
            val totalEssential = 4

            var debounceJob: Job? = null

            fun scheduleStateEmission() {
                debounceJob?.cancel()
                debounceJob = launch(Dispatchers.Default) {
                    delay(120) // Batch state updates to prevent rapid UI recomposition churn
                    val hasContent = featured.isNotEmpty() || trending.isNotEmpty() || top10.isNotEmpty() || nowPlaying.isNotEmpty()
                    if (hasContent) {
                        _uiState.value = HomeScreenUiState.Ready(
                            featuredMovieList = featured,
                            trendingMovieList = trending,
                            top10MovieList = top10,
                            nowPlayingMovieList = nowPlaying,
                            popularThisWeekList = popularThisWeek,
                            bollywoodMovieList = bollywood,
                            netflixMovieList = netflix,
                            primeMovieList = prime,
                            disneyMovieList = disney,
                            upcomingMovieList = upcoming,
                            topRatedMovieList = topRated,
                            bingeDramasMovieList = bingeDramas
                        )
                    } else if (essentialCompleted.get() >= totalEssential && _uiState.value is HomeScreenUiState.Loading) {
                        // All essential loads finished with 0 items -> show Error state with retry
                        _uiState.value = HomeScreenUiState.Error
                    }
                }
            }

            fun onEssentialFinished() {
                if (essentialCompleted.incrementAndGet() >= totalEssential) {
                    scheduleStateEmission()
                }
            }

            // Phase 1: High Priority (Above-the-fold essential rows)
            launch {
                movieRepository.getFeaturedMovies()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        featured = list
                        onEssentialFinished()
                        scheduleStateEmission()
                    }
            }

            launch {
                movieRepository.getTrendingMovies()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        trending = list
                        onEssentialFinished()
                        scheduleStateEmission()
                    }
            }

            launch {
                movieRepository.getTop10Movies()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        top10 = list
                        onEssentialFinished()
                        scheduleStateEmission()
                    }
            }

            launch {
                movieRepository.getNowPlayingMovies()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        nowPlaying = list
                        onEssentialFinished()
                        scheduleStateEmission()
                    }
            }

            // Phase 2: Staggered Secondary Rows (Below-the-fold)
            delay(200) // Allow top rows and posters to establish network priority

            launch {
                movieRepository.getPopularFilmsThisWeek()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        popularThisWeek = list
                        scheduleStateEmission()
                    }
            }

            launch {
                movieRepository.getBollywoodMovies()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        bollywood = list
                        scheduleStateEmission()
                    }
            }

            launch {
                movieRepository.getNetflixMovies()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        netflix = list
                        scheduleStateEmission()
                    }
            }

            launch {
                movieRepository.getPrimeMovies()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        prime = list
                        scheduleStateEmission()
                    }
            }

            launch {
                movieRepository.getDisneyMovies()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        disney = list
                        scheduleStateEmission()
                    }
            }

            launch {
                movieRepository.getUpcomingMovies()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        upcoming = list
                        scheduleStateEmission()
                    }
            }

            launch {
                movieRepository.getMostVotedMovies()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        topRated = list
                        scheduleStateEmission()
                    }
            }

            launch {
                movieRepository.getBingeWatchDramas()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        bingeDramas = list
                        scheduleStateEmission()
                    }
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
        val bollywoodMovieList: MovieList = emptyList(),
        val netflixMovieList: MovieList = emptyList(),
        val primeMovieList: MovieList = emptyList(),
        val disneyMovieList: MovieList = emptyList(),
        val upcomingMovieList: MovieList = emptyList(),
        val topRatedMovieList: MovieList = emptyList(),
        val bingeDramasMovieList: MovieList = emptyList()
    ) : HomeScreenUiState
}
