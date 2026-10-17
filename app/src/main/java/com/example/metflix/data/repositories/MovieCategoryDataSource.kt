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

package com.example.metflix.data.repositories

import com.example.metflix.data.entities.MovieCategory
import com.example.metflix.data.entities.toMovieCategory
import com.example.metflix.data.network.MetFlixApiClient
import com.example.metflix.data.network.NetworkConfig
import com.example.metflix.data.network.tmdb.TmdbApiClient
import com.example.metflix.data.network.tmdb.TmdbConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieCategoryDataSource @Inject constructor(
    private val apiClient: MetFlixApiClient,
    private val tmdbApiClient: TmdbApiClient
) {

    private val defaultCategories = listOf(
        MovieCategory("28", "Action"),
        MovieCategory("12", "Adventure"),
        MovieCategory("16", "Animation"),
        MovieCategory("35", "Comedy"),
        MovieCategory("80", "Crime"),
        MovieCategory("99", "Documentary"),
        MovieCategory("18", "Drama"),
        MovieCategory("10751", "Family"),
        MovieCategory("14", "Fantasy"),
        MovieCategory("36", "History"),
        MovieCategory("27", "Horror"),
        MovieCategory("10402", "Music"),
        MovieCategory("9648", "Mystery"),
        MovieCategory("10749", "Romance"),
        MovieCategory("878", "Sci-Fi"),
        MovieCategory("53", "Thriller"),
        MovieCategory("10752", "War"),
        MovieCategory("37", "Western")
    )

    private val movieCategoryDataReader = CachedDataReader {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getMovieCategories().getOrNull()
            if (!tmdb.isNullOrEmpty()) return@CachedDataReader tmdb
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getCategories(NetworkConfig.CATEGORIES_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) return@CachedDataReader remote.map { it.toMovieCategory() }
        }
        defaultCategories
    }

    suspend fun getMovieCategoryList(): List<MovieCategory> = movieCategoryDataReader.read()
}
