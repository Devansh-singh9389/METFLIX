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

import com.example.metflix.data.entities.Movie
import com.example.metflix.data.entities.toMovie
import com.example.metflix.data.network.MetFlixApiClient
import com.example.metflix.data.network.NetworkConfig
import com.example.metflix.data.network.tmdb.TmdbApiClient
import com.example.metflix.data.network.tmdb.TmdbConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvDataSource @Inject constructor(
    private val apiClient: MetFlixApiClient,
    private val tmdbApiClient: TmdbApiClient
) {
    private val mostPopularTvShowsReader = CachedDataReader {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getPopularTvShows().getOrNull()
            if (!tmdb.isNullOrEmpty()) return@CachedDataReader tmdb
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.TV_SHOWS_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) return@CachedDataReader remote.map { it.toMovie() }
        }
        emptyList()
    }

    suspend fun getTvShowList(): List<Movie> = mostPopularTvShowsReader.read().let { list ->
        val count = list.size.coerceAtMost(10)
        list.subList(0, count)
    }

    suspend fun getBingeWatchDramaList(): List<Movie> = mostPopularTvShowsReader.read().let { list ->
        if (list.size >= 15) {
            list.subList(5, 15)
        } else {
            list
        }
    }
}
