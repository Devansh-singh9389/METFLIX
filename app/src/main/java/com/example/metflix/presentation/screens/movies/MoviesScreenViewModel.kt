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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MoviesScreenViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter = _selectedFilter.asStateFlow()

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    private data class TMDbCoreLists(
        val nowPlaying: MovieList,
        val trendingDay: MovieList,
        val trendingWeek: MovieList,
        val topRated: MovieList,
        val popular: MovieList
    )

    private data class TMDbExtendedLists(
        val upcoming: MovieList,
        val boxOffice: MovieList,
        val mostVoted: MovieList,
        val netflix: MovieList,
        val prime: MovieList,
        val disney: MovieList,
        val filter: String
    )

    val uiState = combine(
        combine(
            movieRepository.getNowPlayingMovies(),
            movieRepository.getTrendingDayMovies(),
            movieRepository.getTrendingMovies(),
            movieRepository.getTop10Movies(),
            movieRepository.getPopularFilmsThisWeek()
        ) { nowPlaying, trendingDay, trendingWeek, topRated, popular ->
            TMDbCoreLists(nowPlaying, trendingDay, trendingWeek, topRated, popular)
        },
        combine(
            movieRepository.getUpcomingMovies(),
            movieRepository.getTopRevenueMovies(),
            movieRepository.getMostVotedMovies(),
            movieRepository.getNetflixMovies(),
            combine(
                movieRepository.getPrimeMovies(),
                movieRepository.getDisneyMovies(),
                _selectedFilter
            ) { prime, disney, filter -> Triple(prime, disney, filter) }
        ) { upcoming, boxOffice, mostVoted, netflix, (prime, disney, filter) ->
            TMDbExtendedLists(upcoming, boxOffice, mostVoted, netflix, prime, disney, filter)
        }
    ) { core, ext ->
        MoviesScreenUiState.Ready(
            heroMovieList = core.nowPlaying.ifEmpty { core.popular },
            trendingTodayList = core.trendingDay.ifEmpty { core.trendingWeek },
            trendingThisWeekList = core.trendingWeek,
            topRatedList = core.topRated,
            popularMoviesList = core.popular,
            upcomingList = ext.upcoming,
            boxOfficeHitsList = ext.boxOffice,
            mostVotedList = ext.mostVoted,
            netflixList = ext.netflix,
            primeList = ext.prime,
            disneyList = ext.disney,
            selectedFilter = ext.filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = MoviesScreenUiState.Loading
    )
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
