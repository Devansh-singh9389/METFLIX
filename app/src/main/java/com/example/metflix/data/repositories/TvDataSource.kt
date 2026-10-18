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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
class TvDataSource @Inject constructor(
    private val apiClient: MetFlixApiClient,
    private val tmdbApiClient: TmdbApiClient,
    private val diskCache: TmdbDiskCache
) {
    private val mostPopularTvShowsReader: CachedDataReader<Movie> = CachedDataReader {
        val cached = diskCache.loadMovies("tv_shows")
        if (cached.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                fetchTvShowsLive()?.let { mostPopularTvShowsReader.update(it) }
            }
            return@CachedDataReader cached
        }
        fetchTvShowsLive() ?: emptyList()
    }

    private val topRatedTvShowsReader: CachedDataReader<Movie> = CachedDataReader {
        val cached = diskCache.loadMovies("tv_shows_top_rated")
        if (cached.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                fetchTopRatedTvShowsLive()?.let { topRatedTvShowsReader.update(it) }
            }
            return@CachedDataReader cached
        }
        fetchTopRatedTvShowsLive() ?: emptyList()
    }

    private suspend fun fetchTvShowsLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val p1 = tmdbApiClient.getPopularTvShows(page = 1).getOrNull().orEmpty()
            val p2 = tmdbApiClient.getPopularTvShows(page = 2).getOrNull().orEmpty()
            val combined = (p1 + p2).distinctBy { it.id }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("tv_shows", combined)
                return combined
            }
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.TV_SHOWS_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) {
                val mapped = remote.map { it.toMovie() }
                diskCache.saveMovies("tv_shows", mapped)
                return mapped
            }
        }
        return null
    }

    private suspend fun fetchTopRatedTvShowsLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val p1 = tmdbApiClient.getTopRatedTvShows(page = 1).getOrNull().orEmpty()
            val p2 = tmdbApiClient.getTopRatedTvShows(page = 2).getOrNull().orEmpty()
            val combined = (p1 + p2).distinctBy { it.id }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("tv_shows_top_rated", combined)
                return combined
            }
        }
        return null
    }

    suspend fun getTvShowList(): List<Movie> = mostPopularTvShowsReader.read()

    suspend fun getBingeWatchDramaList(): List<Movie> {
        val topRated = topRatedTvShowsReader.read()
        return if (topRated.isNotEmpty()) topRated else mostPopularTvShowsReader.read()
    }
}
