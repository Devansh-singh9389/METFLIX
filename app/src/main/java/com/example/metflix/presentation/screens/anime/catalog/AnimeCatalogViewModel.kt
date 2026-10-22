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

package com.example.metflix.presentation.screens.anime.catalog

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.network.anime.models.AnimeEpisodeItem
import com.example.metflix.data.network.anime.models.AnimeMovieResponse
import com.example.metflix.data.network.anime.models.AnimeSeasonItem
import com.example.metflix.data.network.anime.models.AnimeSeriesResponse
import com.example.metflix.data.repositories.AnimeDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface AnimeCatalogUiState {
    data object Loading : AnimeCatalogUiState
    @Immutable
    data class Error(val message: String, val fallbackTitle: String = "", val fallbackPoster: String = "") : AnimeCatalogUiState

    @Immutable
    data class Series(
        val slug: String,
        val title: String,
        val poster: String,
        val synopsis: String,
        val genres: List<String>,
        val languages: List<String>,
        val totalSeasons: Int,
        val seasons: List<AnimeSeasonItem>,
        val selectedSeasonNumber: Int,
        val currentEpisodes: List<AnimeEpisodeItem>
    ) : AnimeCatalogUiState

    @Immutable
    data class MovieCatalog(
        val slug: String,
        val title: String,
        val poster: String,
        val synopsis: String,
        val duration: String,
        val releaseYear: String,
        val genres: List<String>,
        val languages: List<String>,
        val recommendations: List<Movie>
    ) : AnimeCatalogUiState
}

@HiltViewModel
class AnimeCatalogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val animeDataSource: AnimeDataSource
) : ViewModel() {

    private val rawMediaType: String = savedStateHandle.get<String>("mediaType").orEmpty()
    private val slug: String = savedStateHandle.get<String>("slug").orEmpty()

    private val isMovie: Boolean = rawMediaType.equals("movie", ignoreCase = true) || slug.startsWith("movie-")

    private val _uiState = MutableStateFlow<AnimeCatalogUiState>(AnimeCatalogUiState.Loading)
    val uiState: StateFlow<AnimeCatalogUiState> = _uiState.asStateFlow()

    init {
        loadCatalog()
    }

    fun retry() {
        _uiState.value = AnimeCatalogUiState.Loading
        loadCatalog(forceRefresh = true)
    }

    fun loadCatalog(forceRefresh: Boolean = false) {
        if (slug.isBlank()) {
            _uiState.value = AnimeCatalogUiState.Error("Invalid content identifier")
            return
        }

        val knownMovie = animeDataSource.findMovieById(slug)

        viewModelScope.launch {
            if (isMovie) {
                val result = animeDataSource.getMovieData(slug, forceRefresh = forceRefresh)
                result.onSuccess { movieRes ->
                    val recs = movieRes.recommendedMovies.map { it.toMovie() }
                    _uiState.value = AnimeCatalogUiState.MovieCatalog(
                        slug = slug,
                        title = movieRes.title ?: knownMovie?.name ?: slug.replace("-", " "),
                        poster = movieRes.poster ?: knownMovie?.posterUri.orEmpty(),
                        synopsis = movieRes.synopsis ?: knownMovie?.description.orEmpty(),
                        duration = movieRes.duration.orEmpty(),
                        releaseYear = movieRes.releaseYear.orEmpty(),
                        genres = movieRes.genres,
                        languages = movieRes.languages,
                        recommendations = recs
                    )
                }.onFailure { err ->
                    _uiState.value = AnimeCatalogUiState.Error(
                        message = err.message ?: "Failed to load movie details",
                        fallbackTitle = knownMovie?.name.orEmpty(),
                        fallbackPoster = knownMovie?.posterUri.orEmpty()
                    )
                }
            } else {
                val result = animeDataSource.getSeriesData(slug, forceRefresh = forceRefresh)
                result.onSuccess { seriesRes ->
                    val initialSeason = seriesRes.seasons.firstOrNull()?.seasonNumber ?: 1
                    val initialEps = seriesRes.seasons.firstOrNull { it.seasonNumber == initialSeason }?.episodes.orEmpty()

                    _uiState.value = AnimeCatalogUiState.Series(
                        slug = slug,
                        title = seriesRes.title ?: knownMovie?.name ?: slug.replace("-", " "),
                        poster = seriesRes.poster ?: knownMovie?.posterUri.orEmpty(),
                        synopsis = seriesRes.synopsis ?: knownMovie?.description.orEmpty(),
                        genres = seriesRes.genres,
                        languages = seriesRes.languages,
                        totalSeasons = seriesRes.totalSeasons.coerceAtLeast(seriesRes.seasons.size),
                        seasons = seriesRes.seasons,
                        selectedSeasonNumber = initialSeason,
                        currentEpisodes = initialEps
                    )
                }.onFailure { err ->
                    _uiState.value = AnimeCatalogUiState.Error(
                        message = err.message ?: "Failed to load series details",
                        fallbackTitle = knownMovie?.name.orEmpty(),
                        fallbackPoster = knownMovie?.posterUri.orEmpty()
                    )
                }
            }
        }
    }

    /**
     * CRITICAL USER REQUIREMENT:
     * Only update season and episodes when the user explicitly clicks a season,
     * NEVER when simply moving focus / hovering over the season tabs.
     */
    fun onSeasonClicked(seasonNumber: Int) {
        val current = _uiState.value
        if (current !is AnimeCatalogUiState.Series) return
        if (current.selectedSeasonNumber == seasonNumber) return

        val targetSeason = current.seasons.firstOrNull { it.seasonNumber == seasonNumber }
        val episodesForSeason = targetSeason?.episodes.orEmpty()

        _uiState.value = current.copy(
            selectedSeasonNumber = seasonNumber,
            currentEpisodes = episodesForSeason
        )
    }
}
