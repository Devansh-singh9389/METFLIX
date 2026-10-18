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

package com.example.metflix.data.entities

import androidx.compose.runtime.Immutable

@Immutable
data class MovieDetails(
    val id: String,
    val videoUri: String,
    val subtitleUri: String?,
    val posterUri: String,
    val name: String,
    val description: String,
    val pgRating: String,
    val releaseDate: String,
    val categories: List<String>,
    val duration: String,
    val director: String,
    val screenplay: String,
    val music: String,
    val castAndCrew: List<MovieCast>,
    val status: String,
    val originalLanguage: String,
    val budget: String,
    val revenue: String,
    val similarMovies: MovieList,
    val reviewsAndRatings: List<MovieReviewsAndRatings>,
    val watchProviders: WatchProvidersInfo? = null,
    val isTvShow: Boolean = false,
    val seasons: List<TvSeason> = emptyList(),
    val totalSeasons: Int = 0,
    val totalEpisodes: Int = 0
)

@Immutable
data class TvSeason(
    val id: String,
    val name: String,
    val seasonNumber: Int,
    val episodeCount: Int,
    val posterUri: String? = null,
    val episodes: List<TvEpisode> = emptyList()
)

@Immutable
data class TvEpisode(
    val id: String,
    val name: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val overview: String = "",
    val stillUri: String? = null,
    val duration: String = "",
    val rating: String = ""
)
